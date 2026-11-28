-- Atlas RAM · V20: verificación sin administración manual (pedido de la asesoría).
--
-- 1. Correo verificado. Sin esto, cualquiera se registra con un correo ajeno de @essalud.gob.pe. Las cuentas
--    que ya existen (las de demostración y las creadas por un administrador) se dan por verificadas.
-- 2. Dominios institucionales y sector de cada establecimiento. Un profesional con correo VERIFICADO del
--    dominio de su hospital recibe el rol sin que nadie lo apruebe a mano.
-- 3. Administradores de establecimiento: el jefe de cada hospital aprueba a su personal (ya no un admin
--    global para todos).
-- 4. RENIPRESS (SUSALUD): el padrón oficial de IPRESS. Es la fuente que decide si un establecimiento es
--    público o privado; nadie lo autodeclara. La tabla se llena con el CSV de datos abiertos (licencia ODC-BY).
-- 5. Registro de clínicas con RUC y código IPRESS.
-- 6. La colegiatura se guarda cifrada (AES-GCM): la columna crece y el formato se valida en la API.

alter table app_user add column email_verified_at timestamptz;
update app_user set email_verified_at = coalesce(created_at, now());

create table email_verification (
    token_hash varchar(64) primary key,
    user_id    bigint      not null references app_user (id) on delete cascade,
    expires_at timestamptz not null,
    used_at    timestamptz
);
create index idx_email_verification_user on email_verification (user_id);

alter table facility add column email_domains varchar(300);
alter table facility add column sector varchar(10) check (sector in ('PUBLIC', 'PRIVATE'));
alter table facility add column ruc varchar(11) check (ruc ~ '^(10|15|16|17|20)[0-9]{9}$');

-- Los 17 hospitales cargados hoy son de MINSA, EsSalud o gobiernos regionales.
update facility set sector = 'PUBLIC';
-- EsSalud usa un dominio único para todo su personal.
update facility set email_domains = 'essalud.gob.pe'
where name in ('Hospital Nacional Edgardo Rebagliati Martins', 'Hospital Nacional Guillermo Almenara Irigoyen',
               'Hospital Nacional Alberto Sabogal Sologuren');

create table facility_manager (
    user_id     bigint not null references app_user (id) on delete cascade,
    facility_id bigint not null references facility (id) on delete cascade,
    primary key (user_id, facility_id)
);

create table renipress (
    code        varchar(8)   primary key check (code ~ '^[0-9]{8}$'),
    name        varchar(300) not null,
    institution varchar(120) not null,
    category    varchar(20),
    ubigeo      varchar(6),
    status      varchar(40),
    public_sector boolean    not null
);

create table facility_request (
    id             bigserial    primary key,
    requester_id   bigint       not null references app_user (id) on delete cascade,
    name           varchar(200) not null,
    ruc            varchar(11)  not null check (ruc ~ '^(10|15|16|17|20)[0-9]{9}$'),
    renipress_code varchar(8)   not null check (renipress_code ~ '^[0-9]{8}$'),
    district_id    bigint       not null references district (id),
    claimed_sector varchar(10)  not null check (claimed_sector in ('PUBLIC', 'PRIVATE')),
    status         varchar(20)  not null default 'PENDING' check (status in ('PENDING', 'APPROVED', 'REJECTED')),
    created_at     timestamptz  not null default now(),
    decided_at     timestamptz,
    decided_by     bigint references app_user (id),
    note           varchar(300),
    facility_id    bigint references facility (id)
);
create index idx_facility_request_status on facility_request (status, created_at);

alter table access_request drop constraint if exists access_request_registration_number_check;
alter table access_request alter column registration_number type varchar(200);
