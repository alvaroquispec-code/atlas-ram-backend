-- Atlas RAM · V19: segunda fuente de datos REALES publicados (agregados), la más reciente: 2024.
--
-- Rodriguez-Saldaña CA, et al. Factors Associated With the Antimicrobial Resistance Profile of Uropathogenic
-- Escherichia coli in a Referral Hospital in Northern Peru. Interdiscip Perspect Infect Dis. 2026.
-- doi:10.1155/ipid/4524106 (PMC13643944). Licencia CC BY 4.0 (permite reutilizar citando).
-- Hospital III José Cayetano Heredia (EsSalud), Piura. Urocultivos del 1 de enero al 31 de diciembre de 2024,
-- un aislamiento por paciente, 350 pacientes (ambulatorio 201, emergencia 102, hospitalización 47).
--
-- Cómo se cargan los números: el artículo informa el PORCENTAJE de resistentes (sección 3.2 y resumen), no
-- el conteo por antibiótico. Se toma n = 350 (la cohorte) y resistentes = porcentaje × 350, redondeado al
-- entero; al volver a dividir se recupera el porcentaje publicado con un decimal. Los intermedios NO se
-- cuentan como resistentes (así lo hace el artículo). Imipenem ("< 1 %") no se carga porque no da un valor.

insert into published_source (code, title, citation, url, license, setting, specimen_type, period_start, period_end,
                              breakpoint_standard)
values ('PIURA-UPEC-2024',
        'E. coli urinaria en un hospital de referencia de EsSalud (Piura, 2024)',
        'Rodriguez-Saldaña CA, et al. Factors Associated With the Antimicrobial Resistance Profile of Uropathogenic Escherichia coli in a Referral Hospital in Northern Peru. Interdiscip Perspect Infect Dis. 2026',
        'https://doi.org/10.1155/ipid/4524106',
        'CC BY 4.0',
        'Hospital III José Cayetano Heredia (EsSalud), Piura. 350 pacientes, 1 aislamiento por paciente. El artículo da %; n por antibiótico = 350 y resistentes = % × 350 redondeado',
        'URINE', '2024-01-01', '2024-12-31', 'Según el artículo (intermedios no cuentan como resistentes)');

insert into published_result (source_id, microorganism_id, antibiotic_id, organism_label, tested, resistant)
select s.id, m.id, a.id, v.label, v.tested, v.resistant
from (values
    -- código WHONET, etiqueta, antibiótico, n, resistentes  (% publicado)
    ('eco', 'Escherichia coli', 'CIP', 350, 248),   -- 70,9 %
    ('eco', 'Escherichia coli', 'CRO', 350, 229),   -- 65,4 %
    ('eco', 'Escherichia coli', 'CAZ', 350, 217),   -- 62,0 %
    ('eco', 'Escherichia coli', 'SXT', 350, 162),   -- 46,3 %
    ('eco', 'Escherichia coli', 'AMK', 350, 30),    --  8,6 %
    ('eco', 'Escherichia coli', 'NIT', 350, 15),    --  4,3 %
    ('eco', 'Escherichia coli', 'MEM', 350, 8),     --  2,3 %
    ('eco', 'Escherichia coli', 'ETP', 350, 7)      --  2,0 %
) as v (organism_code, label, antibiotic_code, tested, resistant)
join published_source s on s.code = 'PIURA-UPEC-2024'
join microorganism m on m.code = v.organism_code
join antibiotic a on a.code = v.antibiotic_code;
