-- Ids de fleet, trip y sales como texto (decisión del 2026-10-25, plan de eventos, fase 0). Esos
-- servicios numeran con bigserial y mandan su id tal cual ("101"); V1 los suponía UUID, y un id
-- numérico no entraba ni por S6 (400 al leer el body) ni por E9, E11 o E12 (directo a la DLQ).
-- varchar y no bigint: así cabe también un productor que mande UUID, y finance no depende de cómo
-- numera cada uno. 64 es el tope que valida la aplicación (Payloads.LARGO_ID, GastoTallerRequest).
--
-- Los UUID ya guardados pasan a su texto canónico (minúsculas con guiones), el mismo de
-- UUID.toString(): un reverso que llegue después sigue encontrando su ingreso.
--
-- sucursal_id (identity) y registrado_por (identity) siguen uuid. La PK de costo_viaje y la UQ
-- (tipo, referencia_id) de ingreso se reconstruyen solas con el cambio de tipo; ninguna FK apunta a
-- estas columnas.

-- La placa sube de 10 a 20, el largo que fleet admite (placa VARCHAR(20) en su V1): una más larga
-- daba 409 al registrar el gasto y mandaba el E9 a la DLQ después de los reintentos.

ALTER TABLE gasto_taller
    ALTER COLUMN bus_id TYPE varchar(64) USING bus_id::text,
    ALTER COLUMN placa_bus TYPE varchar(20);

ALTER TABLE costo_viaje
    ALTER COLUMN viaje_id TYPE varchar(64) USING viaje_id::text,
    ALTER COLUMN bus_id TYPE varchar(64) USING bus_id::text,
    ALTER COLUMN chofer_id TYPE varchar(64) USING chofer_id::text,
    ALTER COLUMN placa_bus TYPE varchar(20);

-- La venta de sales o el alquiler de customer (este sigue siendo un UUID, guardado como texto).
ALTER TABLE ingreso
    ALTER COLUMN referencia_id TYPE varchar(64) USING referencia_id::text;
