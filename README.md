# finance-service

Puerto `8086` · base `finance_db` · paquete `gt.cunoc.sa.finance`. El diseño (taller y categorías, costos
del viaje, libro de ingresos, reportes) está en el [README del proyecto](../README.md).

finance solo consume eventos (no tiene outbox): arma las réplicas de sucursales y tarifas de depreciación,
el libro de ingresos (boletos y alquileres, con sus reversos) y los costos de cada viaje. Los gastos de
taller y su catálogo de categorías se registran aquí, con el bus validado en fleet (S6). finance se hizo antes
que fleet, trip y sales, que llegaron importados y todavía no cumplen el contrato: S6, E9, E11 y E12 se
implementaron contra el de [API.md](API.md#llama-a) y se prueban con WireMock y eventos publicados a mano. Los
ids de esos tres servicios (bus, viaje, chofer, venta) se guardan como texto (`V3`): ellos numeran con
bigserial y los mandan tal cual (`"7"`).

## Librerías

Spring Boot `3.5.16` sobre Java 21. Las versiones que el `pom.xml` no fija las pone el BOM de Boot;
aquí van resueltas (las mismas que empaqueta `target/app.jar`). Son las de identity-service sin Redis ni
Nimbus, más Resilience4j para el cliente de fleet.

| Librería | Versión | Para qué |
|---|---|---|
| Spring Web MVC · Tomcat | 6.2.19 · 10.1.55 | API REST (`/api/**` y `/internal/**`) y `RestClient` hacia fleet |
| Spring Data JPA · Hibernate ORM · HikariCP | 3.5.13 · 6.6.53.Final · 6.3.3 | Persistencia en `finance_db`, con `ddl-auto: validate` |
| Hibernate Validator (Bean Validation) | 8.0.3.Final | Validación de los `XRequest` |
| Spring Security | 6.5.11 | Autoriza con los `X-User-*` del gateway (`@PreAuthorize`); no valida el JWT |
| Spring AMQP (`spring-rabbit`) · cliente AMQP | 3.2.12 · 5.25.0 | Consume E1, E2, E9, E11, E12, E14 y E16; no publica nada (sin outbox) |
| Flyway (`flyway-core` + `flyway-database-postgresql`) | 11.7.2 | Migraciones de `db/migration` |
| PostgreSQL JDBC | 42.7.11 | Driver de `finance_db` (PostgreSQL 16) |
| Spring Boot Actuator · Micrometer | 3.5.16 · 1.15.12 | `/actuator/health`, el healthcheck de Compose |
| springdoc-openapi (`webmvc-ui`) · Swagger UI | 2.8.17 · 5.32.2 | `/v3/api-docs` y Swagger UI; el gateway los agrega |
| Resilience4j (`resilience4j-spring-boot3`) · Spring AOP (AspectJ) | 2.3.0 · 1.9.25.1 | Circuit breaker y retry del cliente de fleet (S6), por anotaciones; sin AOP se ignoran en silencio |

| Pruebas | Versión | Para qué |
|---|---|---|
| JUnit Jupiter · Mockito · AssertJ (`spring-boot-starter-test`) | 5.12.2 · 5.17.0 · 3.27.7 | Pruebas unitarias y de integración (`@SpringBootTest`) |
| Testcontainers (`junit-jupiter`, `postgresql`, `rabbitmq`) + `spring-boot-testcontainers` | 1.21.4 · 3.5.16 | PostgreSQL y RabbitMQ reales (`ContenedoresDePrueba`) |
| WireMock (`wiremock-standalone`) | 3.13.2 | fleet (S6) simulado por HTTP real: ejercita los timeouts del `HttpClient`, el reintento y el circuito |
| Awaitility | 4.2.2 | Esperar efectos asíncronos (listener) sin `Thread.sleep` |
| Spring Security Test | 6.5.11 | Declarada, sin uso todavía: las pruebas mandan los `X-User-*` directo (`Escenario.como`) |

Imágenes: `maven:3.9-eclipse-temurin-21` compila y `eclipse-temurin:21-jre` corre (`Dockerfile`);
`postgres:16-alpine` para `finance-db`. Las pruebas usan `postgres:16-alpine` y
`rabbitmq:4-management-alpine`.

## Estructura

```
finance-service/
├── pom.xml                                Boot 3.5.16, finalName app; web, JPA, Flyway, Security, AMQP, springdoc, Resilience4j
├── Dockerfile                             dos etapas: Maven compila, JRE 21 corre target/app.jar
├── docker-compose.yml                     proyecto Compose propio: finance-db (red privada) + el servicio (también en transporte)
├── .dockerignore                          deja target/, .env y archivos del IDE fuera de la imagen
├── .gitignore                             lo que no se versiona: target/, .env (no .env.example), logs, IDE y SO
├── .env.example                           variables de su compose (su base y RabbitMQ); se copia a .env, que no se versiona
├── README.md                              estructura y endpoints (este archivo)
├── API.md                                 cada endpoint en detalle, la llamada a fleet y el contrato de los eventos que consume
└── src/
    ├── main/
    │   ├── java/gt/cunoc/sa/finance/
    │   │   ├── FinanceApplication.java    arranque; sin @EnableScheduling (no hay relay ni jobs)
    │   │   ├── api/                       un paquete por recurso: controller, service (transacción y @PreAuthorize), repository y dto/
    │   │   │   ├── categoria/                               /api/categorias-gasto
    │   │   │   │   ├── CategoriaController.java             lista, alta, renombrar, estado, eliminar
    │   │   │   │   ├── CategoriaService.java                solo SYS_ADMIN la administra; con gastos no se elimina (409)
    │   │   │   │   ├── CategoriaRepository.java             por nombre sin mayúsculas, ¿tiene gastos?, FOR SHARE al usarlas
    │   │   │   │   └── dto/                                 CategoriaRequest, EstadoCategoriaRequest, CategoriaResponse
    │   │   │   ├── gasto/                                   /api/gastos-taller
    │   │   │   │   ├── GastoTallerController.java           registro, lista paginada, detalle
    │   │   │   │   ├── GastoTallerService.java              S6 fuera de la transacción, alcance, categorías activas, totales
    │   │   │   │   ├── GastoTallerRepository.java           detalle con líneas, filtros (Specification), encabezados del período
    │   │   │   │   └── dto/                                 GastoTallerRequest, LineaGastoRequest, GastoTallerResponse, ResumenGastoResponse
    │   │   │   ├── reporte/                                 /api/finanzas/reportes y los repositorios del libro
    │   │   │   │   ├── ReporteController.java               los 6 reportes, con desde, hasta, sucursalId y busId
    │   │   │   │   ├── ReporteService.java                  filas del período, agrupado y totales; orden alfabético en español
    │   │   │   │   ├── IngresoRepository.java               registrar y reversar (o lápida) en una sentencia; los vigentes del período
    │   │   │   │   ├── CostoViajeRepository.java            costos de viaje del período
    │   │   │   │   ├── LineaGastoRepository.java            líneas con su gasto y categoría (LineaDeTaller) para los reportes de taller
    │   │   │   │   ├── LineaDeTaller.java                   proyección: una línea con su encabezado y su categoría
    │   │   │   │   ├── SucursalReplicaRepository.java       upsert de E1 que descarta una versión vieja
    │   │   │   │   ├── TarifaDepreciacionRepository.java    insertar la versión de E2; la vigente a una fecha
    │   │   │   │   └── dto/                                 Ingresos y Costos (montos agrupados) y un XResponse por reporte
    │   │   │   ├── interno/                                 /internal/**: solo red interna, el gateway no las expone
    │   │   │   │   └── InternoController.java               reprocesar la DLQ
    │   │   │   └── error/
    │   │   │       ├── ManejadorErrores.java                todo error → ProblemDetail; restricciones (nombre, RESTRICT, sucursal) → 409
    │   │   │       ├── ErrorDeNegocio.java                  error esperado: 400, 401, 404, 409 o 422 con su detalle, o el 4xx de fleet
    │   │   │       └── ServicioNoDisponibleException.java   fleet (S6) no respondió o su circuito está abierto → 503
    │   │   ├── domain/                    entidades JPA y reglas
    │   │   │   ├── CategoriaGastoEntity.java           catálogo; nombre sin espacios de más
    │   │   │   ├── GastoTallerEntity.java              encabezado (bus de fleet como texto); calcula los totales de sus líneas al crearse
    │   │   │   ├── LineaGastoEntity.java               categoría, descripción, tipo y monto
    │   │   │   ├── CostoViajeEntity.java               costos de un viaje (ids de trip y fleet como texto): depreciación = km × tarifa, salario + 15 % en alquiler
    │   │   │   ├── IngresoEntity.java                  una fila del libro, con la venta o el alquiler como texto (solo lectura: la escribe IngresoRepository)
    │   │   │   ├── SucursalReplicaEntity.java          réplica de E1 (solo lectura)
    │   │   │   ├── TarifaDepreciacionEntity.java       réplica de E2 (solo lectura)
    │   │   │   ├── TipoLinea.java                      MANO_DE_OBRA, REPUESTO
    │   │   │   ├── TipoIngreso.java                    BOLETO, ALQUILER
    │   │   │   ├── TipoViaje.java                      REGULAR, ALQUILER
    │   │   │   ├── Rol.java                            SYS_ADMIN, BRANCH_ADMIN, CASHIER, DRIVER, CUSTOMER
    │   │   │   ├── Calendario.java                     "hoy" en America/Guatemala, no en UTC
    │   │   │   └── ConIdAsignado.java                  Persistable: persist() y no merge() con id asignado (UUID o texto)
    │   │   ├── application/               casos de uso compartidos entre recursos o sin endpoint; regla de alcance
    │   │   │   ├── ReplicaSucursales.java               E1
    │   │   │   ├── ReplicaTarifas.java                  E2, solo DEPRECIACION_KM
    │   │   │   ├── CostosDeViaje.java                   E9, con la tarifa vigente a la fecha de llegada
    │   │   │   ├── LibroIngresos.java                   E11 y E14 registran; E12 y E16 reversan o dejan la lápida
    │   │   │   ├── ReplicaPendienteException.java       falta la sucursal o la tarifa → DLQ hasta reprocesar
    │   │   │   ├── Periodo.java                         desde y hasta opcionales; 400 si están al revés
    │   │   │   ├── Alcance.java                         un BRANCH_ADMIN solo toca su sucursal → 403
    │   │   │   └── package-info.java
    │   │   └── infrastructure/
    │   │       ├── client/                                 llamadas REST a otros servicios (modelo: PRACTICA_5)
    │   │       │   ├── ClientesConfig.java                 HttpClient del JDK: conexión 1 s, respuesta 2 s, HTTP/1.1
    │   │       │   ├── ErroresRemotos.java                 4xx → ErrorDeNegocio con su detail; 5xx y transporte → 503; X-Correlation-Id
    │   │       │   ├── FleetClient.java                    S6 con circuit breaker y un reintento; el id va como texto, codificado entero
    │   │       │   └── BusRemoto.java                      lo que se lee del bus: id (texto, aunque llegue como número), placa, sucursal, estado
    │   │       ├── messaging/                              inbox y RabbitMQ (copiado de identity; sin outbox)
    │   │       │   ├── Sobre.java                          sobre de cada evento (eventId, type, aggregateVersion…)
    │   │       │   ├── Payloads.java                       payload → record; ilegible, incompleto o con un id vacío o de más de 64 → DLQ (copiado de customer)
    │   │       │   ├── Inbox.java                          un evento ya aplicado no tiene efecto
    │   │       │   ├── ProcessedEvent.java                 fila de processed_event
    │   │       │   ├── ProcessedEventRepository.java       INSERT … ON CONFLICT DO NOTHING
    │   │       │   ├── ListenerEventos.java                consume finance.events y reparte por tipo
    │   │       │   ├── TopologiaRabbit.java                exchanges, cola, DLQ, alternate-exchange y los 7 bindings (E1, E2, E9, E11, E12, E14, E16)
    │   │       │   ├── ReintentosConfig.java               reintentos a 1, 2, 4 y 8 s; lo no reintentable va a la DLQ
    │   │       │   ├── EventoNoProcesableException.java    evento que nunca va a funcionar → DLQ
    │   │       │   ├── ReprocesadorDlq.java                devuelve la DLQ a la cola
    │   │       │   └── eventos/                            payload de cada evento consumido, con los campos que finance lee
    │   │       │       ├── TiposEvento.java                routing keys: consume E1, E2, E9, E11, E12, E14, E16; no publica
    │   │       │       ├── SucursalGuardada.java           E1
    │   │       │       ├── ConfiguracionActualizada.java   E2
    │   │       │       ├── ViajeFinalizado.java            E9 (contrato con trip; viajeId, busId y choferId como texto)
    │   │       │       ├── BoletosVendidos.java            E11 (contrato con sales: lo de customer más sucursalId; ventaId y viajeId como texto)
    │   │       │       ├── BoletosAnulados.java            E12 (los mismos campos y motivo)
    │   │       │       ├── AlquilerPagado.java             E14
    │   │       │       └── AlquilerCancelado.java          E16
    │   │       ├── security/
    │   │       │   ├── SeguridadConfig.java                cadena de Spring Security; 401 y 403 como ProblemDetail
    │   │       │   ├── EncabezadosGatewayFilter.java       X-User-* del gateway → UsuarioActual
    │   │       │   └── UsuarioActual.java                  principal: usuario, sucursal, chofer (id de fleet, texto) y roles
    │   │       └── web/
    │   │           ├── CorrelationIdFilter.java            X-Correlation-Id → MDC y respuesta
    │   │           ├── DocumentacionConfig.java            OpenAPI: servidor relativo y bearerAuth
    │   │           └── WebConfig.java                      páginas como {content, page}
    │   └── resources/
    │       ├── application.yml                 puerto, base, RabbitMQ, URL de fleet y Resilience4j
    │       └── db/migration/                   Flyway
    │           ├── V1__esquema_inicial.sql     réplicas de sucursales y tarifas de depreciación, categorías,
    │           │                               gastos de taller con sus líneas, costos de viaje, ingresos e inbox
    │           ├── V2__categorias_iniciales.sql nombre único sin mayúsculas y las 8 categorías del enunciado
    │           └── V3__ids_como_texto.sql      ids de fleet, trip y sales de uuid a varchar(64) (bus, viaje, chofer,
    │                                           venta); la placa, de 10 a 20 como en fleet
    └── test/
        └── java/gt/cunoc/sa/finance/
            ├── PruebaDeIntegracion.java        meta-anotación: un solo contexto para todas
            ├── ContenedoresDePrueba.java       PostgreSQL y RabbitMQ en Testcontainers; WireMock como fleet
            ├── Escenario.java                  usuarios del gateway, sucursales, categorías y gastos en la base; buses en WireMock (UUID o id numérico, como fleet)
            ├── Publicador.java                 publica eventos como identity, trip, sales o customer; inbox y DLQ
            ├── FinanceApplicationTests.java    migraciones aplicadas (V1–V3), ids de los importados como texto, sin outbox, categorías iniciales
            ├── SeguridadTest.java              401, 403 de un chofer con id numérico, 404, health y X-Correlation-Id
            ├── domain/
            │   ├── GastoTallerEntityTest.java  totales por tipo, sin líneas, nombre de categoría
            │   └── CostoViajeEntityTest.java   depreciación, +15 % en alquiler, redondeo, km al revés
            └── application/
                ├── ReplicasIT.java             E1 (versión vieja, duplicado, DLQ) y E2 (una vez; alquiler descartado)
                ├── CategoriasIT.java           CRUD, nombre repetido, borrar con y sin gastos, autorización
                ├── GastosTallerIT.java         S6 en WireMock: totales, alcance, 404 de fleet, 422, 409, listas y 400; id numérico de fleet, busId inválido, "/" codificada
                ├── FleetCaidoIT.java           500 con reintento, timeout, circuito abierto; los 404 no lo abren; 200 sin id o con sucursal que no es UUID
                ├── LibroIngresosIT.java        E11, E12, E14, E16: duplicado, reverso, lápida, sucursal sin replicar; ids numéricos de sales, ventaId inválido
                ├── CostosDeViajeIT.java        E9: tarifa a la fecha de llegada en Guatemala, alquiler, sin tarifa, DLQ; ids numéricos (texto y número JSON), id inválido
                └── ReportesIT.java             los 6 reportes sobre dos sucursales (una con ids numéricos): totales, orden, alcance, 403 y 400
```

## Endpoints

Resumen de una línea por endpoint. El detalle de cada uno (campos y validaciones, ejemplos, cada error
con su `detail` y sus efectos) está en [API.md](API.md).

Las rutas `/api/**` llegan por el gateway, que valida el JWT y agrega `X-User-*`. Todas las respuestas de
error son `ProblemDetail`; sin identidad → `401`, rol o sucursal ajenos → `403`. Un `BRANCH_ADMIN` siempre
ve su sucursal; el `SYS_ADMIN`, todas o la que pida con `sucursalId`.

### Categorías de gasto — `/api/categorias-gasto`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| GET | `/api/categorias-gasto` | `SYS_ADMIN`, `BRANCH_ADMIN` | Lista por nombre; filtro `activa` |
| POST | `/api/categorias-gasto` | `SYS_ADMIN` | `{nombre}` → `201`. `409` si ya existe, sin distinguir mayúsculas |
| PUT | `/api/categorias-gasto/{id}` | `SYS_ADMIN` | `{nombre}` renombra. `409` si otra lo usa |
| PATCH | `/api/categorias-gasto/{id}/estado` | `SYS_ADMIN` | `{activa}`; desactivada no se ofrece para gastos nuevos |
| DELETE | `/api/categorias-gasto/{id}` | `SYS_ADMIN` | `204`. `409` si tiene gastos: solo se desactiva |

### Gastos de taller — `/api/gastos-taller`

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/api/gastos-taller` | `BRANCH_ADMIN` | `{busId, fechaServicio?, lineas: [{categoriaId, descripcion, tipo, monto}]}` → `201` con los totales. `busId` es el id de fleet como texto (`"7"`). Valida el bus en fleet (S6): `404` de fleet, `403` de otra sucursal, `503` si no responde; `422` categoría inexistente o desactivada, o fecha futura |
| GET | `/api/gastos-taller` | `SYS_ADMIN` (`sucursalId?`), `BRANCH_ADMIN` | Paginado, del servicio más reciente; filtros `busId` (texto), `desde`, `hasta` |
| GET | `/api/gastos-taller/{id}` | `SYS_ADMIN`, `BRANCH_ADMIN` de su sucursal | Encabezado y líneas con el nombre de su categoría |

### Reportes — `/api/finanzas/reportes`

Todos para `SYS_ADMIN` y `BRANCH_ADMIN`, con `desde` y `hasta` opcionales sobre la fecha de negocio (sin
ellos, todos los registros; `400` si están al revés) y `sucursalId` opcional (solo `SYS_ADMIN`).

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/finanzas/reportes/ingresos` | Por sucursal, por fecha de pago: cada ingreso, subtotales de boletos y alquileres, total. Sin reversados |
| GET | `/api/finanzas/reportes/gastos` | Por sucursal: cada viaje (combustible, depreciación, salario) y gasto de taller (mano de obra, repuestos), subtotales por concepto, totales |
| GET | `/api/finanzas/reportes/ganancias` | Por sucursal en orden alfabético: ingresos, costos y ganancia neta, con totales |
| GET | `/api/finanzas/reportes/taller-por-categoria` | Por categoría: cada línea, total y gran total; `busId` opcional |
| GET | `/api/finanzas/reportes/taller-por-bus` | Por bus: subtotal por categoría y total del bus; `busId` opcional |
| GET | `/api/finanzas/reportes/depreciacion-por-bus` | Por bus: viajes, km y depreciación acumulada; tarifa por km vigente hoy |

### Internas — `/internal` (red interna, sin gateway)

| Método | Ruta | Quién | Qué hace |
|---|---|---|---|
| POST | `/internal/dlq/reprocesar` | operador | Reencola los mensajes de `finance.events.dlq` → `{reprocesados}` |

### Técnicas

| Método | Ruta | Qué es |
|---|---|---|
| GET | `/actuator/health` | Salud (healthcheck de Compose) |
| GET | `/v3/api-docs` · `/swagger-ui.html` | OpenAPI y Swagger UI |

## Levantar en desarrollo

Proyecto Compose propio (`docker-compose.yml` de esta carpeta): `finance-db` y el servicio. Los comandos van
desde `finance-service/`, salvo el de la infra. El [README del proyecto](../README.md#levantar-en-desarrollo)
lo describe con `stack.sh`, que todavía no existe; aquí va con `docker compose` directo.

1. **Infra**, desde la raíz del proyecto, con su `.env` copiado de `.env.example`. Va primero porque crea la red
   `transporte`, que este compose declara externa:

   ```bash
   docker compose up -d --wait                  # Redis, RabbitMQ y la red transporte
   ```

2. **`.env`** de esta carpeta. No hay nada que completar: `RABBITMQ_USER` y `RABBITMQ_PASSWORD` tienen que ser
   los de `../.env`, y los de la base pueden quedar como vienen:

   ```bash
   cp .env.example .env
   ```

3. **En Docker**:

   ```bash
   docker compose up -d --build --wait          # base y servicio; espera a que estén healthy
   curl localhost:8086/actuator/health          # {"status":"UP"}
   ```

   Swagger en http://localhost:8086/swagger-ui.html. Para el día a día:

   ```bash
   docker compose logs -f finance-service
   docker compose up -d --build finance-service    # después de cambiar el código
   docker compose down                          # baja todo; la base se conserva en su volumen
   docker compose down -v                       # además borra la base: Flyway la recrea al arrancar
   ```

4. **Fuera de Docker**, con Java 21 y Maven. Solo la base va en Docker. El servicio usa el mismo puerto que su
   contenedor, así que ese contenedor se detiene primero:

   ```bash
   docker compose stop finance-service          # si estaba arriba
   docker compose up -d --wait finance-db
   set -a; . ./.env; set +a; mvn spring-boot:run
   ```

   La base y RabbitMQ los encuentra en `localhost`, porque los dos se publican en `127.0.0.1`. El usuario de la
   base sale de `DB_USER` y `DB_PASSWORD`, no de `FINANCE_DB_USER`. Sus valores por defecto en
   `application.yml` son los del `.env.example`; si se cambiaron en `.env`, también hay que exportarlos. fleet
   (S6) se busca en `FLEET_URL`, por defecto `http://localhost:8084`, el puerto del fleet importado (en el
   compose, `http://fleet-service:8084`). El 8082 del diseño lo usa trip.

5. **Pruebas**, con Docker corriendo. Testcontainers levanta su propio PostgreSQL y RabbitMQ, y fleet es un
   servidor falso (WireMock), así que no usa la infra, la base de arriba ni fleet:

   ```bash
   mvn -q test                                  # todas, incluidas las *IT
   mvn -q test -Dtest=GastosTallerIT            # una clase
   ```

6. **Probar**: el catálogo de categorías ([`GET /api/categorias-gasto`](API.md#get-apicategorias-gasto)).
   Directo al 8086, los `X-User-*` que pone el gateway se ponen a mano:

   ```bash
   curl -s localhost:8086/api/categorias-gasto \
     -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' -H 'X-User-Roles: SYS_ADMIN'
   ```

   Devuelve las categorías iniciales (Llantas, Motor, Frenos…). Registrar un gasto de taller
   (`POST /api/gastos-taller`) necesita S6, y hoy no se puede: el compose ya busca fleet en su puerto
   (`http://fleet-service:8084`) y el id numérico de fleet entra como texto, pero el fleet importado todavía no
   expone `GET /internal/buses/{id}` (ola 2 del plan de eventos). Con fleet abajo da `503`; con fleet arriba,
   el `4xx` que responda fleet. Los libros y los reportes se llenan con los eventos de identity, trip, sales y
   customer.
