#!/usr/bin/env python3
"""
Atlas RAM - generador de datos de prueba (PostgreSQL).

Datos SINTETICOS calibrados con literatura peruana (ver CALIBRACION.md).
Los nombres de establecimientos y ubigeos son reales; los resultados no.

Modelo de generacion:
  1. Cada aislamiento recibe un FENOTIPO (BLEE, EPC, MRSA, ...) cuya probabilidad
     depende del nivel del establecimiento, del servicio (UCI > hospitalizacion >
     emergencia > consulta externa), de un efecto propio del establecimiento y
     de una tendencia temporal.
  2. Cada antibiotico se decide condicionado al fenotipo, con co-resistencias
     enlazadas (CIP->LVX, CRO->CAZ->FEP, IPM->MEM, GEN->AMK, CLI->ERY).
  3. Se genera la CIM dentro de la categoria y la interpretacion se calcula
     contra el punto de corte, de modo que CIM e interpretacion siempre cuadran.

Ajustado a las entidades de atlas-ram-backend (Spring Boot 3.5.6 / Hibernate 6.6).
Uso: pip install bcrypt && python3 gen_seed.py  ->  data.sql
"""
import math
import random
import datetime as dt
from collections import defaultdict

import bcrypt

SEED = 2031
PHENOTYPE_SEED = 20339      # semilla propia de fenotipos: prevalencias cercanas al valor esperado
rng = random.Random(SEED)

START = dt.date(2024, 9, 1)
MONTHS = 24
BASE_MONTHLY = 26.0          # aislamientos/mes de un hospital de nivel III tipico
DEMO_PASSWORD = "AtlasRam2026!"
OUT_SQL = "data.sql"

# --------------------------------------------------------------------------
# Geografia: Lima Metropolitana + Callao (ubigeo INEI)
# zona y poblacion (miles, aprox.) solo se usan para asignar procedencia
# --------------------------------------------------------------------------
DISTRICTS = [
    ("150101", "Lima", "CENTRO", 268), ("150102", "Ancón", "NORTE", 62),
    ("150103", "Ate", "ESTE", 600), ("150104", "Barranco", "CENTRO", 30),
    ("150105", "Breña", "CENTRO", 85), ("150106", "Carabayllo", "NORTE", 333),
    ("150107", "Chaclacayo", "ESTE", 43), ("150108", "Chorrillos", "SUR", 314),
    ("150109", "Cieneguilla", "ESTE", 34), ("150110", "Comas", "NORTE", 524),
    ("150111", "El Agustino", "ESTE", 198), ("150112", "Independencia", "NORTE", 211),
    ("150113", "Jesús María", "CENTRO", 75), ("150114", "La Molina", "ESTE", 140),
    ("150115", "La Victoria", "CENTRO", 173), ("150116", "Lince", "CENTRO", 54),
    ("150117", "Los Olivos", "NORTE", 325), ("150118", "Lurigancho", "ESTE", 240),
    ("150119", "Lurín", "SUR", 89), ("150120", "Magdalena del Mar", "CENTRO", 60),
    ("150121", "Pueblo Libre", "CENTRO", 83), ("150122", "Miraflores", "CENTRO", 99),
    ("150123", "Pachacámac", "SUR", 110), ("150124", "Pucusana", "SUR", 17),
    ("150125", "Puente Piedra", "NORTE", 329), ("150126", "Punta Hermosa", "SUR", 15),
    ("150127", "Punta Negra", "SUR", 8), ("150128", "Rímac", "NORTE", 174),
    ("150129", "San Bartolo", "SUR", 8), ("150130", "San Borja", "CENTRO", 113),
    ("150131", "San Isidro", "CENTRO", 60), ("150132", "San Juan de Lurigancho", "ESTE", 1038),
    ("150133", "San Juan de Miraflores", "SUR", 356), ("150134", "San Luis", "CENTRO", 52),
    ("150135", "San Martín de Porres", "NORTE", 654), ("150136", "San Miguel", "CENTRO", 155),
    ("150137", "Santa Anita", "ESTE", 196), ("150138", "Santa María del Mar", "SUR", 2),
    ("150139", "Santa Rosa", "NORTE", 27), ("150140", "Santiago de Surco", "SUR", 329),
    ("150141", "Surquillo", "CENTRO", 91), ("150142", "Villa El Salvador", "SUR", 393),
    ("150143", "Villa María del Triunfo", "SUR", 398),
    ("070101", "Callao", "CALLAO", 451), ("070102", "Bellavista", "CALLAO", 75),
    ("070103", "Carmen de la Legua Reynoso", "CALLAO", 42), ("070104", "La Perla", "CALLAO", 61),
    ("070105", "La Punta", "CALLAO", 3), ("070106", "Ventanilla", "CALLAO", 316),
    ("070107", "Mi Perú", "CALLAO", 63),
]

# code, nombre, institucion, nivel, ubigeo, volumen relativo, mezcla de procedencia
# (propio distrito, misma zona, cualquier distrito)
FACILITIES = [
    ("HNCH", "Hospital Nacional Cayetano Heredia", "MINSA", "III", "150135", 1.0, (0.50, 0.35, 0.15)),
    ("HNAL", "Hospital Nacional Arzobispo Loayza", "MINSA", "III", "150101", 1.0, (0.35, 0.40, 0.25)),
    ("HNDM", "Hospital Nacional Dos de Mayo", "MINSA", "III", "150101", 0.9, (0.35, 0.40, 0.25)),
    ("HNERM", "Hospital Nacional Edgardo Rebagliati Martins", "ESSALUD", "III", "150113", 1.3, (0.15, 0.45, 0.40)),
    ("HNGAI", "Hospital Nacional Guillermo Almenara Irigoyen", "ESSALUD", "III", "150115", 1.3, (0.20, 0.40, 0.40)),
    ("HNHU", "Hospital Nacional Hipólito Unanue", "MINSA", "III", "150111", 1.0, (0.40, 0.45, 0.15)),
    ("HNSEB", "Hospital Nacional Sergio E. Bernales", "MINSA", "III", "150110", 0.9, (0.50, 0.40, 0.10)),
    ("HMA", "Hospital María Auxiliadora", "MINSA", "III", "150133", 1.0, (0.45, 0.45, 0.10)),
    ("HNDAC", "Hospital Nacional Daniel Alcides Carrión", "GOBIERNO_REGIONAL", "III", "070102", 0.9, (0.30, 0.55, 0.15)),
    ("HNASS", "Hospital Nacional Alberto Sabogal Sologuren", "ESSALUD", "III", "070102", 1.0, (0.25, 0.50, 0.25)),
    ("HEVES", "Hospital de Emergencias Villa El Salvador", "MINSA", "II", "150142", 0.45, (0.60, 0.35, 0.05)),
    ("HSJL", "Hospital San Juan de Lurigancho", "MINSA", "II", "150132", 0.50, (0.70, 0.25, 0.05)),
    ("HCLLH", "Hospital Carlos Lanfranco La Hoz", "MINSA", "II", "150125", 0.40, (0.65, 0.30, 0.05)),
    ("HVIT", "Hospital de Vitarte", "MINSA", "II", "150103", 0.45, (0.65, 0.30, 0.05)),
    ("HSR", "Hospital Santa Rosa", "MINSA", "II", "150121", 0.40, (0.40, 0.50, 0.10)),
    ("HJATCH", "Hospital José Agurto Tello de Chosica", "MINSA", "II", "150118", 0.35, (0.70, 0.25, 0.05)),
    ("HVEN", "Hospital de Ventanilla", "GOBIERNO_REGIONAL", "II", "070106", 0.40, (0.70, 0.25, 0.05)),
]

# --------------------------------------------------------------------------
# Catalogos
# --------------------------------------------------------------------------
ORGS = [  # codigo WHONET, genus, species, gram
    ("eco", "Escherichia", "coli", "NEGATIVE"),
    ("kpn", "Klebsiella", "pneumoniae", "NEGATIVE"),
    ("pae", "Pseudomonas", "aeruginosa", "NEGATIVE"),
    ("aba", "Acinetobacter", "baumannii", "NEGATIVE"),
    ("sau", "Staphylococcus", "aureus", "POSITIVE"),
    ("efa", "Enterococcus", "faecalis", "POSITIVE"),
]
# Patogenos prioritarios GLASS agregados despues. Se generan con su propia semilla para que
# los seis anteriores conserven exactamente los mismos datos.
EXTRA_ORGS = [
    ("spn", "Streptococcus", "pneumoniae", "POSITIVE"),
    ("sal", "Salmonella", "enterica", "NEGATIVE"),
]
EXTRA_CODES = {o[0] for o in EXTRA_ORGS}
EXTRA_SEED = 2031_0417

# El orden importa: el antibiotico "lider" de un enlace va antes que el seguidor
ABX = [  # codigo WHONET, nombre, clase
    ("AMP", "Ampicilina", "Penicilina"),
    ("SAM", "Ampicilina/sulbactam", "Betalactámico con inhibidor"),
    ("TZP", "Piperacilina/tazobactam", "Betalactámico con inhibidor"),
    ("OXA", "Oxacilina", "Penicilina antiestafilocócica"),
    ("CZO", "Cefazolina", "Cefalosporina de 1.ª generación"),
    ("CRO", "Ceftriaxona", "Cefalosporina de 3.ª generación"),
    ("CAZ", "Ceftazidima", "Cefalosporina de 3.ª generación"),
    ("FEP", "Cefepima", "Cefalosporina de 4.ª generación"),
    ("ETP", "Ertapenem", "Carbapenémico"),
    ("IPM", "Imipenem", "Carbapenémico"),
    ("MEM", "Meropenem", "Carbapenémico"),
    ("GEN", "Gentamicina", "Aminoglucósido"),
    ("AMK", "Amikacina", "Aminoglucósido"),
    ("CIP", "Ciprofloxacino", "Fluoroquinolona"),
    ("LVX", "Levofloxacino", "Fluoroquinolona"),
    ("SXT", "Trimetoprima/sulfametoxazol", "Inhibidor de la vía del folato"),
    ("NIT", "Nitrofurantoína", "Nitrofurano"),
    ("VAN", "Vancomicina", "Glucopéptido"),
    ("LNZ", "Linezolid", "Oxazolidinona"),
    ("CLI", "Clindamicina", "Lincosamida"),
    ("ERY", "Eritromicina", "Macrólido"),
]
ABX_ORDER = [a[0] for a in ABX]

# Puntos de corte CLSI M100 (CIM, ug/mL): (S <=, R >=). Sin categoria I: R = 2*S.
ENTERO = {"AMP": (8, 32), "SAM": (8, 32), "TZP": (8, 32), "CZO": (2, 8), "CRO": (1, 4),
          "CAZ": (4, 16), "FEP": (2, 16), "ETP": (0.5, 2), "IPM": (1, 4), "MEM": (1, 4),
          "GEN": (2, 8), "AMK": (4, 16), "CIP": (0.25, 1), "LVX": (0.5, 2), "SXT": (2, 4),
          "NIT": (32, 128)}
BREAKPOINTS_2024 = {
    "eco": dict(ENTERO),
    # K. pneumoniae: resistencia intrinseca a ampicilina; nitrofurantoina no se reporta
    "kpn": {k: v for k, v in ENTERO.items() if k not in ("AMP", "NIT")},
    # P. aeruginosa: intrinsecamente R a AMP, SAM, CZO, CRO, ETP, SXT (no se prueban)
    "pae": {"TZP": (16, 64), "CAZ": (8, 32), "FEP": (8, 32), "IPM": (2, 8), "MEM": (2, 8),
            "AMK": (16, 64), "CIP": (0.5, 2), "LVX": (1, 4)},
    "aba": {"SAM": (8, 32), "TZP": (16, 128), "CAZ": (8, 32), "FEP": (8, 32), "IPM": (2, 8),
            "MEM": (2, 8), "GEN": (4, 16), "AMK": (16, 64), "CIP": (1, 4), "LVX": (2, 8),
            "SXT": (2, 4)},
    "sau": {"OXA": (2, 4), "VAN": (2, 16), "LNZ": (4, 8), "CLI": (0.5, 4), "ERY": (0.5, 8),
            "GEN": (4, 16), "CIP": (1, 4), "LVX": (1, 4), "SXT": (2, 4)},
    "efa": {"AMP": (8, 16), "VAN": (4, 32), "LNZ": (2, 8), "NIT": (32, 128), "CIP": (1, 4),
            "LVX": (2, 8)},
    # S. pneumoniae: solo antibioticos con un unico punto de corte. Penicilina (3 umbrales segun
    # sitio) y ceftriaxona (2) no caben en la restriccion unica de Breakpoint sin un campo de sitio.
    "spn": {"CLI": (0.25, 1), "ERY": (0.25, 1), "LVX": (2, 8), "SXT": (0.5, 4)},
    # Salmonella: umbrales de fluoroquinolonas mucho mas estrictos que en otras enterobacterias.
    # CLSI desaconseja reportar aminoglucosidos y cefalosporinas de 1.a y 2.a generacion.
    "sal": {"AMP": (8, 32), "CRO": (1, 4), "CIP": (0.06, 1), "LVX": (0.12, 2), "SXT": (2, 4)},
}
# CLSI 2023 cambio los aminoglucosidos en Enterobacterales. La version 2022 permite
# demostrar la reinterpretacion del historico.
BREAKPOINTS_2022 = {org: dict(bps) for org, bps in BREAKPOINTS_2024.items()}
for org in ("eco", "kpn"):
    BREAKPOINTS_2022[org]["GEN"] = (4, 16)
    BREAKPOINTS_2022[org]["AMK"] = (16, 64)
CURRENT_VERSION, LEGACY_VERSION = "2024", "2022"

URINE_ONLY = {("eco", "NIT"), ("efa", "NIT"), ("efa", "CIP"), ("efa", "LVX")}
# CLSI: en Salmonella de heces no se reportan cefalosporinas de 3.a generacion
EXTRAINTESTINAL_ONLY = {("sal", "CRO")}
NOT_IN_LEVEL_II_PANEL = {"ETP", "LNZ"}
ALWAYS_TESTED = {"CRO", "IPM", "MEM", "ETP", "OXA", "VAN"}

# Moda de CIM de la poblacion salvaje
WT_MODE = {
    "eco": {"AMP": 2, "SAM": 4, "TZP": 2, "CZO": 2, "CRO": 0.06, "CAZ": 0.25, "FEP": 0.06, "ETP": 0.03,
            "IPM": 0.25, "MEM": 0.03, "GEN": 0.5, "AMK": 2, "CIP": 0.03, "LVX": 0.06, "SXT": 0.25, "NIT": 16},
    "kpn": {"SAM": 4, "TZP": 2, "CZO": 2, "CRO": 0.06, "CAZ": 0.25, "FEP": 0.06, "ETP": 0.03, "IPM": 0.25,
            "MEM": 0.03, "GEN": 0.25, "AMK": 1, "CIP": 0.06, "LVX": 0.06, "SXT": 0.25},
    "pae": {"TZP": 4, "CAZ": 2, "FEP": 2, "IPM": 2, "MEM": 0.5, "AMK": 4, "CIP": 0.12, "LVX": 0.5},
    "aba": {"SAM": 4, "TZP": 16, "CAZ": 4, "FEP": 4, "IPM": 0.25, "MEM": 0.5, "GEN": 1, "AMK": 4,
            "CIP": 0.25, "LVX": 0.12, "SXT": 0.5},
    "sau": {"OXA": 0.25, "VAN": 1, "LNZ": 2, "CLI": 0.12, "ERY": 0.25, "GEN": 0.5, "CIP": 0.25,
            "LVX": 0.12, "SXT": 0.25},
    "efa": {"AMP": 1, "VAN": 2, "LNZ": 2, "NIT": 8, "CIP": 1, "LVX": 1},
    "spn": {"CLI": 0.06, "ERY": 0.03, "LVX": 1, "SXT": 0.25},
    "sal": {"AMP": 2, "CRO": 0.06, "CIP": 0.03, "LVX": 0.03, "SXT": 0.25},
}
# Limite superior de los paneles automatizados (el valor tope equivale a ">=")
MIC_CAP = {"ETP": 8, "MEM": 16, "IPM": 16, "CIP": 4, "LVX": 8, "GEN": 16, "AMK": 64, "CRO": 64,
           "CAZ": 64, "FEP": 64, "TZP": 128, "AMP": 32, "SAM": 32, "CZO": 64, "OXA": 4, "VAN": 32,
           "LNZ": 8, "CLI": 8, "ERY": 8, "NIT": 512, "SXT": 16}
DILUTIONS = [0.03, 0.06, 0.12, 0.25, 0.5, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512]

# --------------------------------------------------------------------------
# Epidemiologia
# --------------------------------------------------------------------------
# Prevalencia marginal del fenotipo al inicio y cambio absoluto por anio
PREVALENCE = {
    ("eco", "ESBL"): {"III": (0.39, 0.012), "II": (0.30, 0.012)},
    ("eco", "CRE"): {"III": (0.006, 0.0), "II": (0.002, 0.0)},
    ("kpn", "ESBL"): {"III": (0.55, 0.015), "II": (0.40, 0.015)},
    ("kpn", "CRE"): {"III": (0.09, 0.050), "II": (0.035, 0.015)},
    ("pae", "CR"): {"III": (0.40, 0.015), "II": (0.22, 0.010)},
    ("aba", "CR"): {"III": (0.85, 0.010), "II": (0.65, 0.010)},
    ("sau", "MRSA"): {"III": (0.60, 0.0), "II": (0.45, 0.0)},
    ("efa", "VRE"): {"III": (0.015, 0.003), "II": (0.003, 0.0)},
    # neumococo resistente a macrolidos: 26% en portadores; algo mayor en aislamientos clinicos
    ("spn", "MACRO"): {"III": (0.30, 0.0), "II": (0.28, 0.0)},
    # Salmonella con sensibilidad disminuida a ciprofloxacino (incluye las BLEE tipo Infantis)
    ("sal", "DSC"): {"III": (0.64, 0.015), "II": (0.62, 0.015)},
    # S. Infantis con megaplasmido pESI y CTX-M-65, en expansion
    ("sal", "ESBL"): {"III": (0.25, 0.020), "II": (0.22, 0.020)},
}

# P(R) por antibiotico condicionado al fenotipo
PROFILE = {
    "eco": {
        "WT": {"AMP": .65, "SAM": .30, "TZP": .03, "CZO": .25, "CRO": .03, "CAZ": .02, "FEP": .02, "ETP": 0,
               "IPM": 0, "MEM": 0, "GEN": .12, "AMK": .01, "CIP": .40, "LVX": .36, "SXT": .45, "NIT": .06},
        "ESBL": {"AMP": 1, "SAM": .75, "TZP": .15, "CZO": 1, "CRO": 1, "CAZ": .80, "FEP": .75, "ETP": .01,
                 "IPM": .003, "MEM": .003, "GEN": .45, "AMK": .06, "CIP": .85, "LVX": .82, "SXT": .70, "NIT": .15},
        "CRE": {"AMP": 1, "SAM": 1, "TZP": 1, "CZO": 1, "CRO": 1, "CAZ": .97, "FEP": .95, "ETP": 1, "IPM": .92,
                "MEM": .95, "GEN": .80, "AMK": .20, "CIP": .95, "LVX": .80, "SXT": .85, "NIT": .30},
    },
    "kpn": {
        "WT": {"SAM": .15, "TZP": .05, "CZO": .15, "CRO": .02, "CAZ": .02, "FEP": .01, "ETP": 0, "IPM": 0,
               "MEM": 0, "GEN": .05, "AMK": .01, "CIP": .10, "LVX": .08, "SXT": .15},
        "ESBL": {"SAM": .85, "TZP": .35, "CZO": 1, "CRO": 1, "CAZ": .85, "FEP": .80, "ETP": .04, "IPM": .005,
                 "MEM": .005, "GEN": .55, "AMK": .10, "CIP": .70, "LVX": .60, "SXT": .75},
        # perfil tipo NDM-1 descrito en Lima: R a casi todo, levofloxacino y amikacina conservados
        "CRE": {"SAM": 1, "TZP": 1, "CZO": 1, "CRO": 1, "CAZ": 1, "FEP": 1, "ETP": 1, "IPM": .95, "MEM": .97,
                "GEN": .90, "AMK": .25, "CIP": .97, "LVX": .45, "SXT": .95},
    },
    "pae": {
        "WT": {"TZP": .12, "CAZ": .12, "FEP": .10, "IPM": .02, "MEM": .01, "AMK": .04, "CIP": .18, "LVX": .22},
        "CR": {"TZP": .60, "CAZ": .55, "FEP": .50, "IPM": .93, "MEM": .88, "AMK": .30, "CIP": .60, "LVX": .65},
    },
    "aba": {
        "WT": {"SAM": .10, "TZP": .25, "CAZ": .25, "FEP": .20, "IPM": .01, "MEM": .01, "GEN": .20, "AMK": .10,
               "CIP": .30, "LVX": .20, "SXT": .30},
        "CR": {"SAM": .70, "TZP": .97, "CAZ": .92, "FEP": .88, "IPM": .98, "MEM": .97, "GEN": .75, "AMK": .60,
               "CIP": .97, "LVX": .85, "SXT": .75},
    },
    "sau": {
        "WT": {"OXA": 0, "VAN": 0, "LNZ": 0, "CLI": .15, "ERY": .25, "GEN": .05, "CIP": .10, "LVX": .09, "SXT": .05},
        "MRSA": {"OXA": 1, "VAN": 0, "LNZ": .002, "CLI": .80, "ERY": .88, "GEN": .55, "CIP": .85, "LVX": .82,
                 "SXT": .08},
    },
    "efa": {
        "WT": {"AMP": .02, "VAN": 0, "LNZ": .01, "NIT": .03, "CIP": .35, "LVX": .33},
        "VRE": {"AMP": .10, "VAN": 1, "LNZ": .03, "NIT": .10, "CIP": .80, "LVX": .78},
    },
    "spn": {
        "WT": {"CLI": 0, "ERY": 0, "LVX": .01, "SXT": .50},
        # erm(B) da resistencia a clindamicina; mef(E) solo a macrolidos
        "MACRO": {"CLI": .55, "ERY": 1, "LVX": .02, "SXT": .75},
    },
    "sal": {
        "WT": {"AMP": .12, "CRO": 0, "CIP": 0, "LVX": 0, "SXT": .08},
        "DSC": {"AMP": .30, "CRO": .01, "CIP": .12, "LVX": .05, "SXT": .20},
        "ESBL": {"AMP": 1, "CRO": 1, "CIP": .22, "LVX": .10, "SXT": .95},
    },
}
CARBAPENEM_PHENOTYPES = {"CRE", "CR"}
# seguidor: (lider, P(seguidor R | lider R))
LINKS = {"LVX": ("CIP", 1.0), "CAZ": ("CRO", .90), "FEP": ("CAZ", .85), "MEM": ("IPM", .90),
         "AMK": ("GEN", .90), "ERY": ("CLI", .95)}
# fraccion de no-R que cae en intermedio
I_RATE = {("sau", "VAN"): 0, ("sau", "LNZ"): 0, ("efa", "LNZ"): .005}
DEFAULT_I_RATE = .03
# En neumococo, la resistencia a clindamicina sin resistencia a eritromicina practicamente no existe
LINK_OVERRIDES = {("spn", "ERY"): 1.0}
# fraccion de no-R en intermedio que depende del fenotipo (mutaciones en gyrA de Salmonella)
I_RATE_PHENOTYPE = {("sal", "CIP", "DSC"): 1.0, ("sal", "CIP", "ESBL"): 1.0,
                    ("sal", "LVX", "DSC"): .85, ("sal", "LVX", "ESBL"): .85,
                    ("sal", "CIP", "WT"): .02, ("spn", "SXT", "WT"): .15, ("spn", "SXT", "MACRO"): .15,
                    ("spn", "LVX", "WT"): .005, ("spn", "LVX", "MACRO"): .005}

SPECIMEN_MIX = {"III": {"URINE": 40, "BLOOD": 20, "RESPIRATORY": 20, "WOUND": 20},
                "II": {"URINE": 60, "BLOOD": 12, "RESPIRATORY": 10, "WOUND": 18}}
WARD_MIX = {"URINE": {"OUTPATIENT": 40, "EMERGENCY": 30, "INPATIENT": 25, "ICU": 5},
            "BLOOD": {"OUTPATIENT": 0, "EMERGENCY": 35, "INPATIENT": 40, "ICU": 25},
            "RESPIRATORY": {"OUTPATIENT": 0, "EMERGENCY": 15, "INPATIENT": 35, "ICU": 50},
            "WOUND": {"OUTPATIENT": 25, "EMERGENCY": 25, "INPATIENT": 45, "ICU": 5}}
ORG_MIX = {"URINE": {"eco": 70, "kpn": 14, "efa": 8, "pae": 6, "aba": 1, "sau": 1},
           "BLOOD": {"eco": 25, "kpn": 22, "sau": 22, "aba": 12, "pae": 10, "efa": 9},
           "RESPIRATORY": {"pae": 28, "aba": 26, "kpn": 24, "sau": 17, "eco": 5, "efa": 0},
           "WOUND": {"sau": 35, "eco": 18, "pae": 18, "kpn": 12, "aba": 10, "efa": 7}}
WARD_LOGIT = {"OUTPATIENT": -0.8, "EMERGENCY": -0.25, "INPATIENT": 0.2, "ICU": 0.9}
GRAM_NEG_SUMMER = 1.15       # enero-marzo: mas bacilos gramnegativos

# Brote ficticio de K. pneumoniae productora de carbapenemasa (configurable)
OUTBREAK = {"facility": "HNHU", "org": "kpn", "phenotype": "CRE", "n": 32,
            "start": dt.date(2026, 4, 6), "end": dt.date(2026, 6, 26)}

# Laboratorios de nivel II que siguieron reportando aminoglucosidos con CLSI 2022
LEGACY_REPORTING_UNTIL = dt.date(2025, 7, 1)
LIMA_TZ = dt.timezone(dt.timedelta(hours=-5))


# --------------------------------------------------------------------------
# Utilidades
# --------------------------------------------------------------------------
def logit(p):
    p = min(max(p, 1e-6), 1 - 1e-6)
    return math.log(p / (1 - p))


def sigmoid(x):
    return 1 / (1 + math.exp(-x))


def weighted(options):
    keys = [k for k, w in options.items() if w > 0]
    return rng.choices(keys, weights=[options[k] for k in keys])[0]


def r_weighted(r, options):
    keys = [k for k, w in options.items() if w > 0]
    return r.choices(keys, weights=[options[k] for k in keys])[0]


def poisson_with(r, lam):
    limit, k, p = math.exp(-lam), 0, 1.0
    while True:
        p *= r.random()
        if p <= limit:
            return k
        k += 1


def poisson(lam):
    limit, k, p = math.exp(-lam), 0, 1.0
    while True:
        p *= rng.random()
        if p <= limit:
            return k
        k += 1


def month_start(offset):
    y, m = divmod(START.month - 1 + offset, 12)
    return dt.date(START.year + y, m + 1, 1)


def years_since_start(d):
    return (d - START).days / 365.25


def interpret(mic, bp):
    s_max, r_min = bp
    return "S" if mic <= s_max else "R" if mic >= r_min else "I"


# --------------------------------------------------------------------------
# Paso 1: estructura (muestras y aislamientos sin resultados)
# --------------------------------------------------------------------------
district_by_ubigeo = {d[0]: d for d in DISTRICTS}
districts_by_zone = defaultdict(list)
for d in DISTRICTS:
    districts_by_zone[d[2]].append(d)

facility_effect = {f[0]: rng.gauss(0, 0.25) for f in FACILITIES}
facility_org_effect = {(f[0], o[0]): rng.gauss(0, 0.15) for f in FACILITIES for o in ORGS}


def residence(facility, r=None):
    r = r or rng
    own, zone, anywhere = facility[6]
    home = district_by_ubigeo[facility[4]]
    u = r.random()
    if u < own:
        return home[0]
    pool = districts_by_zone[home[2]] if u < own + zone else DISTRICTS
    return r.choices(pool, weights=[d[3] for d in pool])[0][0]


def patient(specimen, ward):
    if rng.random() < 0.06:
        age = rng.randint(0, 14)
    else:
        mode = 40 if (specimen == "URINE" and ward == "OUTPATIENT") else 63 if ward == "ICU" else 55
        age = int(rng.triangular(15, 95, mode))
    female = rng.random() < (0.78 if specimen == "URINE" else 0.45)
    return age, "FEMALE" if female else "MALE"


def pick_org(specimen, month, exclude=None):
    mix = dict(ORG_MIX[specimen])
    if month in (1, 2, 3):
        for org in ("eco", "kpn", "pae", "aba"):
            mix[org] *= GRAM_NEG_SUMMER
    if exclude:
        mix.pop(exclude, None)
    return weighted(mix)


samples, isolates = [], []


def new_sample(facility, day, specimen, ward):
    age, sex = patient(specimen, ward)
    sample = {"id": len(samples) + 1, "facility": facility[0], "date": day, "specimen": specimen,
              "ward": ward, "age": age, "sex": sex}
    samples.append(sample)
    return sample


def new_isolate(sample, facility, org, forced=None):
    isolates.append({"id": len(isolates) + 1, "sample": sample, "org": org, "facility": facility[0],
                     "level": facility[3], "district": residence(facility), "forced": forced})


for fac in FACILITIES:
    for offset in range(MONTHS):
        first = month_start(offset)
        days_in_month = (month_start(offset + 1) - first).days
        for _ in range(poisson(BASE_MONTHLY * fac[5])):
            specimen = weighted(SPECIMEN_MIX[fac[3]])
            wards = dict(WARD_MIX[specimen])
            if fac[3] == "II":
                wards["ICU"] *= 0.4
            ward = weighted(wards)
            day = first + dt.timedelta(days=rng.randrange(days_in_month))
            sample = new_sample(fac, day, specimen, ward)
            org = pick_org(specimen, day.month)
            new_isolate(sample, fac, org)
            if specimen in ("WOUND", "RESPIRATORY") and rng.random() < 0.08:
                new_isolate(sample, fac, pick_org(specimen, day.month, exclude=org))

outbreak_fac = next(f for f in FACILITIES if f[0] == OUTBREAK["facility"])
span = (OUTBREAK["end"] - OUTBREAK["start"]).days
for _ in range(OUTBREAK["n"]):
    day = OUTBREAK["start"] + dt.timedelta(days=rng.randrange(span))
    specimen = rng.choice(["BLOOD", "RESPIRATORY", "RESPIRATORY"])
    ward = weighted({"ICU": 70, "INPATIENT": 30})
    new_isolate(new_sample(outbreak_fac, day, specimen, ward), outbreak_fac, OUTBREAK["org"],
                forced=OUTBREAK["phenotype"])

rng_extra = random.Random(EXTRA_SEED)
for f in FACILITIES:
    for org in sorted(EXTRA_CODES):
        facility_org_effect[(f[0], org)] = rng_extra.gauss(0, 0.15)

EXTRA_SPECIMENS = {"spn": {"RESPIRATORY": 55, "BLOOD": 35, "CSF": 10},
                   "sal": {"STOOL": 60, "BLOOD": 35, "URINE": 5}}
EXTRA_WARDS = {"RESPIRATORY": {"OUTPATIENT": 10, "EMERGENCY": 40, "INPATIENT": 35, "ICU": 15},
               "BLOOD": {"EMERGENCY": 40, "INPATIENT": 45, "ICU": 15},
               "CSF": {"INPATIENT": 50, "ICU": 50},
               "STOOL": {"OUTPATIENT": 35, "EMERGENCY": 45, "INPATIENT": 20},
               "URINE": {"OUTPATIENT": 40, "EMERGENCY": 40, "INPATIENT": 20}}
EXTRA_MONTHLY = {"spn": 0.9, "sal": 0.8}


def extra_season(org, month):
    if org == "spn":        # invierno limeño
        return 1.5 if month in (6, 7, 8) else 0.8 if month in (12, 1, 2, 3) else 1.0
    return 1.6 if month in (1, 2, 3) else 0.8 if month in (6, 7, 8) else 1.0   # verano: diarreas


def extra_patient(r):
    u = r.random()
    age = r.randint(0, 4) if u < 0.30 else r.randint(5, 14) if u < 0.42 else int(r.triangular(15, 95, 55))
    return age, "FEMALE" if r.random() < 0.5 else "MALE"


for fac in FACILITIES:
    for offset in range(MONTHS):
        first = month_start(offset)
        days_in_month = (month_start(offset + 1) - first).days
        for org in sorted(EXTRA_CODES):
            lam = EXTRA_MONTHLY[org] * fac[5] * extra_season(org, first.month)
            for _ in range(poisson_with(rng_extra, lam)):
                specimen = r_weighted(rng_extra, EXTRA_SPECIMENS[org])
                ward = r_weighted(rng_extra, EXTRA_WARDS[specimen])
                day = first + dt.timedelta(days=rng_extra.randrange(days_in_month))
                age, sex = extra_patient(rng_extra)
                sample = {"id": len(samples) + 1, "facility": fac[0], "date": day, "specimen": specimen,
                          "ward": ward, "age": age, "sex": sex}
                samples.append(sample)
                isolates.append({"id": len(isolates) + 1, "sample": sample, "org": org, "facility": fac[0],
                                 "level": fac[3], "district": residence(fac, rng_extra), "forced": None,
                                 "extra": True})

samples.sort(key=lambda s: (s["date"], s["facility"]))
for i, s in enumerate(samples, 1):
    s["id"] = i
isolates.sort(key=lambda x: (x["sample"]["id"], x["org"]))
for i, iso in enumerate(isolates, 1):
    iso["id"] = i


# --------------------------------------------------------------------------
# Paso 2: fenotipos calibrados
# --------------------------------------------------------------------------
def offset_of(iso):
    ward = 0 if iso["org"] in EXTRA_CODES else WARD_LOGIT[iso["sample"]["ward"]]
    return (ward + facility_effect[iso["facility"]]
            + facility_org_effect[(iso["facility"], iso["org"])])


def target_p(key, level, day):
    base, slope = PREVALENCE[key][level]
    return base + slope * years_since_start(day)


calibration = {}
for key in PREVALENCE:
    for level in ("III", "II"):
        group = [i for i in isolates if i["org"] == key[0] and i["level"] == level and not i["forced"]]
        targets = [target_p(key, level, i["sample"]["date"]) for i in group]
        offsets = [offset_of(i) for i in group]
        goal = sum(targets) / len(targets)
        lo, hi = -4.0, 4.0
        for _ in range(40):
            mid = (lo + hi) / 2
            mean = sum(sigmoid(logit(t) + o + mid) for t, o in zip(targets, offsets)) / len(group)
            lo, hi = (mid, hi) if mean < goal else (lo, mid)
        calibration[(key, level)] = (lo + hi) / 2


def phenotype_prob(key, iso):
    p = target_p(key, iso["level"], iso["sample"]["date"])
    return sigmoid(logit(p) + offset_of(iso) + calibration[(key, iso["level"])])


def assign_phenotype(iso):
    if iso["forced"]:
        return iso["forced"]
    org = iso["org"]
    if org == "sal":
        p_esbl = phenotype_prob((org, "ESBL"), iso)
        p_dsc = phenotype_prob((org, "DSC"), iso)
        u = rng.random()
        return "ESBL" if u < p_esbl else "DSC" if u < max(p_dsc, p_esbl) else "WT"
    if org in ("eco", "kpn"):
        p_cre = phenotype_prob((org, "CRE"), iso)
        p_esbl = phenotype_prob((org, "ESBL"), iso)
        u = rng.random()
        return "CRE" if u < p_cre else "ESBL" if u < max(p_esbl, p_cre) else "WT"
    key = next(k for k in PREVALENCE if k[0] == org)
    return key[1] if rng.random() < phenotype_prob(key, iso) else "WT"


# --------------------------------------------------------------------------
# Paso 3: resultados de susceptibilidad
# --------------------------------------------------------------------------
def draw_mic(org, abx, category, bp, mechanism):
    s_max, r_min = bp
    cap = max(MIC_CAP[abx], r_min)
    if category == "R":
        k = rng.choices([0, 1, 2, 3], weights=[35, 30, 20, 15])[0]
        return min(r_min * 2 ** k, cap)
    if category == "I":
        return rng.choice([d for d in DILUTIONS if s_max < d < r_min])
    mode_idx = DILUTIONS.index(WT_MODE[org][abx]) + (2 if mechanism else 0)
    idx = mode_idx + rng.choices([0, -1, 1, -2, 2], weights=[50, 15, 15, 10, 10])[0]
    idx = max(0, idx)
    while DILUTIONS[idx] > s_max:
        idx -= 1
    return DILUTIONS[idx]


def results_for(iso, phenotype):
    org, sample = iso["org"], iso["sample"]
    bps = BREAKPOINTS_2024[org]
    profile = PROFILE[org][phenotype]
    status = {}
    out = []
    for abx in ABX_ORDER:
        if abx not in bps:
            continue
        if (org, abx) in URINE_ONLY and sample["specimen"] != "URINE":
            continue
        if (org, abx) in EXTRAINTESTINAL_ONLY and sample["specimen"] == "STOOL":
            continue
        if iso["level"] == "II" and abx in NOT_IN_LEVEL_II_PANEL:
            continue
        if abx not in ALWAYS_TESTED and rng.random() < 0.04:
            continue
        p_r = profile[abx]
        if abx in LINKS and LINKS[abx][0] in status:
            leader = LINKS[abx][0]
            p_leader = profile[leader]
            c = min(LINK_OVERRIDES.get((org, abx), LINKS[abx][1]), p_r / p_leader) if p_leader > 0 else 0
            if status[leader] == "R":
                p_r = c
            else:
                p_r = max(0.0, (p_r - p_leader * c) / (1 - p_leader)) if p_leader < 1 else 0.0
        s_max, r_min = bps[abx]
        has_i = any(s_max < d < r_min for d in DILUTIONS)
        i_rate = I_RATE_PHENOTYPE.get((org, abx, phenotype), I_RATE.get((org, abx), DEFAULT_I_RATE))
        p_i = (1 - p_r) * i_rate if has_i else 0
        u = rng.random()
        category = "R" if u < p_r else "I" if u < p_r + p_i else "S"
        status[abx] = category
        out.append([abx, category])

    if phenotype in CARBAPENEM_PHENOTYPES and not any(
            c == "R" for a, c in out if a in ("ETP", "IPM", "MEM")):
        for row in out:
            if row[0] == "MEM":
                row[1] = "R"

    rows = []
    for abx, category in out:
        mechanism = phenotype != "WT"
        mic = draw_mic(org, abx, category, bps[abx], mechanism)
        current = interpret(mic, bps[abx])
        reported = current
        if (iso["level"] == "II" and sample["date"] < LEGACY_REPORTING_UNTIL
                and org in ("eco", "kpn") and abx in ("GEN", "AMK")):
            reported = interpret(mic, BREAKPOINTS_2022[org][abx])
        rows.append((abx, mic, current, reported))
    return rows


# --------------------------------------------------------------------------
# Usuarios, panel, cargas y escritura SQL
# --------------------------------------------------------------------------
INTERPRETATION_ENUM = {"S": "SUSCEPTIBLE", "I": "INTERMEDIATE", "R": "RESISTANT"}
ROW_ERRORS = [
    "microorganismo 'Klebsiella sp.' no reconocido",
    "microorganismo 'Staphylococcus coagulasa negativo' no reconocido",
    "CIM '>=16' no numérica",
    "fecha de toma posterior a la fecha de carga",
    "aislamiento duplicado en el mismo archivo",
    "antibiótico 'CTX' no incluido en el panel del establecimiento",
]
FAILED_UPLOAD_ERRORS = [
    "Encabezados no reconocidos: falta la columna 'microorganismo'",
    "El archivo no tiene formato CSV válido (codificación no soportada)",
    "Archivo vacío",
]

facility_ids = {f[0]: i for i, f in enumerate(FACILITIES, 1)}
district_ids = {d[0]: i for i, d in enumerate(DISTRICTS, 1)}
org_ids = {o[0]: i for i, o in enumerate(ORGS + EXTRA_ORGS, 1)}
abx_ids = {a[0]: i for i, a in enumerate(ABX, 1)}


def sql_value(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "TRUE" if v else "FALSE"
    if isinstance(v, (int, float)):
        return format(v, "g")
    if isinstance(v, dt.datetime):
        return f"'{v.isoformat(sep=' ')}'"
    if isinstance(v, dt.date):
        return f"'{v.isoformat()}'"
    return "'" + str(v).replace("'", "''") + "'"


def write_insert(fh, table, columns, rows, chunk=1000):
    for i in range(0, len(rows), chunk):
        fh.write(f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n")
        fh.write(",\n".join("(" + ", ".join(sql_value(v) for v in r) + ")" for r in rows[i:i + chunk]))
        fh.write(";\n\n")


def lima_time(day, hour, minute):
    return dt.datetime(day.year, day.month, day.day, hour, minute, tzinfo=LIMA_TZ)


def build_users():
    password_hash = bcrypt.hashpw(DEMO_PASSWORD.encode(), bcrypt.gensalt(rounds=10)).decode()
    first = ["Lucía", "Carlos", "María", "Jorge", "Rosa", "Luis", "Ana", "Miguel", "Carmen", "José",
             "Patricia", "Ricardo", "Elena", "Víctor", "Silvia", "Raúl", "Gabriela", "Óscar", "Diana",
             "Fernando", "Milagros", "César", "Pilar", "Hugo", "Teresa"]
    last = ["Quispe", "Flores", "Rojas", "Huamán", "Mendoza", "Chávez", "Vargas", "Ramos", "Castillo",
            "Torres", "Mamani", "Salazar", "Paredes", "Cárdenas", "Espinoza", "Gutiérrez", "Rivera",
            "Villanueva", "Palacios", "Condori", "Aguilar", "Medina", "Soto", "Ponce", "Núñez"]
    created = dt.datetime(2024, 8, 20, 9, 0, tzinfo=LIMA_TZ)
    users, links = [], []

    def add(name, email, role, facilities):
        uid = len(users) + 1
        users.append((uid, email, password_hash, name, role, True, created))
        links.extend((uid, facility_ids[code]) for code in facilities)
        return uid

    add("Administrador Atlas RAM", "admin@atlasram.test", "ADMIN", [])
    add(f"{first[0]} {last[0]}", "epidemiologia.lima@atlasram.test", "EPIDEMIOLOGIST", [])
    add(f"{first[1]} {last[1]}", "epidemiologia.callao@atlasram.test", "EPIDEMIOLOGIST", [])
    second_lab = {"HNCH": "HSR", "HNHU": "HVIT", "HNDAC": "HVEN"}
    lab_users = {}
    for i, fac in enumerate(FACILITIES):
        facs = [fac[0]] + ([second_lab[fac[0]]] if fac[0] in second_lab else [])
        name = f"{first[(i + 2) % 25]} {last[(i * 7 + 3) % 25]}"
        lab_users[fac[0]] = add(name, f"lab.{fac[0].lower()}@atlasram.test", "LAB_TECHNICIAN", facs)
    for primary, extra in second_lab.items():
        lab_users[extra] = lab_users[primary]
    for i, facs in enumerate([["HNCH"], ["HNERM"], ["HNGAI", "HNAL"], ["HMA"], ["HSJL"], ["HNDAC", "HNASS"]]):
        name = f"{first[(i + 19) % 25]} {last[(i * 11 + 5) % 25]}"
        add(name, f"medico{i + 1}@atlasram.test", "PUBLIC_VIEWER", facs)
    return users, links, lab_users


def build_uploads(lab_users):
    rows, ids = [], {}
    by_month = defaultdict(int)
    for iso in isolates:
        d = iso["sample"]["date"]
        by_month[(iso["facility"], d.year, d.month)] += 1
    keys = sorted(by_month, key=lambda k: (k[1], k[2], k[0]))
    failed_slots = set(rng.sample(range(len(keys)), len(FAILED_UPLOAD_ERRORS)))
    for n, key in enumerate(keys):
        fac, year, month = key
        first_next = dt.date(year + (month == 12), month % 12 + 1, 1)
        base_day = first_next + dt.timedelta(days=rng.randint(1, 6))
        name = f"{fac.lower()}_{year}{month:02d}.csv"
        if n in failed_slots:
            started = lima_time(base_day - dt.timedelta(days=1), rng.randint(8, 12), rng.randint(0, 59))
            rows.append([len(rows) + 1, name, "FAILED", 0, 0, 0, FAILED_UPLOAD_ERRORS.pop(),
                         started, started + dt.timedelta(seconds=rng.randint(1, 4)),
                         facility_ids[fac], lab_users[fac]])
        failed = rng.choices([0, 1, 2, 3, 5], weights=[50, 20, 15, 10, 5])[0]
        valid = by_month[key]
        started = lima_time(base_day, rng.randint(13, 18), rng.randint(0, 59))
        log = "\n".join(f"Fila {r}: {rng.choice(ROW_ERRORS)}"
                        for r in sorted(rng.sample(range(2, valid + failed + 2), failed))) or None
        rows.append([len(rows) + 1, name, "COMPLETED", valid + failed, valid, failed, log,
                     started, started + dt.timedelta(seconds=rng.randint(4, 40)),
                     facility_ids[fac], lab_users[fac]])
        ids[key] = rows[-1][0]
    return rows, ids


def main():
    users, user_links, lab_users = build_users()
    upload_rows, upload_ids = build_uploads(lab_users)

    panel_rows = []
    for fac in FACILITIES:
        for abx in ABX_ORDER:
            if not (fac[3] == "II" and abx in NOT_IN_LEVEL_II_PANEL):
                panel_rows.append((facility_ids[fac[0]], abx_ids[abx]))

    isolate_cols = ["id", "collection_date", "specimen_type", "patient_age", "patient_sex",
                    "facility_id", "district_id", "microorganism_id", "data_upload_id"]
    isolate_rows, result_rows = [], []
    phenotype_count = defaultdict(int)
    originals = [iso for iso in isolates if not iso.get("extra")]
    extras = [iso for iso in isolates if iso.get("extra")]
    rng.seed(PHENOTYPE_SEED)
    phenotypes = {iso["id"]: assign_phenotype(iso) for iso in originals}
    rng.seed(PHENOTYPE_SEED + 2)
    phenotypes.update({iso["id"]: assign_phenotype(iso) for iso in extras})
    results = {}
    rng.seed(PHENOTYPE_SEED + 1)
    for iso in originals:
        results[iso["id"]] = results_for(iso, phenotypes[iso["id"]])
    rng.seed(PHENOTYPE_SEED + 3)
    for iso in extras:
        results[iso["id"]] = results_for(iso, phenotypes[iso["id"]])
    for iso in isolates:
        s = iso["sample"]
        d = s["date"]
        isolate_rows.append([iso["id"], d, s["specimen"], s["age"], s["sex"][0], facility_ids[iso["facility"]],
                             district_ids[iso["district"]], org_ids[iso["org"]],
                             upload_ids[(iso["facility"], d.year, d.month)]])
        phenotype = phenotypes[iso["id"]]
        phenotype_count[(iso["org"], phenotype)] += 1
        for abx, mic, current, reported in results[iso["id"]]:
            result_rows.append((len(result_rows) + 1, iso["id"], abx_ids[abx], mic, None,
                                INTERPRETATION_ENUM[current], INTERPRETATION_ENUM[reported],
                                "CLSI", CURRENT_VERSION))

    breakpoint_rows = []
    for version, table in ((LEGACY_VERSION, BREAKPOINTS_2022), (CURRENT_VERSION, BREAKPOINTS_2024)):
        for org, bps in table.items():
            for abx in ABX_ORDER:
                if abx in bps:
                    breakpoint_rows.append((len(breakpoint_rows) + 1, org_ids[org], abx_ids[abx], "CLSI",
                                            version, bps[abx][0], bps[abx][1]))

    with open(OUT_SQL, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("-- Atlas RAM: datos de prueba SINTETICOS calibrados con literatura peruana.\n"
                 "-- No representan la situacion real de ningun establecimiento. El brote es ficticio.\n"
                 f"-- Generado con gen_seed.py (semilla {SEED}). Contrasena de todos los usuarios: {DEMO_PASSWORD}\n"
                 "-- Cargar una sola vez sobre las tablas creadas por Hibernate.\n\n"
                 "SET client_encoding = 'UTF8';\nBEGIN;\n\n")
        write_insert(fh, "district", ["id", "ubigeo", "name", "province", "department"],
                     [(district_ids[d[0]], d[0], d[1], "Lima" if d[0].startswith("15") else "Callao",
                       "Lima" if d[0].startswith("15") else "Callao") for d in DISTRICTS])
        write_insert(fh, "facility", ["id", "code", "name", "district_id"],
                     [(facility_ids[f[0]], f[0], f[1], district_ids[f[4]]) for f in FACILITIES])
        write_insert(fh, "app_user", ["id", "email", "password_hash", "full_name", "role", "active", "created_at"],
                     users)
        write_insert(fh, "user_facility", ["user_id", "facility_id"], user_links)
        write_insert(fh, "microorganism", ["id", "code", "genus", "species", "gram_stain"],
                     [(org_ids[o[0]],) + o for o in ORGS + EXTRA_ORGS])
        write_insert(fh, "antibiotic", ["id", "code", "name", "antibiotic_class"],
                     [(abx_ids[a[0]],) + a for a in ABX])
        write_insert(fh, "facility_antibiotic_panel", ["facility_id", "antibiotic_id"], panel_rows)
        write_insert(fh, "breakpoint", ["id", "microorganism_id", "antibiotic_id", "standard", "version",
                                        "susceptible_max", "resistant_min"], breakpoint_rows)
        write_insert(fh, "data_upload", ["id", "file_name", "status", "total_rows", "processed_rows", "failed_rows",
                                         "error_log", "started_at", "finished_at", "facility_id", "uploaded_by"],
                     upload_rows)
        write_insert(fh, "isolate", isolate_cols, isolate_rows)
        write_insert(fh, "susceptibility_result", ["id", "isolate_id", "antibiotic_id", "mic_value",
                                                   "disk_diffusion_mm", "interpretation",
                                                   "reported_interpretation", "breakpoint_standard",
                                                   "breakpoint_version"], result_rows)
        fh.write("-- Sincroniza las secuencias IDENTITY despues de insertar ids explicitos\n")
        for table in ("district", "facility", "app_user", "microorganism", "antibiotic", "breakpoint",
                      "data_upload", "isolate", "susceptibility_result"):
            fh.write(f"SELECT setval(pg_get_serial_sequence('{table}', 'id'), (SELECT MAX(id) FROM {table}));\n")
        fh.write("\nCOMMIT;\n")

    print(f"aislamientos: {len(isolate_rows)}  resultados: {len(result_rows)}  breakpoints: {len(breakpoint_rows)}  "
          f"cargas: {len(upload_rows)}  usuarios: {len(users)}  panel: {len(panel_rows)}")
    for org in ("eco", "kpn", "pae", "aba", "sau", "efa", "spn", "sal"):
        total = sum(v for (o, _), v in phenotype_count.items() if o == org)
        detail = ", ".join(f"{p} {100 * v / total:.1f}%" for (o, p), v in sorted(phenotype_count.items()) if o == org)
        print(f"  {org} (n={total}): {detail}")


if __name__ == "__main__":
    main()
