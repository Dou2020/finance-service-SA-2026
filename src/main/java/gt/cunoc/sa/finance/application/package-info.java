/**
 * Casos de uso que no son de un solo recurso o no tienen endpoint: los manejadores de eventos
 * (ReplicaSucursales y ReplicaTarifas aplican E1 y E2; CostosDeViaje registra los costos de E9;
 * LibroIngresos registra los ingresos de E11 y E14 y los reversa con E12 y E16), la regla de alcance
 * (Alcance: un BRANCH_ADMIN solo ve los gastos y reportes de su sucursal) y el intervalo de fechas
 * que comparten la lista de gastos y los reportes (Periodo). Lo propio de un recurso (transacción y
 * @PreAuthorize) vive en su *Service.
 */
package gt.cunoc.sa.finance.application;
