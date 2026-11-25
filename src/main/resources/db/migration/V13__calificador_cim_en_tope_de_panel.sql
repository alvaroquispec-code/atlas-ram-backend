-- Atlas RAM · V13: calificador "≥" en las CIM sintéticas que caen en el tope del panel.
--
-- Un panel automatizado (VITEK 2, Phoenix, MicroScan) tiene una dilución máxima: si la bacteria crece en
-- todas, informa ">=16", no "16". El generador del seed (db/seed/gen_seed.py, MIC_CAP) ya trataba ese tope
-- como ">=", pero lo guardaba sin calificador, así que el detalle del aislamiento mostraba "16".
--
-- Solo se tocan datos SINTÉTICOS sin calificador cuyo valor es exactamente el tope de su antibiótico. La
-- categoría S/I/R no cambia: en todos los casos el tope es igual o mayor que el punto de corte de resistencia.
-- Los datos reales llegan con el calificador que informa el equipo y no se modifican.

update susceptibility_result r
set mic_qualifier = 'GE'
from isolate i, antibiotic a,
     (values ('ETP', 8), ('MEM', 16), ('IPM', 16), ('CIP', 4), ('LVX', 8), ('GEN', 16), ('AMK', 64),
             ('CRO', 64), ('CAZ', 64), ('FEP', 64), ('TZP', 128), ('AMP', 32), ('SAM', 32), ('CZO', 64),
             ('OXA', 4), ('VAN', 32), ('LNZ', 8), ('CLI', 8), ('ERY', 8), ('NIT', 512), ('SXT', 16))
         as cap(code, mic)
where i.id = r.isolate_id
  and a.id = r.antibiotic_id
  and a.code = cap.code
  and i.data_origin = 'SYNTHETIC'
  and r.mic_qualifier is null
  and r.mic_value = cap.mic;
