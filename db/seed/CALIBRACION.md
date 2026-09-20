# Seed de Atlas RAM: calibración y uso

Ajustado a las entidades de `atlas-ram-backend` (Spring Boot 3.5.6, Hibernate 6.6), incluidos los campos nuevos: `Isolate.district`, `Isolate.dataUpload` y `SusceptibilityResult.reportedInterpretation`, `breakpointStandard` y `breakpointVersion`. Se probó en PostgreSQL 16 sobre un esquema idéntico al que Hibernate genera desde las anotaciones, incluidas las restricciones `check` de los enums.

## Contenido

| Tabla | Filas |
| --- | --- |
| district | 50 (Lima Metropolitana y Callao, ubigeo INEI) |
| facility | 17 hospitales reales (10 de nivel III y 7 de nivel II) |
| facility_antibiotic_panel | 343 |
| app_user / user_facility | 26 / 28 |
| microorganism / antibiotic | 8 / 21 (códigos WHONET) |
| breakpoint | 146 (CLSI versiones "2022" y "2024") |
| data_upload | 411 (408 COMPLETED y 3 FAILED, con `error_log`) |
| isolate | 9 168 (septiembre 2024 a agosto 2026), cada uno con distrito de procedencia y carga de origen |
| susceptibility_result | 103 329 |

Todos los usuarios tienen la contraseña `AtlasRam2026!` (hash BCrypt). Hay un ADMIN, dos EPIDEMIOLOGIST, un LAB_TECHNICIAN por establecimiento (tres de ellos en dos laboratorios) y seis PUBLIC_VIEWER que representan médicos.

## Cómo se generó

La procedencia del paciente se asigna según el área de influencia del hospital: la mayoría vive en el mismo distrito o en la misma zona de Lima, y los hospitales nacionales de referencia reciben pacientes de más lejos, ponderando por población.

Cada aislamiento recibe un fenotipo (BLEE, enterobacteria productora de carbapenemasa, MRSA, *P. aeruginosa* o *A. baumannii* resistentes a carbapenémicos, VRE). Su probabilidad depende del nivel del establecimiento, del servicio clínico, de un efecto propio del establecimiento y de una tendencia temporal. El servicio se usa solo para generar; no se guarda porque el modelo no tiene ese campo.

Cada antibiótico se decide según el fenotipo, con co-resistencias enlazadas: ciprofloxacino → levofloxacino, ceftriaxona → ceftazidima → cefepima, imipenem → meropenem, gentamicina → amikacina y clindamicina → eritromicina. La CIM se genera dentro de la categoría y la interpretación se calcula contra CLSI 2024, así que CIM e interpretación siempre coinciden.

Además de los seis patógenos hospitalarios hay dos patógenos prioritarios de GLASS con perfil comunitario: *S. pneumoniae* (muestras respiratorias, hemocultivos y líquido cefalorraquídeo, con pico en invierno) y *Salmonella enterica* (heces, hemocultivos y orina, con pico en verano). Ambos tienen más pacientes pediátricos, y en ellos el servicio clínico no modifica la probabilidad de resistencia. Se generan con una semilla propia, así que agregarlos no alteró ningún dato de los otros seis.

Los paneles respetan la microbiología: no se prueba ampicilina en *K. pneumoniae* (resistencia intrínseca), ni ceftriaxona, ertapenem o cotrimoxazol en *P. aeruginosa*. Nitrofurantoína, y las quinolonas en *E. faecalis*, solo se prueban en orina. En *Salmonella* de heces no se reporta ceftriaxona, como indica CLSI. Los hospitales de nivel II no tienen ertapenem ni linezolid en su panel, y ningún resultado cae fuera del panel de su establecimiento.

## Comparación con la literatura

| Indicador | Generado (nivel III / II) | Referencia peruana |
| --- | --- | --- |
| *E. coli* resistente a ceftriaxona | 42.0% / 34.3% | 38.7% a 42.3% en urocultivos del Hospital Cayetano Heredia, 2019-2022 [1] |
| *E. coli* resistente a ciprofloxacino | 59.0% / 53.5% | 55% a 61% en Cayetano Heredia [1]; 31% a 70% en estudios nacionales [2] |
| *E. coli* resistente a cotrimoxazol | 56.5% / 55.4% | 54% en el Instituto Nacional Materno Perinatal [3] |
| *E. coli* resistente a nitrofurantoína | 8.3% / 10.4% | 18% en el INMP [3]; menos de 20% en un hospital III-1 de Chiclayo [4] |
| *E. coli* resistente a meropenem | 0.6% | Sin resistencia en el INMP ni en Chiclayo [3][4] |
| *K. pneumoniae* resistente a meropenem | 14.1% / 5.5% | Emergencia de cepas NDM-1 en hospitales de Lima [5][6]; 44% en áreas críticas de un hospital de la selva [7] |
| *P. aeruginosa* resistente a meropenem | 37.4% / 20.7% | 85% en áreas críticas de la selva [7] |
| *A. baumannii* resistente a meropenem | 79.9% / 60.3% | 97% en áreas críticas de la selva [7] |
| *S. aureus* resistente a oxacilina (MRSA) | 59.9% / 44.7% | 58% en tres hospitales de Lima [8]; 50% a 90% según hospital [9] |
| *S. aureus* no sensible a vancomicina | 0% | Sin reportes de VRSA en Perú |
| *S. pneumoniae* resistente a cotrimoxazol | 55.7% | 58% en portadores menores de 2 años de siete regiones del Perú [11] |
| *S. pneumoniae* resistente a eritromicina | 26.9% | 26.3% en portadores; azitromicina 28.9% [11] |
| *S. pneumoniae* resistente a levofloxacino | 1.3% | Resistencia excepcional |
| *Salmonella* resistente a ciprofloxacino | 14.5% (65.5% no sensible) | 11% resistente y 70% intermedio o resistente; *S.* Infantis fue el serotipo más frecuente [12] |
| *Salmonella* resistente a ceftriaxona (extraintestinal) | 26.0% | *S.* Infantis multirresistente con CTX-M-65 en Perú [13] |

Las series de la selva provienen solo de áreas críticas, por eso superan las cifras del hospital completo.

## Señales para la demo

**Tendencia.** *K. pneumoniae* resistente a meropenem pasa de 8.1% a 10.1%, 9.6%, 18.1% y 17.1% por semestre, siguiendo la emergencia de NDM descrita en Lima [5]. Conviene graficar por semestre.

**Brote ficticio.** En el Hospital Nacional Hipólito Unanue, entre abril y junio de 2026, la resistencia a meropenem llega a 62.7%, frente a 12.1% en el resto de hospitales de nivel III en el mismo trimestre. Si se mide todo 2026, el brote se diluye (43.4% frente a 15.7%), así que la alerta debe evaluarse por ventanas cortas. Sirve para probar las alertas; se cambia en `OUTBREAK` dentro de `gen_seed.py`.

**Mapa.** `Isolate.district` es opcional porque no todos los laboratorios reportan la residencia del paciente. El campo calculado `Isolate.aggregationDistrictId` (`@Formula`) usa la procedencia si se conoce y, si no, el distrito del establecimiento; toda consulta que agrupe o filtre por distrito debe usar ese campo y no `district` directamente. En el seed todos los aislamientos tienen procedencia: 28 distritos tienen al menos 30 aislamientos de *E. coli*, y la resistencia a ceftriaxona varía entre 20% y 56%.

**Reinterpretación.** Existen las versiones "2022" y "2024" de los puntos de corte; CLSI 2023 endureció gentamicina y amikacina en enterobacterias. `interpretation` está calculada con CLSI 2024. `reported_interpretation` guarda lo que informó el laboratorio: los de nivel II siguieron usando CLSI 2022 en aminoglucósidos hasta junio de 2025, así que 79 resultados difieren (`countDiscordantWithReported`). Si se reinterpretara todo el histórico con la versión 2022, cambiarían 852.

**Cargas.** Cada aislamiento apunta a su carga, y `processed_rows` coincide con los aislamientos de esa carga. Revertir `hvit_202503.csv` u otra carga borra exactamente sus aislamientos y resultados. Hay 378 filas rechazadas en total, con errores típicos de ingesta en `error_log` (microorganismo no reconocido, CIM con texto como ">=16", fecha inválida), más tres archivos que fallaron completos.

## Limitaciones (para el informe)

> Los datos de prueba son sintéticos. Emplean establecimientos y ubigeos reales, nomenclatura WHONET y puntos de corte CLSI, y están calibrados con estudios peruanos publicados, pero no representan la situación de ningún establecimiento. El brote incluido es ficticio.

**Puntos de corte que dependen del sitio de infección.** La restricción única de `Breakpoint` (microorganismo, antibiótico, norma, versión) admite un solo umbral por combinación. CLSI publica tres para penicilina en *S. pneumoniae* (meningitis, no meningitis y vía oral) y dos para ceftriaxona, así que el neumococo solo incluye eritromicina, clindamicina, levofloxacino y cotrimoxazol. Clínicamente es un panel incompleto, sobre todo en líquido cefalorraquídeo. Lo mismo ocurre, en menor grado, con cefazolina en enterobacterias: CLSI tiene un umbral sistémico y otro para infección urinaria no complicada, y el seed usa el sistémico en todas las muestras. La solución es agregar un campo de sitio de infección a `Breakpoint` e incluirlo en la restricción única.

**Procedencia del paciente.** WHONET, el software más usado por los laboratorios, registra por defecto edad, sexo y ubicación dentro del hospital, pero no el distrito de residencia; solo lo tendría un laboratorio que lo haya agregado como campo definido por el usuario. Si la fuente real no lo trae, el mapa refleja la ubicación de los establecimientos y no la residencia de los pacientes, y así debe presentarse.

No se aplica la regla de "primer aislamiento por paciente" de CLSI M39, porque no hay entidad paciente. Las CIM en el tope del panel automatizado equivalen a "≥", aunque se guardan como número.

## Cómo cargarlo

1. Si ya tenían una base local, empezar de cero con `docker compose down -v`. Hibernate con `ddl-auto=update` no actualiza la restricción `check` del enum `UploadStatus` para aceptar `REVERTED`. Si antes usaban otro seed con `spring.sql.init.mode=always` y `src/main/resources/data.sql`, quiten ambos.
2. Levantar la base con `docker compose up -d`.
3. Arrancar la aplicación una vez para que Hibernate cree las tablas (`ddl-auto=update`) y detenerla.
4. Copiar el archivo al contenedor y ejecutarlo desde la raíz del repositorio:

```bash
docker cp db/seed/data.sql atlasram-db:/tmp/data.sql
docker exec atlasram-db psql -U atlasram -d atlasram -v ON_ERROR_STOP=1 -f /tmp/data.sql
```

El script va en una transacción: si las tablas ya tienen datos, falla sin insertar nada. Para recargar, borrar el volumen con `docker compose down -v` y repetir. Después de cargar, la aplicación sigue insertando normalmente porque las secuencias quedan sincronizadas.

Está en `db/seed/` y no en `src/main/resources/data.sql` a propósito: con `spring.sql.init.mode=always` se ejecutaría en cada arranque.

Si cambia una entidad, se edita `gen_seed.py` y se ejecuta `pip install bcrypt` y luego `python3 gen_seed.py`. Las semillas son fijas, así que los datos son siempre los mismos; solo cambia la sal de los hashes BCrypt, que es aleatoria por diseño.

## Referencias

1. Comparación de la resistencia antibiótica de *E. coli* en urocultivos antes y después de la pandemia, Hospital Cayetano Heredia, 2019-2022. Repositorio UPCH. https://repositorio.upch.edu.pe/entities/publication/d0582fbd-8ee5-440e-8b7a-33c49c98300a
2. Infección urinaria alta comunitaria por *E. coli* resistente a ciprofloxacino, Hospital Rebagliati. https://dialnet.unirioja.es/servlet/articulo?codigo=5687819
3. *E. coli* multidrogorresistente en urocultivos del Instituto Nacional Materno Perinatal. Rev Peru Med Exp Salud Publica 2021;38(4). http://www.scielo.org.pe/scielo.php?script=sci_arttext&pid=S1726-46342021000400668
4. Resistencia antibiótica de *E. coli* según producción de BLEE en urocultivos, hospital III-1, Chiclayo, 2020. http://www.scielo.org.pe/scielo.php?script=sci_arttext&pid=S2227-47312022000400017
5. Emerging carbapenem-resistant *Klebsiella pneumoniae* in a tertiary care hospital in Lima, Peru. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11792469/
6. Spread of ST348 *K. pneumoniae* producing NDM-1 in a Peruvian hospital. https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7563475/
7. Perfil microbiológico de sensibilidad y resistencia en un hospital general de la selva peruana, 2021. http://www.scielo.org.pe/scielo.php?script=sci_arttext&pid=S2308-05312024000400035
8. *S. aureus* resistente a meticilina adquirido en la comunidad en tres hospitales de Lima. Rev Med Hered 2010;21(1). https://revistas.upch.edu.pe/index.php/RMH/article/view/1139
9. Susceptibilidad antimicrobiana de *S. aureus* sensible, borderline y resistente a meticilina. Rev Med Hered 2003. http://www.scielo.org.pe/scielo.php?script=sci_arttext&pid=S1018-130X2003000400006
10. CLSI. M100, Performance Standards for Antimicrobial Susceptibility Testing (ediciones 32 y 34) y M39, Analysis and Presentation of Cumulative Antimicrobial Susceptibility Test Data.
11. Resistencia antibiótica de *Streptococcus pneumoniae* en portadores nasofaríngeos sanos de siete regiones del Perú. Rev Peru Med Exp Salud Publica 2013;30(4). http://www.scielo.org.pe/scielo.php?script=sci_arttext&pid=S1726-46342013000400006
12. Vigilancia de la resistencia antimicrobiana de *Salmonella* spp. Repositorio UPCH. https://repositorio.upch.edu.pe/entities/publication/fc6a6eef-245c-4cb5-a0d5-c5dbb931d3a8
13. Multidrogorresistencia de *Salmonella* Infantis en Perú: un estudio mediante secuenciamiento de nueva generación. Rev Peru Med Exp Salud Publica 2019;36(1). http://www.scielo.org.pe/scielo.php?pid=S1726-46342019000100006&script=sci_arttext
