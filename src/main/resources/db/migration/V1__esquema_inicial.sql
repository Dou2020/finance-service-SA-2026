-- finance_db: réplicas de sucursales y tarifas de depreciación, catálogo de categorías, gastos de
-- taller y el libro de ingresos y costos (DIAGRAMAS.md §7.7), más el inbox de mensajería (copiado
-- de identity). Sin outbox: finance solo consume eventos.

-- Réplica de las sucursales: se llena con SucursalGuardada (E1). Agrupa los reportes por sucursal
-- con su nombre sin preguntarle a identity.
CREATE TABLE sucursal_replica (
    -- El mismo id de identity.sucursal (referencia por id, sin FK: es otra base)
    id      uuid         PRIMARY KEY,
    nombre  varchar(100) NOT NULL,
    activa  boolean      NOT NULL,
    -- aggregateVersion del último E1 aplicado: uno viejo que llega tarde se descarta
    version bigint       NOT NULL
);

-- Réplica de la tarifa de depreciación por km: se llena con ConfiguracionActualizada (E2), solo
-- las de tipo DEPRECIACION_KM. Como en identity, nunca se actualiza: cada cambio es una versión
-- nueva con su vigente_desde, y lo ya calculado con la anterior no se toca.
CREATE TABLE tarifa_depreciacion (
    -- El mismo id de identity.tarifa (referencia por id, sin FK): un E2 repetido no duplica.
    id            bigint         PRIMARY KEY,
    monto_por_km  numeric(12, 4) NOT NULL,
    vigente_desde date           NOT NULL UNIQUE
);

-- Catálogo administrable: una categoría nueva no toca código. Con gastos no se borra, se desactiva.
CREATE TABLE categoria_gasto (
    id     serial      PRIMARY KEY,
    nombre varchar(60) NOT NULL UNIQUE,
    activa boolean     NOT NULL DEFAULT true
);

-- Encabezado del gasto: los totales los calcula el sistema a partir de las líneas.
CREATE TABLE gasto_taller (
    id              uuid           PRIMARY KEY,
    -- Referencia por id a fleet.bus: lo valida S6 al registrar el gasto. La placa se guarda para
    -- los reportes, sin preguntarle a fleet.
    bus_id          uuid           NOT NULL,
    placa_bus       varchar(10)    NOT NULL,
    sucursal_id     uuid           NOT NULL REFERENCES sucursal_replica (id),
    -- La que eligió el usuario (README, "Fechas"); creado_en es la del sistema.
    fecha_servicio  date           NOT NULL,
    total_mano_obra numeric(12, 2) NOT NULL,
    total_repuestos numeric(12, 2) NOT NULL,
    total           numeric(12, 2) NOT NULL,
    -- Referencia por id a identity.usuario (el X-User-Id de quien lo registró)
    registrado_por  uuid           NOT NULL,
    creado_en       timestamptz    NOT NULL DEFAULT now()
);

-- Reportes de taller por sucursal y período.
CREATE INDEX gasto_taller_sucursal_fecha_idx ON gasto_taller (sucursal_id, fecha_servicio);

CREATE TABLE linea_gasto (
    id           bigserial      PRIMARY KEY,
    gasto_id     uuid           NOT NULL REFERENCES gasto_taller (id) ON DELETE CASCADE,
    -- RESTRICT: una categoría con gastos no se puede borrar, solo desactivar (activa = false).
    categoria_id integer        NOT NULL REFERENCES categoria_gasto (id) ON DELETE RESTRICT,
    descripcion  varchar(200)   NOT NULL,
    tipo         varchar(15)    NOT NULL CHECK (tipo IN ('MANO_DE_OBRA', 'REPUESTO')),
    monto        numeric(12, 2) NOT NULL CHECK (monto > 0)
);

CREATE INDEX linea_gasto_gasto_idx ON linea_gasto (gasto_id);

-- Costos de un viaje, al consumir ViajeFinalizado (E9). Se guardan calculados: cambiar la tarifa
-- no altera los registros existentes.
--   depreciacion   = km_recorridos × tarifa_aplicada (la vigente a la fecha de llegada)
--   salario_chofer = salario base, o base × 1.15 en ALQUILER
CREATE TABLE costo_viaje (
    -- Referencia por id a trip.viaje: un E9 repetido no duplica el costo.
    viaje_id        uuid           PRIMARY KEY,
    tipo_viaje      varchar(10)    NOT NULL CHECK (tipo_viaje IN ('REGULAR', 'ALQUILER')),
    -- Referencias por id a fleet.bus y fleet.chofer: sin FK, son otra base.
    bus_id          uuid           NOT NULL,
    placa_bus       varchar(10)    NOT NULL,
    chofer_id       uuid           NOT NULL,
    sucursal_id     uuid           NOT NULL REFERENCES sucursal_replica (id),
    -- La de llegada que eligió el usuario en trip (README, "Fechas"); registrado_en es cuando
    -- finance aplicó el evento.
    fecha           date           NOT NULL,
    km_recorridos   integer        NOT NULL,
    combustible     numeric(12, 2) NOT NULL,
    tarifa_id       bigint         NOT NULL REFERENCES tarifa_depreciacion (id),
    tarifa_aplicada numeric(12, 4) NOT NULL,
    depreciacion    numeric(12, 2) NOT NULL,
    salario_chofer  numeric(12, 2) NOT NULL,
    registrado_en   timestamptz    NOT NULL DEFAULT now()
);

-- Reportes de gastos, ganancias y depreciación por sucursal y período.
CREATE INDEX costo_viaje_sucursal_fecha_idx ON costo_viaje (sucursal_id, fecha);

-- Ingresos por boletos (BoletosVendidos, E11) y alquileres (AlquilerPagado, E14). El monto nunca
-- cambia: BoletosAnulados (E12) y AlquilerCancelado (E16) solo marcan reversado.
CREATE TABLE ingreso (
    id            bigserial      PRIMARY KEY,
    tipo          varchar(10)    NOT NULL CHECK (tipo IN ('BOLETO', 'ALQUILER')),
    -- La venta (sales.venta) o el alquiler (customer.alquiler) de origen, por id
    referencia_id uuid           NOT NULL,
    -- Boletos: la sucursal de origen de la ruta; alquileres: la que lo atiende (README, "Supuestos").
    sucursal_id   uuid           NOT NULL REFERENCES sucursal_replica (id),
    monto         numeric(12, 2) NOT NULL,
    -- La fecha de pago que eligió el usuario (README, "Fechas"); registrado_en es la del sistema.
    fecha         date           NOT NULL,
    detalle       varchar(200)   NOT NULL,
    reversado     boolean        NOT NULL DEFAULT false,
    reversado_en  timestamptz,
    registrado_en timestamptz    NOT NULL DEFAULT now(),
    -- Un ingreso por venta o alquiler: un E11 o E14 repetido no lo duplica.
    CONSTRAINT ingreso_tipo_referencia_uq UNIQUE (tipo, referencia_id)
);

-- Reportes de ingresos y ganancias por sucursal y período.
CREATE INDEX ingreso_sucursal_fecha_idx ON ingreso (sucursal_id, fecha);

-- Inbox: un evento ya aplicado por este consumidor se descarta.
CREATE TABLE processed_event (
    event_id     uuid        NOT NULL,
    consumidor   varchar(60) NOT NULL,
    procesado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumidor)
);
