-- Atlas RAM · V5: revertir cargas y evitar cargas duplicadas.

-- Huella SHA-256 del archivo. El índice único parcial impide que el mismo CSV quede cargado dos veces
-- en el mismo establecimiento; una carga fallida o revertida libera la huella para volver a subirlo.
alter table data_upload add column file_sha256 varchar(64);
create unique index uk_upload_file_active on data_upload (facility_id, file_sha256)
    where file_sha256 is not null and status in ('PENDING', 'PROCESSING', 'COMPLETED');

-- Auditoría de la reversión: quién, cuándo y por qué. La fila de la carga no se borra nunca.
alter table data_upload add column reverted_at timestamp(6) with time zone;
alter table data_upload add column reverted_by bigint;
alter table data_upload add column revert_reason varchar(500);
alter table data_upload add constraint fk_upload_reverted_by foreign key (reverted_by) references app_user;

-- Motivo de cierre cuando no lo decide una persona (por ejemplo, la carga que originó la alerta se revirtió).
alter table resistance_alert add column close_reason varchar(500);
