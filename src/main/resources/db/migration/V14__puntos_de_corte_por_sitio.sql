-- Atlas RAM · V14: puntos de corte por sitio de infección (meningitis / no meningitis).
--
-- CLSI M100 tiene dos puntos de corte para Streptococcus pneumoniae frente a ceftriaxona y cefepima, porque el
-- antibiótico llega peor al líquido cefalorraquídeo (vigentes desde NCCLS M100-S12, 2002):
--   meningitis       S <= 0,5   I 1   R >= 2 µg/mL
--   no meningitis    S <= 1     I 2   R >= 4 µg/mL
-- Hasta ahora breakpoint guardaba uno solo por germen y antibiótico, así que estos pares no tenían punto de
-- corte y el WISCA mostraba "sin datos" para S. pneumoniae en ceftriaxona. La interpretación elige el sitio
-- por la muestra: líquido cefalorraquídeo = meningitis; cualquier otra = no meningitis.

alter table breakpoint add column site varchar(20) not null default 'ANY'
    check (site in ('ANY', 'MENINGITIS', 'NON_MENINGITIS'));
alter table breakpoint drop constraint uk_breakpoint_combo;
alter table breakpoint add constraint uk_breakpoint_combo
    unique (microorganism_id, antibiotic_id, standard, version, site);

-- Datos del seed (en una base vacía estas dos sentencias no insertan nada; el seed local las repite al final
-- de data.sql). Solo datos SINTÉTICOS.
-- Puntos de corte CLSI de S. pneumoniae frente a ceftriaxona y cefepima, por sitio de infección.
INSERT INTO breakpoint (microorganism_id, antibiotic_id, standard, version, susceptible_max, resistant_min, site)
SELECT m.id, a.id, 'CLSI', v.version, s.s_max, s.r_min, s.site
FROM microorganism m, antibiotic a,
     (VALUES ('2022'), ('2024')) AS v(version),
     (VALUES ('MENINGITIS', 0.5, 2), ('NON_MENINGITIS', 1, 4)) AS s(site, s_max, r_min)
WHERE m.code = 'spn' AND a.code IN ('CRO', 'FEP')
ON CONFLICT DO NOTHING;

-- CIM sintéticas de ceftriaxona para los S. pneumoniae del seed (deterministas: dependen del id del aislamiento).
-- Distribución: <=0,03 40 %, 0,06 18 %, 0,12 12 %, 0,25 9 %, 0,5 7 %, 1 10 %, 2 3 %, 4 1 %.
-- Con los puntos de corte de CLSI reproduce dos cifras peruanas a la vez (ver db/seed/CALIBRACION.md):
--   infección no meníngea, no sensible (CIM >= 2): 4 %   (Castro 2017: 4,5 %; Gomez 2025: 2 %)
--   meningitis, no sensible (CIM >= 1):            14 %  (Torres 2026, periodo 2016-2019: 14 %)
INSERT INTO susceptibility_result (isolate_id, antibiotic_id, mic_value, mic_qualifier, interpretation,
                                   reported_interpretation, interpretation_source, breakpoint_standard,
                                   breakpoint_version)
SELECT i.id, a.id, d.mic, d.qualifier, c.category, c.category, 'CALCULATED', 'CLSI', '2024'
FROM isolate i
JOIN microorganism m ON m.id = i.microorganism_id AND m.code = 'spn'
JOIN antibiotic a ON a.code = 'CRO'
CROSS JOIN LATERAL (SELECT abs(hashtext('CRO-' || i.id)) % 1000 AS u) h
JOIN (VALUES (0, 400, 0.03, 'LE'), (400, 580, 0.06, NULL), (580, 700, 0.12, NULL), (700, 790, 0.25, NULL),
             (790, 860, 0.5, NULL), (860, 960, 1, NULL), (960, 990, 2, NULL), (990, 1000, 4, NULL))
     AS d(lo, hi, mic, qualifier) ON h.u >= d.lo AND h.u < d.hi
CROSS JOIN LATERAL (SELECT CASE
        WHEN i.specimen_type = 'CSF' THEN CASE WHEN d.mic <= 0.5 THEN 'SUSCEPTIBLE'
                                               WHEN d.mic >= 2 THEN 'RESISTANT' ELSE 'INTERMEDIATE' END
        ELSE CASE WHEN d.mic <= 1 THEN 'SUSCEPTIBLE' WHEN d.mic >= 4 THEN 'RESISTANT' ELSE 'INTERMEDIATE' END
    END AS category) c
WHERE i.data_origin = 'SYNTHETIC'
  AND NOT EXISTS (SELECT 1 FROM susceptibility_result r WHERE r.isolate_id = i.id AND r.antibiotic_id = a.id);
