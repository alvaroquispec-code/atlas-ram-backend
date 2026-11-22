-- Atlas RAM · V6: asegurar que la restricción de estados de data_upload admite REVERTED.
-- Las bases creadas antes de Flyway (baseline en v1) conservan la restricción que generó Hibernate
-- con los estados de entonces, sin REVERTED. Se reemplaza por la definición completa, que es la
-- misma de V1: en una base nueva este cambio no altera nada.
alter table data_upload drop constraint if exists data_upload_status_check;
alter table data_upload add constraint data_upload_status_check
    check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERTED'));
