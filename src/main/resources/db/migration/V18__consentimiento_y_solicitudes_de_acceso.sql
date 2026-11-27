-- Atlas RAM · V18: consentimiento de datos personales y solicitudes de acceso profesional.
--
-- 1. Consentimiento (Ley 29733 y su reglamento, DS 016-2024-JUS): quien se registra acepta la política de
--    tratamiento de datos; se guarda qué versión aceptó y cuándo. Las cuentas anteriores quedan en null.
-- 2. Solicitudes de acceso: un profesional de la salud se registra como usuario de consulta y pide un rol
--    clínico en un establecimiento, con su número de colegiatura. El sistema valida lo que puede validar
--    (formato, establecimiento, rol permitido) y un administrador aprueba con un clic. Al aprobar, el usuario
--    recibe el rol y queda asignado a ese establecimiento.

alter table app_user add column data_policy_version varchar(20);
alter table app_user add column data_policy_accepted_at timestamptz;

create table access_request (
    id                  bigserial primary key,
    user_id             bigint       not null references app_user on delete cascade,
    facility_id         bigint       not null references facility,
    requested_role      varchar(30)  not null
        check (requested_role in ('LAB_TECHNICIAN', 'EPIDEMIOLOGIST', 'PHARMACIST')),
    professional_body   varchar(10)  not null check (professional_body in ('CMP', 'CTMP', 'CBP', 'CQFP')),
    registration_number varchar(10)  not null check (registration_number ~ '^[0-9]{4,6}$'),
    status              varchar(10)  not null default 'PENDING'
        check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    created_at          timestamptz  not null default now(),
    decided_at          timestamptz,
    decided_by          bigint references app_user,
    note                varchar(300)
);

create index idx_access_request_status on access_request (status, created_at);
-- Una sola solicitud pendiente por usuario.
create unique index uk_access_request_pending on access_request (user_id) where status = 'PENDING';
