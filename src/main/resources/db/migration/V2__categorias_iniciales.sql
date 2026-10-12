-- Nombre único sin distinguir mayúsculas: "Llantas" y "llantas" serían la misma categoría partida
-- en dos en los reportes. La UQ de V1 (categoria_gasto_nombre_key) sigue ahí; este índice la cubre.
CREATE UNIQUE INDEX categoria_gasto_nombre_ci_uq ON categoria_gasto (lower(nombre));

-- Las categorías que da el enunciado como ejemplo. Son el catálogo inicial, no datos de
-- demostración: el SYS_ADMIN las renombra, desactiva o borra (sin gastos) como cualquier otra.
INSERT INTO categoria_gasto (nombre)
VALUES ('Llantas'), ('Motor'), ('Frenos'), ('Suspensión'), ('Cambio de aceite'), ('Eléctrico'),
       ('Carrocería'), ('Otros')
ON CONFLICT DO NOTHING;
