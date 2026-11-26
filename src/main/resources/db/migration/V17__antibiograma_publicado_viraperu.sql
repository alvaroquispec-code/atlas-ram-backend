-- Atlas RAM · V17: datos REALES publicados (agregados), separados de los aislamientos.
--
-- En el Perú no hay datos abiertos a nivel de aislamiento: lo real y público son agregados publicados
-- (artículos de acceso abierto, mapas microbiológicos de hospitales). Se guardan aparte, con su fuente y
-- su licencia, para no mezclarlos nunca con aislamientos individuales ni con los datos sintéticos.
--
-- Primera fuente: estudio VIRAPERU, Tabla 1. Krapp F, García C, Hinostroza N, et al. Prevalence of
-- Antimicrobial Resistance in Gram-Negative Bacteria Bloodstream Infections in Peru and Associated
-- Outcomes: VIRAPERU Study. Am J Trop Med Hyg. 2023;109(5):1095-1106. doi:10.4269/ajtmh.22-0556.
-- Licencia CC BY (permite reutilizar citando). Hemocultivos, primer aislamiento por paciente,
-- 15 hospitales públicos de 12 regiones, julio 2017 a octubre 2019, puntos de corte CLSI M100 (31.ª ed.).

create table published_source (
    id                  bigserial primary key,
    code                varchar(40)   not null unique,
    title               varchar(300)  not null,
    citation            varchar(600)  not null,
    url                 varchar(300)  not null,
    license             varchar(60)   not null,
    setting             varchar(300)  not null,
    specimen_type       varchar(20)   not null,
    period_start        date          not null,
    period_end          date          not null,
    breakpoint_standard varchar(60)   not null
);

create table published_result (
    id               bigserial primary key,
    source_id        bigint  not null references published_source on delete cascade,
    microorganism_id bigint  not null references microorganism,
    antibiotic_id    bigint  not null references antibiotic,
    -- Nombre del organismo como lo informa la fuente (p. ej. "Acinetobacter spp.").
    organism_label   varchar(80) not null,
    tested           integer not null check (tested > 0),
    resistant        integer not null check (resistant >= 0 and resistant <= tested),
    unique (source_id, microorganism_id, antibiotic_id)
);

insert into published_source (code, title, citation, url, license, setting, specimen_type, period_start, period_end,
                              breakpoint_standard)
values ('VIRAPERU-2023',
        'VIRAPERU: resistencia en bacteriemias por gramnegativos en el Perú',
        'Krapp F, García C, Hinostroza N, et al. Prevalence of Antimicrobial Resistance in Gram-Negative Bacteria Bloodstream Infections in Peru and Associated Outcomes: VIRAPERU Study. Am J Trop Med Hyg. 2023;109(5):1095-1106',
        'https://doi.org/10.4269/ajtmh.22-0556',
        'CC BY',
        '15 hospitales públicos de 12 regiones del Perú (Lima, Arequipa, Cusco, Iquitos, Trujillo, Chiclayo y otras)',
        'BLOOD', '2017-07-01', '2019-10-31', 'CLSI M100, 31.ª edición (2021)');

-- Tabla 1 del artículo: n resistentes de n aislamientos por especie. Solo antibióticos que el estudio probó
-- en esa especie. Organismos y antibióticos se buscan por su código WHONET: si alguno no existe en el
-- catálogo, esa fila simplemente no se inserta.
insert into published_result (source_id, microorganism_id, antibiotic_id, organism_label, tested, resistant)
select s.id, m.id, a.id, v.label, v.tested, v.resistant
from (values
    ('eco', 'Escherichia coli', 'CRO', 199, 136), ('eco', 'Escherichia coli', 'CAZ', 199, 86),
    ('eco', 'Escherichia coli', 'FEP', 199, 106), ('eco', 'Escherichia coli', 'ETP', 199, 0),
    ('eco', 'Escherichia coli', 'IPM', 199, 0),   ('eco', 'Escherichia coli', 'MEM', 199, 0),
    ('eco', 'Escherichia coli', 'CIP', 199, 150), ('eco', 'Escherichia coli', 'AMK', 199, 13),
    ('eco', 'Escherichia coli', 'GEN', 199, 74),  ('eco', 'Escherichia coli', 'SXT', 199, 131),
    ('eco', 'Escherichia coli', 'TZP', 199, 6),
    ('kpn', 'Klebsiella pneumoniae', 'CRO', 118, 81), ('kpn', 'Klebsiella pneumoniae', 'CAZ', 118, 69),
    ('kpn', 'Klebsiella pneumoniae', 'FEP', 118, 64), ('kpn', 'Klebsiella pneumoniae', 'ETP', 118, 13),
    ('kpn', 'Klebsiella pneumoniae', 'IPM', 118, 8),  ('kpn', 'Klebsiella pneumoniae', 'MEM', 118, 11),
    ('kpn', 'Klebsiella pneumoniae', 'CIP', 118, 80), ('kpn', 'Klebsiella pneumoniae', 'AMK', 118, 5),
    ('kpn', 'Klebsiella pneumoniae', 'GEN', 118, 58), ('kpn', 'Klebsiella pneumoniae', 'SXT', 118, 78),
    ('kpn', 'Klebsiella pneumoniae', 'TZP', 118, 21),
    ('pae', 'Pseudomonas aeruginosa', 'CAZ', 81, 16), ('pae', 'Pseudomonas aeruginosa', 'FEP', 81, 17),
    ('pae', 'Pseudomonas aeruginosa', 'IPM', 81, 28), ('pae', 'Pseudomonas aeruginosa', 'MEM', 81, 23),
    ('pae', 'Pseudomonas aeruginosa', 'CIP', 81, 22), ('pae', 'Pseudomonas aeruginosa', 'AMK', 81, 17),
    ('pae', 'Pseudomonas aeruginosa', 'GEN', 81, 22), ('pae', 'Pseudomonas aeruginosa', 'TZP', 81, 10),
    ('aba', 'Acinetobacter spp.', 'CAZ', 51, 33), ('aba', 'Acinetobacter spp.', 'IPM', 51, 29),
    ('aba', 'Acinetobacter spp.', 'MEM', 51, 30), ('aba', 'Acinetobacter spp.', 'CIP', 51, 33),
    ('aba', 'Acinetobacter spp.', 'AMK', 51, 29), ('aba', 'Acinetobacter spp.', 'GEN', 51, 26),
    ('aba', 'Acinetobacter spp.', 'SXT', 51, 31)
) as v (organism_code, label, antibiotic_code, tested, resistant)
join published_source s on s.code = 'VIRAPERU-2023'
join microorganism m on m.code = v.organism_code
join antibiotic a on a.code = v.antibiotic_code;
