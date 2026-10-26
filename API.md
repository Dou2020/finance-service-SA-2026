# finance-service — API

Referencia de cada endpoint: qué entra, qué sale, qué errores da (con el `detail` exacto que muestra el
frontend) y qué efectos tiene. El resumen de una línea por endpoint está en el [README](README.md#endpoints);
las reglas de negocio, en el [README del proyecto](../README.md). Las secciones [Llama a](#llama-a) y
[Eventos que consume](#eventos-que-consume) son el contrato que tienen que cumplir fleet, trip y sales: llegaron
importados (2026-10-10) y todavía no lo cumplen. Sus ids son texto en finance (V3, 2026-10-25).

| | URL base |
|---|---|
| Por el gateway (frontend) | `http://localhost:8080` — valida el JWT y aplica los límites |
| Directo al servicio (solo desarrollo) | `http://localhost:8086` — los `X-User-*` se ponen a mano |
| Entre servicios (red `transporte`) | `http://finance-service:8086` — solo `/internal/**` |
| Swagger | `http://localhost:8080/swagger-ui.html` (agregado) · `http://localhost:8086/swagger-ui.html` |

## Contenido

- [Convenciones](#convenciones)
  - [Autenticación](#autenticación) · [Formatos](#formatos) · [Paginación](#paginación) ·
    [Errores](#errores) · [Respuestas del gateway y límites](#respuestas-del-gateway-y-límites)
- [Categorías de gasto — `/api/categorias-gasto`](#categorías-de-gasto--apicategorias-gasto):
  [lista](#get-apicategorias-gasto) · [alta](#post-apicategorias-gasto) · [renombrar](#put-apicategorias-gastoid) ·
  [estado](#patch-apicategorias-gastoidestado) · [eliminar](#delete-apicategorias-gastoid)
- [Gastos de taller — `/api/gastos-taller`](#gastos-de-taller--apigastos-taller):
  [registro](#post-apigastos-taller) · [lista](#get-apigastos-taller) · [detalle](#get-apigastos-tallerid)
- [Reportes — `/api/finanzas/reportes`](#reportes--apifinanzasreportes): [ingresos](#get-apifinanzasreportesingresos) ·
  [gastos](#get-apifinanzasreportesgastos) · [ganancias](#get-apifinanzasreportesganancias) ·
  [taller por categoría](#get-apifinanzasreportestaller-por-categoria) ·
  [taller por bus](#get-apifinanzasreportestaller-por-bus) ·
  [depreciación por bus](#get-apifinanzasreportesdepreciacion-por-bus)
- [Internas — `/internal`](#internas--internal): [reprocesar DLQ](#post-internaldlqreprocesar)
- [Llama a](#llama-a): [S6, el bus en fleet](#s6-get-internalbusesid-en-fleet)
- [Eventos que consume](#eventos-que-consume)
- [Técnicas](#técnicas)

---

## Convenciones

Las mismas que identity-service; aquí va lo propio de finance y un enlace para lo común.

### Autenticación

Igual que en identity ([Autenticación](../identity-service/API.md#autenticación)): el gateway valida el
JWT y reenvía `X-User-Id`, `X-User-Roles`, `X-Sucursal-Id` y `X-Chofer-Id`. finance no tiene rutas
públicas: todo `/api/**` exige token. Llamando directo al puerto 8086 en desarrollo, se ponen a mano:

```bash
curl -H 'X-User-Id: 0d3f5a1e-6c2b-4f7e-9a8d-1b2c3d4e5f60' -H 'X-User-Roles: BRANCH_ADMIN' \
     -H 'X-Sucursal-Id: 6a1f2b3c-4d5e-4f60-8a9b-0c1d2e3f4a5b' \
     http://localhost:8086/api/gastos-taller
```

**Alcance.** Un `BRANCH_ADMIN` solo ve gastos y reportes de su sucursal (la del token): en las listas y
los reportes el filtro va en la consulta y un `sucursalId` que pida se ignora; un gasto de otra sucursal
por id → `403`. El `SYS_ADMIN` ve todas, o una con `sucursalId`. `CASHIER`, `DRIVER` y `CUSTOMER` no usan
finance (`403`).

### Formatos

Como en identity ([Formatos](../identity-service/API.md#formatos)): JSON en UTF-8, ids UUID, instantes
ISO-8601 en UTC, fechas de negocio `AAAA-MM-DD` ("hoy" en `America/Guatemala`) y montos como número JSON.

**Ids de fleet, trip y sales: texto.** Esos servicios numeran con bigserial, así que el bus, el viaje, el
chofer y la venta van como su id en texto (`"7"`, `"101"`), de hasta 64 caracteres; un número JSON también se
acepta y se lee como su texto. Se guardan y se devuelven tal como llegaron (V3). Los ids de finance (gastos,
categorías), de identity (sucursales, usuarios) y de customer (alquileres) siguen siendo UUID o enteros.
La fecha del servicio de taller la elige el usuario, con hoy por defecto, y los reportes filtran por las
fechas de negocio (pago, llegada, servicio), no por la del sistema (README del proyecto, "Fechas").

Enumerados:

| Tipo | Valores |
|---|---|
| `TipoLinea` | `MANO_DE_OBRA`, `REPUESTO` |
| `TipoIngreso` | `BOLETO`, `ALQUILER` |

### Paginación

Pagina `GET /api/gastos-taller`, con `page` (desde 0), `size` (20 por defecto) y `sort` (`campo,asc|desc`),
y la respuesta `{content, page}` de identity ([Paginación](../identity-service/API.md#paginación)). Un campo
de orden que no existe → `400 No se puede ordenar por '<campo>'`.

### Errores

Todo error sale como `ProblemDetail` (RFC 9457); los comunes (`400`, `401`, `403`, `404`, `405`, `500`)
son los de identity ([Errores](../identity-service/API.md#errores)). Lo propio de finance:

| Estado | `detail` | Cuándo |
|---|---|---|
| `400` | `La fecha inicial no puede ser posterior a la final` | `desde` > `hasta` en la lista de gastos o en un reporte |
| `409` | `Tu sucursal todavía no está disponible en finanzas. Intenta de nuevo en unos minutos` | finance no recibió todavía el `SucursalGuardada` (E1) de la sucursal del bus |
| `503` | `El servicio no está disponible en este momento. Intenta de nuevo en unos minutos` | fleet no respondió al validar el bus de un gasto (S6), o su circuito está abierto |

Los `409` por datos repetidos o de concurrencia. Si dos requests simultáneas pasan la revisión del caso de
uso, la restricción de PostgreSQL decide y la que pierde recibe el mismo mensaje:

| `detail` | Dato |
|---|---|
| `Ya existe una categoría con ese nombre` | nombre de una categoría, sin distinguir mayúsculas |
| `La categoría tiene gastos registrados: solo se puede desactivar` | borrar una categoría que un gasto acaba de usar |
| `Otra operación está modificando estos datos. Intenta de nuevo` | no se obtuvo un bloqueo de PostgreSQL |
| `El cambio choca con datos que ya existen` | otra restricción sin mensaje propio |

### Respuestas del gateway y límites

Las mismas respuestas del gateway que en identity
([Respuestas del gateway](../identity-service/API.md#respuestas-del-gateway-y-límites)), con finance en
lugar de identity en los `503` y `504`.

| Rutas | Clave | Límite |
|---|---|---|
| `/api/categorias-gasto/**`, `/api/gastos-taller/**`, `/api/finanzas/**` | usuario del token | 20 por segundo, ráfagas de 40 |

`/internal/**` no tiene ruta en el gateway.

---

## Categorías de gasto — `/api/categorias-gasto`

El catálogo que clasifica cada línea de un gasto de taller. Se agregan categorías sin tocar código. Una con
gastos no se elimina, solo se desactiva: deja de ofrecerse para gastos nuevos y los ya registrados la
conservan. La migración `V2` trae las del enunciado: Llantas, Motor, Frenos, Suspensión, Cambio de aceite,
Eléctrico, Carrocería y Otros.

`CategoriaResponse`:

```json
{ "id": 3, "nombre": "Frenos", "activa": true }
```

### `GET /api/categorias-gasto`

**Acceso:** `SYS_ADMIN` y `BRANCH_ADMIN` (este la usa al registrar gastos).

| Query | Tipo | Descripción |
|---|---|---|
| `activa` | boolean | opcional; sin él, todas |

**`200`** — `CategoriaResponse[]` ordenadas por nombre. Sin paginar: el catálogo es corto.

### `POST /api/categorias-gasto`

**Acceso:** `SYS_ADMIN`.

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `nombre` | string | sí | hasta 60; se guarda sin espacios de más (`"  Cambio   de aceite "` → `"Cambio de aceite"`) |

**`201`** — `CategoriaResponse` activa, con `Location: /api/categorias-gasto/{id}`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `409` | `Ya existe una categoría con ese nombre` | Otra con el mismo nombre, sin distinguir mayúsculas |

### `PUT /api/categorias-gasto/{id}`

Renombra. El nombre nuevo aparece también en los reportes de gastos ya registrados: es el mismo catálogo.

**Acceso:** `SYS_ADMIN`. Body: `{nombre}`, con las reglas del alta.

**`200`** — `CategoriaResponse`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `404` | `No existe esa categoría` | |
| `409` | `Ya existe una categoría con ese nombre` | Otra categoría ya lo usa |

### `PATCH /api/categorias-gasto/{id}/estado`

**Acceso:** `SYS_ADMIN`. Body: `{"activa": false}` (obligatorio).

**`200`** — `CategoriaResponse`. `404 No existe esa categoría`. Un gasto que se registra al mismo tiempo
bloquea sus categorías: la desactivación espera a que termine.

### `DELETE /api/categorias-gasto/{id}`

**Acceso:** `SYS_ADMIN`.

**`204`** sin cuerpo.

| Estado | `detail` | Cuándo |
|---|---|---|
| `404` | `No existe esa categoría` | |
| `409` | `La categoría tiene gastos registrados: solo se puede desactivar` | Alguna línea de gasto la usa. El árbitro es el `ON DELETE RESTRICT` de `linea_gasto` |

---

## Gastos de taller — `/api/gastos-taller`

Un gasto es un encabezado (bus, fecha del servicio, totales) y sus líneas, cada una con categoría
obligatoria. Los totales los calcula el sistema. No hay edición ni borrado: es parte del libro de costos.

`GastoTallerResponse`:

| Campo | Notas |
|---|---|
| `placa`, `sucursalId` | los que respondió fleet al registrarlo |
| `totalManoObra`, `totalRepuestos`, `total` | suma de las líneas de cada tipo, y de todas |
| `registradoPor` | el `X-User-Id` de quien lo registró |
| `lineas[].categoria` | el nombre actual de la categoría |

```json
{
  "id": "a3f9…", "busId": "7", "placa": "P123BCD", "sucursalId": "1b4e…", "fechaServicio": "2026-09-30",
  "totalManoObra": 350.00, "totalRepuestos": 1300.49, "total": 1650.49,
  "registradoPor": "0d3f…", "creadoEn": "2026-10-07T23:41:02.113Z",
  "lineas": [
    { "id": 17, "categoriaId": 1, "categoria": "Llantas", "descripcion": "Cambio de 2 llantas",
      "tipo": "REPUESTO", "monto": 1200.50 },
    { "id": 18, "categoriaId": 1, "categoria": "Llantas", "descripcion": "Montaje",
      "tipo": "MANO_DE_OBRA", "monto": 150.00 }
  ]
}
```

### `POST /api/gastos-taller`

Registra el gasto de un bus de su sucursal. Primero consulta el bus en fleet (S6): que exista y que sea de
la sucursal del admin.

**Acceso:** `BRANCH_ADMIN` (ni el `SYS_ADMIN` ni el cajero: `403`).

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `busId` | string | sí | el id del bus en fleet como texto (`"7"`; un número JSON también entra); no vacío, hasta 64. Un bus de su sucursal |
| `fechaServicio` | `AAAA-MM-DD` | no | hoy por defecto; no puede ser futura |
| `lineas` | arreglo | sí | de 1 a 50 |
| `lineas[].categoriaId` | entero | sí | una categoría activa |
| `lineas[].descripcion` | string | sí | hasta 200 |
| `lineas[].tipo` | `TipoLinea` | sí | |
| `lineas[].monto` | número | sí | mayor que cero, hasta 10 enteros y 2 decimales |

```json
{
  "busId": "7", "fechaServicio": "2026-09-30",
  "lineas": [
    { "categoriaId": 1, "descripcion": "Cambio de 2 llantas", "tipo": "REPUESTO", "monto": 1200.50 },
    { "categoriaId": 1, "descripcion": "Montaje", "tipo": "MANO_DE_OBRA", "monto": 150 }
  ]
}
```

**`201`** — `GastoTallerResponse`, con `Location: /api/gastos-taller/{id}`.

| Estado | `detail` | Cuándo |
|---|---|---|
| `400` | `Hay datos inválidos en la solicitud`, con `errores.busId` = `no debe estar vacío` · `el tamaño debe estar entre 0 y 64`, `errores.lineas` = `debe tener al menos una línea` o `errores['lineas[0].monto']` = `debe ser mayor que cero` · `debe tener a lo sumo 2 decimales` | Validación del body; no llama a fleet |
| `403` | `No tienes permiso para esta acción` | El bus es de otra sucursal |
| `404` (o el 4xx de fleet) | el `detail` que respondió fleet, por ejemplo `No existe ese bus`; sin `detail`, `fleet respondió <estado>` | fleet no conoce el bus |
| `409` | `Tu sucursal todavía no está disponible en finanzas. Intenta de nuevo en unos minutos` | Sin su E1 todavía |
| `422` | `La fecha del servicio no puede ser posterior a hoy` | |
| `422` | `No existe la categoría <id>` · `La categoría <nombre> está desactivada` | |
| `503` | `El servicio no está disponible en este momento. Intenta de nuevo en unos minutos` | fleet caído, lento (más de 2 s, con un reintento) o con el circuito abierto |

**Efectos:** ninguno fuera de finance. Con un `4xx` o un `503` no se guarda nada. El `busId` se le
consulta a fleet sin los espacios de los extremos, y se guarda el `id` que fleet respondió. Las categorías
del gasto quedan bloqueadas (`FOR SHARE`) hasta que se confirma: un borrado o una desactivación simultánea
espera.

### `GET /api/gastos-taller`

**Acceso:** `SYS_ADMIN` (todas, o una con `sucursalId`) y `BRANCH_ADMIN` (siempre la suya).

| Query | Tipo | Descripción |
|---|---|---|
| `sucursalId` | UUID | opcional, solo `SYS_ADMIN` |
| `busId` | string | opcional: el id de fleet como texto (`7`); vacío es sin filtro |
| `desde`, `hasta` | `AAAA-MM-DD` | opcionales, sobre `fechaServicio` |
| `page`, `size`, `sort` | | por defecto `fechaServicio,desc` y `creadoEn,desc` |

**`200`** — página de encabezados, sin las líneas (están en el detalle):

```json
{
  "content": [
    { "id": "a3f9…", "busId": "7", "placa": "P123BCD", "sucursalId": "1b4e…",
      "fechaServicio": "2026-09-30", "totalManoObra": 350.00, "totalRepuestos": 1300.49, "total": 1650.49,
      "creadoEn": "2026-10-07T23:41:02.113Z" }
  ],
  "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 }
}
```

### `GET /api/gastos-taller/{id}`

**Acceso:** `SYS_ADMIN` y el `BRANCH_ADMIN` de su sucursal; otro → `403`. **`200`** — `GastoTallerResponse`.
`404 No existe ese gasto de taller`.

---

## Reportes — `/api/finanzas/reportes`

Los 6 reportes que son de finance (README del proyecto, "Reportes"). El frontend los muestra en
`ReportTable` y los exporta a PDF.

**Acceso:** `SYS_ADMIN` (todas las sucursales, o una con `sucursalId`) y `BRANCH_ADMIN` (siempre la suya,
pida la que pida; el campo `sucursalId` de la respuesta dice cuál se usó). `CASHIER` y los demás → `403`.

| Query | Tipo | Descripción |
|---|---|---|
| `desde`, `hasta` | `AAAA-MM-DD` | opcionales e inclusivos, sobre la fecha de negocio; sin ellos, todos los registros. `400` si `desde` > `hasta` |
| `sucursalId` | UUID | opcional, solo `SYS_ADMIN` |
| `busId` | string | opcional, solo en los dos de taller: el id de fleet como texto (`7`); vacío es sin filtro |

Las sucursales van en orden alfabético en español ("Ávila" antes que "Barberena"), con el nombre de la
réplica que finance arma con E1. Los montos agrupados usan dos objetos:

| Objeto | Campos |
|---|---|
| `Ingresos` | `boletos`, `alquileres`, `total` |
| `Costos` | `combustible`, `depreciacion`, `salarios` (choferes), `manoDeObra`, `repuestos`, `total` |

### `GET /api/finanzas/reportes/ingresos`

Ingresos por sucursal: cada uno, el subtotal por sucursal y el total global. Los boletos cuentan en la
sucursal de origen de la ruta y los alquileres en la que los atiende, por **fecha de pago** (supuestos 6 y
7). Los reversados (venta anulada, alquiler cancelado después de pagar) no cuentan. Solo aparecen
sucursales con ingresos en el período.

```json
{
  "desde": "2026-09-01", "hasta": "2026-09-30", "sucursalId": null,
  "sucursales": [
    {
      "sucursalId": "1b4e…", "nombre": "Quetzaltenango",
      "filas": [
        { "fecha": "2026-09-05", "tipo": "BOLETO", "referenciaId": "c0a8…",
          "detalle": "Quetzaltenango → Ciudad de Guatemala · 20/10/2026 08:00 · 3 asientos · en línea",
          "monto": 225.00 },
        { "fecha": "2026-09-07", "tipo": "ALQUILER", "referenciaId": "8f0c…",
          "detalle": "Alquiler · Ana López · Quetzaltenango → Antigua Guatemala · sale 20/10/2026",
          "monto": 1800.00 }
      ],
      "subtotal": { "boletos": 225.00, "alquileres": 1800.00, "total": 2025.00 }
    }
  ],
  "total": { "boletos": 225.00, "alquileres": 1800.00, "total": 2025.00 }
}
```

`referenciaId` es la venta (sales) o el alquiler (customer) de origen, como texto: el `ventaId` tal como
llegó en E11 (su número o un UUID) o el UUID del alquiler.

### `GET /api/finanzas/reportes/gastos`

Gastos por sucursal, con la misma estructura: una fila por viaje finalizado (combustible, depreciación y
salario del chofer, por fecha de llegada) y una por gasto de taller (mano de obra y repuestos, por fecha del
servicio), en orden de fecha, con el subtotal por concepto de cada sucursal y los totales.

```json
{
  "desde": "2026-09-01", "hasta": "2026-09-30", "sucursalId": "1b4e…",
  "sucursales": [
    {
      "sucursalId": "1b4e…", "nombre": "Quetzaltenango",
      "filas": [
        { "fecha": "2026-09-10", "origen": "VIAJE", "referenciaId": "101", "placa": "P777AAA",
          "detalle": "Viaje regular · 250 km",
          "costos": { "combustible": 850.75, "depreciacion": 375.00, "salarios": 300.00, "manoDeObra": 0,
                      "repuestos": 0, "total": 1525.75 } },
        { "fecha": "2026-09-12", "origen": "TALLER", "referenciaId": "a3f9…", "placa": "P123BCD",
          "detalle": "Gasto de taller",
          "costos": { "combustible": 0, "depreciacion": 0, "salarios": 0, "manoDeObra": 350.00,
                      "repuestos": 1200.00, "total": 1550.00 } }
      ],
      "subtotal": { "combustible": 850.75, "depreciacion": 375.00, "salarios": 300.00, "manoDeObra": 350.00,
                    "repuestos": 1200.00, "total": 3075.75 }
    }
  ],
  "total": { "…": "Costos" }
}
```

`origen` es `VIAJE` (`referenciaId` = el id del viaje en trip, como texto) o `TALLER` (el UUID del gasto de
taller).

### `GET /api/finanzas/reportes/ganancias`

Por sucursal, en orden alfabético: ingresos, costos y ganancia neta (`ingresos.total − costos.total`, puede
ser negativa), con los totales. Aparecen todas las sucursales activas aunque no tengan movimiento, y las
inactivas que sí lo tuvieron; con `sucursalId`, solo esa.

```json
{
  "desde": "2026-09-01", "hasta": "2026-09-30", "sucursalId": null,
  "sucursales": [
    { "sucursalId": "1b4e…", "nombre": "Quetzaltenango",
      "ingresos": { "boletos": 225.00, "alquileres": 1800.00, "total": 2025.00 },
      "costos": { "combustible": 850.75, "depreciacion": 375.00, "salarios": 300.00, "manoDeObra": 450.00,
                  "repuestos": 1200.00, "total": 3175.75 },
      "ganancia": -1150.75 }
  ],
  "ingresos": { "…": "Ingresos" }, "costos": { "…": "Costos" }, "ganancia": -1150.75
}
```

### `GET /api/finanzas/reportes/taller-por-categoria`

Por categoría (en orden alfabético): cada línea de gasto (bus, sucursal, fecha, descripción, tipo, monto),
el total de la categoría y el gran total. Filtros opcionales `sucursalId` y `busId`.

```json
{
  "desde": "2026-09-01", "hasta": "2026-09-30", "sucursalId": null, "busId": null,
  "categorias": [
    { "categoriaId": 3, "nombre": "Frenos",
      "filas": [
        { "gastoId": "a3f9…", "placa": "P123BCD", "sucursal": "Quetzaltenango", "fecha": "2026-09-12",
          "descripcion": "Cambio de zapatas", "tipo": "MANO_DE_OBRA", "monto": 350.00 }
      ],
      "total": 350.00 }
  ],
  "granTotal": 350.00
}
```

### `GET /api/finanzas/reportes/taller-por-bus`

Por bus (por placa): cuántos gastos tuvo, el subtotal de cada categoría separado en mano de obra y
repuestos, y el total del bus; con el gran total. Un bus que se repite en una categoría (motor, por
ejemplo) es candidato a desactivarse. Filtros opcionales `sucursalId` y `busId`.

```json
{
  "desde": "2026-09-01", "hasta": "2026-09-30", "sucursalId": "1b4e…", "busId": null,
  "buses": [
    { "busId": "7", "placa": "P123BCD", "sucursal": "Quetzaltenango", "gastos": 2,
      "categorias": [
        { "nombre": "Frenos", "manoDeObra": 450.00, "repuestos": 0, "subtotal": 450.00 },
        { "nombre": "Llantas", "manoDeObra": 0, "repuestos": 1200.00, "subtotal": 1200.00 }
      ],
      "manoDeObra": 450.00, "repuestos": 1200.00, "total": 1650.00 }
  ],
  "granTotal": 1650.00
}
```

### `GET /api/finanzas/reportes/depreciacion-por-bus`

Por bus (por placa): viajes finalizados, km recorridos y depreciación acumulada, con la tarifa por km
configurada hoy. Cada viaje usó la tarifa vigente a su fecha de llegada (supuesto 10): si la tarifa cambió,
la acumulada no es `kmRecorridos × tarifaVigente`. Solo aparecen buses con viajes finalizados en el período
(finance conoce los buses por sus viajes, no por fleet).

```json
{
  "desde": null, "hasta": null, "sucursalId": "1b4e…",
  "tarifaVigente": 1.5000, "tarifaVigenteDesde": "2026-01-01",
  "buses": [
    { "busId": "12", "placa": "P777AAA", "sucursal": "Quetzaltenango", "viajes": 1, "kmRecorridos": 250,
      "depreciacion": 375.00 }
  ],
  "viajes": 1, "kmRecorridos": 250, "depreciacion": 375.00
}
```

`tarifaVigente` y `tarifaVigenteDesde` son `null` si todavía no se configuró ninguna en identity.

---

## Internas — `/internal`

Para el operador. El gateway **no** las expone y no piden autenticación: en producción solo se llega a
ellas desde la red interna de Docker. Ningún servicio llama a finance: lo que necesita le llega por eventos,
y la única llamada que hace (S6) es hacia fleet.

### `POST /internal/dlq/reprocesar`

Devuelve a la cola `finance.events` los mensajes de `finance.events.dlq`, una vez corregida la causa por
la que fallaron. Los casos típicos en finance: un ingreso o un costo de una sucursal que todavía no estaba
replicada, o un viaje sin tarifa de depreciación vigente a su fecha de llegada ([Eventos que
consume](#eventos-que-consume)).

**Llama:** el operador, desde el servidor (en `finance-service/`). Sin body.

```bash
docker compose exec finance-service curl -s -X POST localhost:8086/internal/dlq/reprocesar
```

**`200`**:

```json
{ "reprocesados": 3 }
```

**Efectos:**

- Mueve solo los mensajes que había al empezar. Uno que vuelve a fallar regresa a la DLQ después de sus
  reintentos y no se reprocesa en un ciclo sin fin.
- Cada mensaje sale de la DLQ solo después de que RabbitMQ confirma el reenvío, así que un fallo a la
  mitad no pierde ninguno.
- El inbox no registró el intento fallido: el evento reprocesado se aplica completo.
- Si RabbitMQ no responde, da `500`.

---

## Llama a

### S6 `GET /internal/buses/{id}` en fleet

Al registrar un gasto de taller. Es el contrato mínimo que fleet tiene que cumplir: el fleet importado
todavía no expone ninguna ruta `/internal/**` (llega en la ola 2 del plan de eventos).

`{id}` es el id del bus en fleet como texto (`GET /internal/buses/7`), el que mandó el frontend en `busId`.
Va como variable de la plantilla, así que se codifica entero: una `/` sale como `%2F` y no cambia la ruta.
finance lo busca en `FLEET_URL`: `http://fleet-service:8084` en su compose y `http://localhost:8084` fuera de
Docker (8084 es el puerto del fleet importado; el 8082 del diseño lo usa trip).

| Respuesta | finance hace |
|---|---|
| `200 {id, placa, sucursalId, estado}` | Compara `sucursalId` con la del admin (`403` si es otra) y guarda `id`, `placa` y `sucursalId` en el gasto. `id` puede venir como número JSON (el `Long` de fleet): se guarda como su texto. `sucursalId` es el UUID de identity y `placa` tiene hasta 20 caracteres. `estado` no se valida: un gasto se registra en cualquier estado del bus |
| `4xx` (`ProblemDetail`) | Responde el mismo estado con el `detail` de fleet; sin `detail` (como el `{timestamp, status, message}` de los errores de fleet hoy), con `fleet respondió <estado>`. No se reintenta ni abre el circuito: es la respuesta de un servicio sano |
| `5xx`, sin conexión, más de 2 s, o un `200` sin `id`, `placa` o `sucursalId` (o con un `sucursalId` que no es UUID) | Un reintento a los 300 ms y luego `503` |

```json
{ "id": 7, "placa": "P123BCD", "sucursalId": "1b4e…", "estado": "DISPONIBLE" }
```

Resilience4j, instancia `fleet` (`application.yml`): conexión 1 s, respuesta 2 s, 2 intentos; el circuito
(por fuera del reintento) abre con 50 % de fallos sobre las últimas 10 llamadas (mínimo 5), queda abierto
10 s y prueba con 3. Reenvía el `X-Correlation-Id` del request. fleet caído no pone en rojo el health de
finance.

---

## Eventos que consume

finance no publica eventos (no tiene outbox). Cada evento se aplica en una transacción con su inbox
(`processed_event`): uno repetido no tiene efecto. Los errores transitorios (la base) se reintentan a 1, 2,
4 y 8 s y después van a la DLQ. Lo que nunca va a funcionar va directo a la DLQ, sin reintentos: payload
ilegible, dato obligatorio que falta, un id vacío o de más de 64 caracteres, o una réplica que finance no
tiene (la sucursal del ingreso o del costo, o la tarifa de depreciación vigente a la fecha de llegada). Lo
último no se reintenta porque el E1 o el E2 que faltan, si vienen detrás en la misma cola, no se procesan
mientras tanto; se arregla la causa y se [reprocesa la DLQ](#post-internaldlqreprocesar).

**Ids de fleet, trip y sales: texto.** `viajeId`, `busId` y `choferId` (E9) y `ventaId` y `viajeId` (E11 y
E12) son el id del productor tal cual, como texto (`"101"`): trip, fleet y sales numeran con bigserial. Un
número JSON (`101`) también se acepta y se guarda como su texto; un UUID en texto, también. Tienen que ser
no vacíos y de hasta 64 caracteres; si no, el evento va directo a la DLQ. Se guardan sin normalizar: el
productor tiene que mandar siempre el mismo texto para el mismo agregado (el E12 encuentra su E11 por
`ventaId`). `sucursalId` (identity) y `alquilerId` (customer) siguen siendo UUID: uno que no lo es deja el
payload ilegible y el evento va a la DLQ.

trip y sales (importados) todavía no publican en `transporte.events`; tienen que publicar **al menos** estos
campos:

| Evento | Payload que finance lee | Qué hace |
|---|---|---|
| E1 `identity.sucursal.guardada` | `{sucursalId, nombre, activa}`; `aggregateVersion` = versión de la sucursal | Inserta o actualiza la réplica. Una versión igual o más vieja que la guardada se descarta |
| E2 `identity.configuracion.actualizada` | `{tarifaId, tipo, valor, vigenteDesde}` | Guarda la versión de `DEPRECIACION_KM` con el id de identity; descarta las de alquiler |
| E9 `trip.viaje.finalizado` | `{viajeId, tipo, sucursalId, busId, placa, choferId, salarioBase, kmInicial, kmFinal, combustible, llegada}`. `viajeId`, `busId` y `choferId`, texto; `tipo` es `REGULAR` o `ALQUILER`; `sucursalId`, la de origen de la ruta o la que atiende el alquiler; `placa`, hasta 20; `salarioBase`, el snapshot de la asignación; `kmInicial` y `kmFinal`, enteros; `llegada`, el instante real que eligió el usuario. `alquilerId`, si viene, es el UUID de customer o `null`: finance no lo usa | Costo del viaje con fecha = la de `llegada` en Guatemala: km = final − inicial, depreciación = km × tarifa vigente a esa fecha, salario = base (× 1.15 en `ALQUILER`), a 2 decimales. Guarda la tarifa aplicada. Un `viajeId` ya registrado se descarta. `kmFinal` < `kmInicial` o montos negativos → DLQ |
| E11 `sales.boletos.vendidos` | Lo que lee customer **más `sucursalId`**: `{ventaId, sucursalId, canal, viajeId, origen, destino, salida, asientos, total, fechaPago}`; `ventaId` y `viajeId`, texto; `sucursalId` es la de origen de la ruta | Ingreso `BOLETO` por `total` en `fechaPago`, con `ventaId` de referencia. Si ya existe (o su lápida), no cambia |
| E12 `sales.boletos.anulados` | Los mismos de E11 y `motivo` | Marca el ingreso reversado (el monto no cambia). Si no existe, deja la **lápida**: el ingreso ya reversado con estos datos, así un E11 tardío no cuenta. Pasa cuando sales anula una venta antes de confirmarla, o cuando un E11 se reprocesa desde la DLQ después de su E12 |
| E14 `customer.alquiler.pagado` | `{alquilerId, sucursalId, nombre, origen, destino, fechaSalida, monto, fechaPago}` | Ingreso `ALQUILER` por `monto` en `fechaPago` en la sucursal que lo atiende |
| E16 `customer.alquiler.cancelado` | `{alquilerId, sucursalId, nombre, estabaPagado, monto, fechaPago}` | Con `estabaPagado`, reversa el ingreso (o deja la lápida), sea el reembolso a la cartera o en caja. Sin pagar, no toca el libro |

Payloads de ejemplo de E9 y E11 con los ids numéricos de trip, fleet y sales (los campos de más se ignoran):

```json
{ "viajeId": "101", "tipo": "REGULAR", "alquilerId": null, "sucursalId": "1b4e…", "busId": "7",
  "placa": "P777AAA", "choferId": "3", "salarioBase": 300.00, "kmInicial": 12000, "kmFinal": 12250,
  "combustible": 850.75, "llegada": "2026-09-10T20:00:00Z" }
```

```json
{ "ventaId": "5012", "sucursalId": "1b4e…", "canal": "PRESENCIAL", "viajeId": "101",
  "origen": "Quetzaltenango", "destino": "San Marcos", "salida": "2026-10-20T14:00:00Z", "asientos": [4, 5],
  "total": 150.00, "fechaPago": "2026-10-05" }
```

---

## Técnicas

| Método | Ruta | Qué devuelve |
|---|---|---|
| `GET` | `/actuator/health` | `200 {"status":"UP"}`, o `503 {"status":"DOWN"}` si cae la base o RabbitMQ (fleet caído no lo afecta). Público; lo usa el healthcheck de Compose. El gateway no lo expone |
| `GET` | `/v3/api-docs` | Definición OpenAPI 3 en JSON. Por el gateway: `/v3/api-docs/finance` |
| `GET` | `/swagger-ui.html` | Swagger UI del servicio. Las rutas protegidas se prueban con el botón *Authorize* (`bearerAuth`) desde el Swagger del gateway |
