-- Atlas RAM · V4: calificador de la CIM y procedencia de la interpretación.
--
-- 1. Los equipos automatizados (VITEK 2, Phoenix, MicroScan) y WHONET reportan CIM fuera de rango
--    como "<=0.25" o ">=16". El calificador se guarda aparte del valor numérico.
-- 2. Cada interpretación declara de dónde viene: CALCULATED (calculada por el sistema contra un punto
--    de corte) o LAB_REPORTED (la que informó el laboratorio, cuando no se pudo calcular).

alter table susceptibility_result add column mic_qualifier varchar(2)
    check (mic_qualifier in ('LT','LE','EQ','GE','GT'));

alter table susceptibility_result add column interpretation_source varchar(20) not null default 'CALCULATED'
    check (interpretation_source in ('CALCULATED','LAB_REPORTED'));

-- Corrección retroactiva de la trazabilidad: antes, cuando no había punto de corte (o no había CIM),
-- se guardaba la interpretación del laboratorio pero sellada como "CLSI 2024". Esas filas pasan a
-- LAB_REPORTED y pierden un sello que no les correspondía.
update susceptibility_result r
set interpretation_source = 'LAB_REPORTED',
    breakpoint_standard = null,
    breakpoint_version = null
where r.reported_interpretation is not null
  and (r.mic_value is null
       or not exists (select 1
                      from breakpoint b
                      join isolate i on i.id = r.isolate_id
                      where b.microorganism_id = i.microorganism_id
                        and b.antibiotic_id = r.antibiotic_id
                        and b.standard = r.breakpoint_standard
                        and b.version = r.breakpoint_version));
