-- Atlas RAM · V16: registro de auditoría de accesos.
--
-- Quién hizo qué y cuándo: toda escritura (POST, PUT, DELETE) y toda lectura de datos individuales
-- (aislamientos, FHIR, usuarios). Lo pide el tratamiento de datos de salud (Ley 29733 y su reglamento,
-- DS 016-2024-JUS: medidas de seguridad y trazabilidad) y la recomendación de OWASP de registrar accesos.
-- Solo guarda la ruta sin parámetros de consulta y el correo del usuario: nunca datos del paciente.

create table audit_log (
    id          bigserial primary key,
    occurred_at timestamptz  not null default now(),
    user_email  varchar(255),
    method      varchar(10)  not null,
    path        varchar(500) not null,
    status      integer      not null,
    client_ip   varchar(64)
);

create index idx_audit_log_occurred on audit_log (occurred_at desc);
create index idx_audit_log_user on audit_log (user_email, occurred_at desc);
