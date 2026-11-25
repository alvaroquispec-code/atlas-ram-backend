-- Atlas RAM · V10: procedencia de los datos y código RENIPRESS.
--
-- 1. data_origin en data_upload e isolate: REAL (resultados de un laboratorio) o SYNTHETIC (datos
--    generados para pruebas y demostración). Todo lo que existe hoy es sintético (seed y cargas de prueba).
--    El valor por defecto de la columna es SYNTHETIC para que un script SQL nunca marque datos como reales
--    por omisión; la API siempre lo escribe de forma explícita.
-- 2. facility.renipress_code: código único de IPRESS asignado por SUSALUD (8 dígitos). Opcional, único.

alter table data_upload add column data_origin varchar(20) not null default 'SYNTHETIC'
    check (data_origin in ('REAL', 'SYNTHETIC'));

alter table isolate add column data_origin varchar(20) not null default 'SYNTHETIC'
    check (data_origin in ('REAL', 'SYNTHETIC'));

alter table facility add column renipress_code varchar(8)
    check (renipress_code ~ '^[0-9]{8}$');
create unique index uk_facility_renipress on facility (renipress_code) where renipress_code is not null;
