"""
Genera db/seed/inventario_demo.sql: inventario SINTÉTICO de antibióticos para la demo del módulo logístico.

Todo es ilustrativo (consumos, costos, camas): sirve para mostrar cada situación que el tablero detecta,
no describe a ningún establecimiento real. Se carga DESPUÉS de data.sql y de la migración V9.

Situaciones que deja la demo en el Hospital Nacional Hipólito Unanue (HNHU), al 22-11-2026:
  MEM  consumo estable, reposiciones menores al consumo        -> substock, pedido sugerido
  VAN  el proveedor dejó de entregar en agosto                  -> desabastecido; el CPMA excluye los meses en 0
  CRO  consumo estable y bien abastecido                        -> normostock
  AMK  reposiciones por debajo del consumo                        -> substock; con la alerta de K. pneumoniae
                                                                   resistente a carbapenémicos, presión de brote
  LNZ  Reserve AWaRe, tratamientos esporádicos                  -> demanda intermitente (SBA), servicio 99 %
  CZO  una compra grande con vencimiento corto                    -> sobrestock y unidades en riesgo de vencer
  ETP  se dejó de usar en abril                                 -> sin rotación
  CIP  uso esporádico que se apagó en julio                     -> intermitente que se apaga (TSB)

Uso: python3 gen_inventario_demo.py > inventario_demo.sql
"""
import math
import random
from datetime import date, timedelta

random.seed(2031)
START = date(2025, 11, 1)
END = date(2026, 11, 22)          # día de corte: no se generan movimientos desde esta fecha
FACILITY = "HNHU"


def poisson(lam):
    limit, k, p = math.exp(-lam), 0, 1.0
    while True:
        p *= random.random()
        if p <= limit:
            return k
        k += 1


def days():
    d = START
    while d < END:
        yield d
        d += timedelta(days=1)


def month_start(d):
    return d.replace(day=1)


# código, presentación, g/unidad, DDD (solo valores verificados en el índice ATC/DDD de la OMS), costo, VEN, R, L
ITEMS = [
    ("MEM", "Vial 1 g", 1.0, 3.0, 25.00, "VITAL", 30, 15),
    ("VAN", "Vial 500 mg", 0.5, None, 12.00, "VITAL", 30, 15),
    ("CRO", "Vial 1 g", 1.0, 2.0, 3.50, "ESSENTIAL", 30, 15),
    ("AMK", "Ampolla 500 mg/2 mL", 0.5, 1.0, 4.00, "ESSENTIAL", 30, 15),
    ("LNZ", "Bolsa 600 mg/300 mL", 0.6, None, 90.00, "ESSENTIAL", 30, 20),
    ("CZO", "Vial 1 g", 1.0, None, 3.00, "ESSENTIAL", 30, 15),
    ("ETP", "Vial 1 g", 1.0, 1.0, 60.00, "NON_ESSENTIAL", 30, 15),
    ("CIP", "Bolsa 200 mg/100 mL", 0.2, 0.8, 5.00, "NON_ESSENTIAL", 30, 15),
]


def demand(code, d):
    if code == "MEM":
        return poisson(10)
    if code == "VAN":
        return poisson(7)
    if code == "CRO":
        return poisson(20)
    if code == "CZO":
        return poisson(12)
    if code == "AMK":
        return random.randint(4, 30) if random.random() < 0.3 else 0
    if code == "ETP":
        return poisson(1.5) if d < date(2026, 4, 1) else 0
    if code == "CIP":
        return random.randint(5, 25) if d < date(2026, 7, 1) and random.random() < 0.05 else 0
    if code == "LNZ":
        return 0  # tratamientos completos, ver LNZ_COURSES
    raise ValueError(code)


# Linezolid: tratamientos de 2 bolsas/día por 10 días que empiezan en fechas sueltas.
LNZ_COURSES = [date(2025, 11, 20), date(2026, 1, 8), date(2026, 3, 2), date(2026, 3, 20),
               date(2026, 6, 11), date(2026, 8, 25), date(2026, 10, 14)]


def lnz_demand(d):
    return sum(2 for s in LNZ_COURSES if s <= d < s + timedelta(days=10))


# Ingresos: (fecha, cantidad, días de vida útil)
def receipts(code):
    monthly = []
    m = START
    while m < END:
        monthly.append(m)
        m = (m + timedelta(days=32)).replace(day=1)
    if code == "MEM":
        return [(START, 450, 720)] + [(x + timedelta(days=4), 270, 720) for x in monthly[1:]]
    if code == "VAN":
        return [(START, 260, 720)] + [(x + timedelta(days=4), 215, 720) for x in monthly[1:] if x < date(2026, 9, 1)]
    if code == "CRO":
        return [(START, 1500, 900)] + [(x + timedelta(days=3), 610, 900) for x in monthly[1:]]
    if code == "CZO":
        return ([(START, 700, 900)] + [(x + timedelta(days=3), 370, 900) for x in monthly[1:] if x < date(2026, 5, 1)]
                + [(date(2026, 5, 6), 4600, 270)])
    if code == "AMK":
        return [(START, 300, 720)] + [(x + timedelta(days=4), 130, 720) for x in monthly[1:]]
    if code == "ETP":
        return [(START, 260, 720)]
    if code == "CIP":
        return [(START, 420, 720)]
    if code == "LNZ":
        return [(START, 60, 540), (date(2026, 3, 1), 60, 540), (date(2026, 8, 20), 40, 540)]
    raise ValueError(code)


out = []
w = out.append
w("-- GENERADO por gen_inventario_demo.py. Inventario SINTÉTICO e ilustrativo para la demo; no son datos reales.")
w("-- Requiere data.sql y la migración V9. Borra y vuelve a crear el inventario del HNHU.")
w("begin;")
w(f"delete from stock_item where facility_id = (select id from facility where code = '{FACILITY}');")
w(f"update facility set beds = 400, occupancy_rate = 0.850 where code = '{FACILITY}';  -- ilustrativo")
# Usuario farmacéutico de prueba, con la misma contraseña que los usuarios de data.sql (AtlasRam2026!).
w("insert into app_user (email, password_hash, full_name, role, active, created_at) values "
  "('farmacia.hnhu@atlasram.test', (select password_hash from app_user where email = 'admin@atlasram.test'), "
  "'Farmacia HNHU (demo)', 'PHARMACIST', true, now()) on conflict (email) do nothing;")
w(f"insert into user_facility (user_id, facility_id) select u.id, f.id from app_user u, facility f "
  f"where u.email = 'farmacia.hnhu@atlasram.test' and f.code = '{FACILITY}' on conflict do nothing;")

item_id, lot_id, mov_id = 9000, 90000, 900000
for code, pres, gpu, ddd, cost, ven, r, l in ITEMS:
    item_id += 1
    w(f"insert into stock_item (id, facility_id, antibiotic_id, presentation, grams_per_unit, ddd_grams, unit_cost, "
      f"ven_class, review_period_days, lead_time_days) values ({item_id}, (select id from facility where code = '{FACILITY}'), "
      f"(select id from antibiotic where code = '{code}'), '{pres}', {gpu}, {'null' if ddd is None else ddd}, {cost:.2f}, "
      f"'{ven}', {r}, {l});")
    pending = sorted(receipts(code))
    lots = []   # [id, numero, vence, recibido, saldo, recibido_el]
    movements = []
    for d in days():
        while pending and pending[0][0] <= d:
            rec_date, qty, life = pending.pop(0)
            lot_id += 1
            lots.append([lot_id, f"{code}-{rec_date:%y%m%d}", rec_date + timedelta(days=life), qty, qty, rec_date])
            movements.append((lot_id, "RECEIPT", qty, rec_date))
        want = lnz_demand(d) if code == "LNZ" else demand(code, d)
        for lot in sorted(lots, key=lambda x: (x[2], x[0])):
            if want == 0:
                break
            if lot[4] == 0 or lot[2] < d:
                continue
            take = min(want, lot[4])
            lot[4] -= take
            want -= take
            movements.append((lot[0], "CONSUMPTION", take, d))
        # la demanda que no se pudo atender se pierde (quiebre de stock)
    for lid, num, exp, rec, onhand, rdate in lots:
        w(f"insert into stock_lot (id, stock_item_id, lot_number, expiry_date, quantity_received, quantity_on_hand, "
          f"received_on) values ({lid}, {item_id}, '{num}', '{exp}', {rec}, {onhand}, '{rdate}');")
    rows = []
    for lid, typ, qty, d in movements:
        mov_id += 1
        rows.append(f"({mov_id}, {item_id}, {lid}, '{typ}', {qty}, '{d}')")
    for i in range(0, len(rows), 500):
        w("insert into stock_movement (id, stock_item_id, lot_id, type, quantity, movement_date) values\n  "
          + ",\n  ".join(rows[i:i + 500]) + ";")

w("select setval(pg_get_serial_sequence('stock_item', 'id'), greatest((select max(id) from stock_item), 1));")
w("select setval(pg_get_serial_sequence('stock_lot', 'id'), greatest((select max(id) from stock_lot), 1));")
w("select setval(pg_get_serial_sequence('stock_movement', 'id'), greatest((select max(id) from stock_movement), 1));")
w("commit;")
print("\n".join(out))
