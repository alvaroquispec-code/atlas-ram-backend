-- Atlas RAM · V7: seudónimo del paciente y código del aislamiento en el laboratorio.

-- HMAC-SHA256 en hexadecimal (64 caracteres). Nunca se guarda el identificador original.
alter table isolate add column patient_key varchar(64);
-- Número de muestra o de accesión del laboratorio.
alter table isolate add column lab_isolate_id varchar(50);

-- Regla del primer aislamiento (CLSI M39): buscar el primer aislamiento de cada paciente y especie.
create index idx_isolate_patient_first
    on isolate (patient_key, microorganism_id, collection_date, id)
    where patient_key is not null;

-- Un mismo aislamiento del laboratorio no puede registrarse dos veces, aunque llegue en otro archivo.
create unique index uk_isolate_lab_id
    on isolate (facility_id, lab_isolate_id, microorganism_id)
    where lab_isolate_id is not null;
