# Atlas RAM

**Plataforma de antibiogramas acumulados para una terapia antibiótica empírica informada**

| | |
|---|---|
| **Curso** | CS 2031 Desarrollo Basado en Plataforma |
| **Universidad** | Universidad de Ingeniería y Tecnología (UTEC), 2026-2 |
| **Integrantes** | Álvaro Felipe Quispe Carrillo · Joaquim Alexander Carrión Díaz · Fabian Isaias Sanchez Zedano · Adriano Alonso Cosme Salazar |
| **API desplegada** | `https://3-85-201-152.sslip.io/api/v1` |

---

## Proyecto 2: aplicación completa

| Componente | URL | Repositorio |
|---|---|---|
| **API REST** (Spring Boot) | https://3-85-201-152.sslip.io/api/v1 | este repositorio |
| **Web** (React) | https://atlas-ram-web.vercel.app | [`atlas-ram-web`](https://github.com/alvaroquispec-code/atlas-ram-web) |
| **Móvil** (React Native + Expo) | https://github.com/alvaroquispec-code/atlas-ram-mobile/releases/latest/download/atlas-ram.apk | [`atlas-ram-mobile`](https://github.com/alvaroquispec-code/atlas-ram-mobile) |

Usuario de prueba: `admin@atlasram.test` / `AtlasRam2026!` (datos de demostración). También hay técnicos de laboratorio, como `lab.hnch@atlasram.test`, con la misma contraseña.

### Arquitectura de despliegue

```
 Web (Vercel, HTTPS) ──┐
                       ├──► Caddy :443 (HTTPS, Let's Encrypt) ──► Spring Boot :8080 ──► RDS PostgreSQL 16
 Móvil (APK, HTTPS) ───┘          EC2 t3.micro · Amazon Linux 2023                      (solo acepta al SG del servidor)
                                                   │
                                                   └──► Nominatim (OpenStreetMap), llamado desde el móvil
```

| Pieza | Configuración |
|---|---|
| HTTPS | Caddy como proxy inverso con certificado automático de Let's Encrypt sobre `3-85-201-152.sslip.io` |
| CORS | `CORS_ALLOWED_ORIGINS` en el servidor, con el dominio de Vercel y `localhost` para desarrollo |
| Secretos | JWT y credenciales de la base solo en variables de entorno del servidor (`/etc/atlasram.env`), fuera del repositorio |
| Frontends | `VITE_API_URL` (Vercel) y `EXPO_PUBLIC_API_URL` (`eas.json`): ninguna URL está escrita en el código |

### Integrantes y participación

El porcentaje indica cuánto cumplió cada integrante de sus responsabilidades en cada fase (100 % = cumplimiento completo).

| Integrante | Principales aportes | Fase 1 (API) | Fase 2 (web, móvil y despliegue) |
|---|---|:---:|:---:|
| Álvaro Felipe Quispe Carrillo | Modelo de dominio y lógica de la API; despliegue completo (AWS EC2, RDS, HTTPS con Caddy, Vercel, EAS); web: panel de resistencia, aislamientos, usuarios y catálogo; base de la app móvil | 100 % | 100 % |
| Joaquim Alexander Carrión Díaz | Fase 1: autenticación JWT con refresh tokens y autorización por rol (#5). Fase 2: web, con login y registro, cliente API con interceptores y rutas protegidas | 100 % | 100 % |
| Adriano Alonso Cosme Salazar | Fase 1: ingesta asíncrona de CSV, eventos de dominio y notificaciones (#8). Fase 2: app móvil, con escáner QR, registro de aislamientos y publicación del APK con EAS | 100 % | 100 % |
| Fabian Isaias Sanchez Zedano | Fase 1: excepciones propias, manejo global de errores y endpoints de la API (#6). No participó en la fase 2 | 100 % | 0 % |

---|---|
| Álvaro Quispe | Backend, despliegue (AWS, HTTPS, Vercel), web (bloques 2 y 3) y base de la app móvil |
| Joaquim Carrión | Web: autenticación, cliente API y rutas protegidas |
| Adriano Cosme | Móvil: escáner QR, registro de aislamientos y publicación con EAS |

---

## Índice

- [Proyecto 2: aplicación completa](#proyecto-2-aplicación-completa)
- [Introducción](#introducción)
- [Identificación del problema](#identificación-del-problema)
- [Descripción de la solución](#descripción-de-la-solución)
- [Modelo de entidades](#modelo-de-entidades)
- [Manejo de errores](#manejo-de-errores)
- [Medidas de seguridad](#medidas-de-seguridad)
- [Eventos y asincronía](#eventos-y-asincronía)
- [Despliegue](#despliegue)
- [Ejecución local](#ejecución-local)
- [GitHub y gestión del proyecto](#github-y-gestión-del-proyecto)
- [Conclusión](#conclusión)
- [Apéndices](#apéndices)

---

## Introducción

### Contexto

La resistencia antimicrobiana es una de las principales amenazas de salud pública según la Organización Mundial de la Salud. Cuando un paciente llega con una infección grave, el médico no puede esperar los dos o tres días que tarda el cultivo: tiene que elegir un antibiótico de inmediato. Esa elección, llamada **terapia empírica**, se apoya en el **antibiograma acumulado**, un resumen de qué porcentaje de las bacterias aisladas en una institución o región resiste a cada antibiótico.

En el Perú, los laboratorios generan estos datos a diario, pero quedan dispersos en hojas de cálculo y exportaciones de WHONET que rara vez se consolidan. Los antibiogramas acumulados, cuando existen, se publican una vez al año y en PDF.

### Objetivos

- Centralizar los resultados de susceptibilidad de múltiples laboratorios en un solo sistema.
- Interpretar cada concentración mínima inhibitoria (CIM) contra puntos de corte versionados, de forma trazable.
- Calcular antibiogramas acumulados filtrables por microorganismo, distrito y período, respetando el umbral mínimo de la guía CLSI M39.
- Procesar cargas masivas de forma asíncrona y alertar cuando una combinación supera un umbral de resistencia.
- Proteger el acceso según el rol de cada usuario.

---

## Identificación del problema

### Descripción

Un laboratorio clínico reporta, para cada aislamiento bacteriano, la CIM de cada antibiótico probado y su interpretación: sensible, intermedio o resistente. Esa interpretación depende de un **punto de corte** publicado por comités como el CLSI, que se revisa cada año. En 2019, por ejemplo, el CLSI redujo el punto de corte de ciprofloxacino para enterobacterias: resultados que antes eran sensibles pasaron a ser resistentes sin que la bacteria cambiara.

Si un sistema solo guarda la interpretación final, pierde la capacidad de explicar por qué un resultado dice lo que dice, y una serie histórica deja de ser comparable. Si además los datos no se agregan, el médico decide a ciegas.

### Justificación

Una terapia empírica mal elegida retrasa el tratamiento efectivo y selecciona más resistencia. Contar con datos locales, actualizados y trazables es la base de cualquier programa de optimización de antimicrobianos. El problema no es la falta de datos, sino su fragmentación.

---

## Descripción de la solución

### Funcionalidades implementadas

**Carga masiva asíncrona.** Un técnico sube un archivo CSV con los resultados de su laboratorio. El servidor responde `202 Accepted` de inmediato y procesa el archivo en segundo plano. Cada fila con errores se registra sin abortar la carga completa, y el usuario consulta el progreso en cualquier momento.

**Interpretación trazable.** Cada resultado conserva la CIM cruda, la interpretación que reportó el laboratorio y la que calcula el sistema, junto con el estándar y la versión del punto de corte aplicado. Esto permite reinterpretar el histórico cuando cambie la norma.

**Antibiograma acumulado.** Sigue la guía CLSI M39: cuenta solo el **primer aislamiento de cada paciente y especie** en el período (los cultivos repetidos de un mismo paciente suelen ser los más resistentes e inflarían el porcentaje), informa el porcentaje de sensibles y de resistentes con su **intervalo de confianza de Wilson al 95 %**, y omite las combinaciones con menos de 30 aislamientos, lo que además reduce el riesgo de reidentificar pacientes en grupos pequeños. Cada fila dice cuántos aislamientos repetidos se excluyeron y qué porcentaje tenía paciente identificado.

**Hospitales de referencia.** Un distrito pequeño, por ejemplo sin hospital propio, rara vez reúne 30 resultados por combinación. En vez de mostrar un porcentaje poco confiable, `GET /antibiogram/referral` identifica los tres hospitales donde más se atienden los pacientes de ese distrito y devuelve el antibiograma de esos hospitales.

**Búsqueda de aislamientos.** El listado acepta filtros opcionales y combinables (establecimiento, microorganismo y tipo de muestra), construidos con JPA Specifications, además de paginación y orden.

**Alertas de brote.** Tras cada carga, el sistema compara las últimas 4 semanas de cada establecimiento con su propia línea base de 12 meses mediante una prueba binomial exacta, agrupa la señal por clase de antibiótico y mantiene una sola alerta activa por combinación; si la carga que la originó se revierte, la alerta se retira con el motivo.

**Control de acceso por rol.** Administradores, técnicos de laboratorio, epidemiólogos y usuarios públicos tienen permisos distintos.

### Tecnologías utilizadas

| Categoría | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.5.6 (Web, Data JPA, Security, Validation, Mail) |
| Persistencia | Hibernate 6.6, PostgreSQL 16, Flyway (migraciones) |
| Autenticación | JWT con jjwt 0.12.6, BCrypt |
| Utilidades | Lombok |
| Infraestructura | Docker Compose (desarrollo), AWS EC2 y RDS (producción) |
| Integración continua | GitHub Actions |

### Arquitectura

```mermaid
flowchart LR
    C[Cliente] -->|HTTP + JWT| F[JwtAuthenticationFilter]
    F --> CT[Controllers]
    CT --> S[Services]
    S --> R[Repositories]
    R --> DB[(PostgreSQL)]
    S -->|publica| E[Eventos]
    E -->|@Async| L[Listeners]
    L --> M[MailService]
```

El código sigue una arquitectura en capas: los controladores reciben la petición y delegan en los servicios, que contienen la lógica de negocio y acceden a los datos mediante repositorios de Spring Data. Las entidades nunca se exponen: toda entrada y salida pasa por DTOs y mappers.

---

## Modelo de entidades

```mermaid
erDiagram
    DISTRICT {
        bigint id PK
        string ubigeo UK
        string name
        string province
        string department
    }
    FACILITY {
        bigint id PK
        string code UK
        string name
        bigint district_id FK
    }
    MICROORGANISM {
        bigint id PK
        string code UK
        string genus
        string species
        string gram_stain
    }
    ANTIBIOTIC {
        bigint id PK
        string code UK
        string name
        string antibiotic_class
    }
    APP_USER {
        bigint id PK
        string email UK
        string password_hash
        string full_name
        string role
        boolean active
        timestamp created_at
    }
    ISOLATE {
        bigint id PK
        date collection_date
        string specimen_type
        int patient_age
        string patient_sex
        bigint facility_id FK
        bigint district_id FK
        bigint microorganism_id FK
        bigint data_upload_id FK
    }
    SUSCEPTIBILITY_RESULT {
        bigint id PK
        bigint isolate_id FK
        bigint antibiotic_id FK
        decimal mic_value
        int disk_diffusion_mm
        string interpretation
        string reported_interpretation
        string breakpoint_standard
        string breakpoint_version
    }
    BREAKPOINT {
        bigint id PK
        bigint microorganism_id FK
        bigint antibiotic_id FK
        string standard
        string version
        decimal susceptible_max
        decimal resistant_min
    }
    DATA_UPLOAD {
        bigint id PK
        string file_name
        string status
        int total_rows
        int processed_rows
        int failed_rows
        string error_log
        timestamp started_at
        timestamp finished_at
        bigint uploaded_by FK
        bigint facility_id FK
    }
    USER_FACILITY {
        bigint user_id PK, FK
        bigint facility_id PK, FK
    }
    FACILITY_ANTIBIOTIC_PANEL {
        bigint facility_id PK, FK
        bigint antibiotic_id PK, FK
    }

    DISTRICT ||--o{ FACILITY : "ubica"
    DISTRICT |o--o{ ISOLATE : "residencia"
    FACILITY ||--o{ ISOLATE : "procesa"
    MICROORGANISM ||--o{ ISOLATE : "identifica"
    ISOLATE ||--o{ SUSCEPTIBILITY_RESULT : "contiene"
    ANTIBIOTIC ||--o{ SUSCEPTIBILITY_RESULT : "probado en"
    MICROORGANISM ||--o{ BREAKPOINT : "tiene"
    ANTIBIOTIC ||--o{ BREAKPOINT : "tiene"
    APP_USER ||--o{ USER_FACILITY : "pertenece"
    FACILITY ||--o{ USER_FACILITY : "asigna"
    FACILITY ||--o{ FACILITY_ANTIBIOTIC_PANEL : "declara"
    ANTIBIOTIC ||--o{ FACILITY_ANTIBIOTIC_PANEL : "incluido en"
    APP_USER ||--o{ DATA_UPLOAD : "sube"
    FACILITY ||--o{ DATA_UPLOAD : "recibe"
    DATA_UPLOAD |o--o{ ISOLATE : "origina"
```

### Descripción de entidades

| Entidad | Propósito |
|---|---|
| `District` | Distritos con su ubigeo del INEI. |
| `Facility` | Establecimientos de salud, cada uno en un distrito. |
| `Microorganism` | Catálogo de microorganismos con código WHONET y tinción de Gram. |
| `Antibiotic` | Catálogo de antibióticos con código WHONET y familia. |
| `User` | Usuarios con correo, contraseña cifrada y rol. |
| `Isolate` | Un aislamiento bacteriano: fecha, tipo de muestra, microorganismo y establecimiento. |
| `SusceptibilityResult` | La CIM de un antibiótico sobre un aislamiento y su interpretación. |
| `Breakpoint` | Puntos de corte por microorganismo, antibiótico, estándar y versión. |
| `DataUpload` | Registro de cada carga masiva con su estado y errores. |

Se usan los cuatro tipos de relación de JPA. Todos los `@ManyToOne` son `LAZY` para evitar consultas N+1. La relación entre `Isolate` y `SusceptibilityResult` es bidireccional con `cascade = ALL` y `orphanRemoval`, porque un resultado no existe fuera de su aislamiento. Las relaciones hacia catálogos son unidireccionales, porque nadie necesita cargar todos los aislamientos de un distrito.

### Decisiones de diseño

- **Sin identificador de paciente.** El modelo no guarda nombre ni documento: la anonimización está en el diseño, no en una promesa.
- **CIM en `BigDecimal`.** La comparación contra puntos de corte exige aritmética exacta.
- **Puntos de corte versionados.** La restricción única combina microorganismo, antibiótico, estándar y versión, de modo que conviven varias versiones del CLSI.
- **Restricciones con nombre explícito.** `uk_isolate_antibiotic` impide duplicar un antibiótico en un aislamiento, y su nombre es el contrato con el manejador de errores.
- **Migraciones con Flyway.** El esquema vive en `src/main/resources/db/migration` como SQL versionado (`V1__esquema_inicial.sql`, `V2__…`), y Hibernate corre con `ddl-auto=validate`: si una entidad no coincide con la base, la aplicación no arranca. La base de producción, creada antes con `ddl-auto=update`, se adoptó con un *baseline* en la versión 1, sin volver a ejecutar V1.
- **Distrito de agregación.** La procedencia del paciente es opcional. Si falta, un campo calculado usa el distrito del establecimiento, sin copiarlo al guardar, para no confundir un dato real con uno inferido.

---

## Manejo de errores

La API centraliza el manejo de errores en un `@ControllerAdvice`, de modo que ninguna excepción llega al cliente como una traza de Java. Toda respuesta de error comparte el mismo formato:

```json
{
  "timestamp": "2026-09-22T08:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Establecimiento no encontrado: 99",
  "path": "/api/v1/facilities/99"
}
```

Se definieron ocho excepciones personalizadas, y el manejador traduce cada una a un código HTTP:

| Excepción | Código | Cuándo ocurre |
|---|---|---|
| `ResourceNotFoundException` | 404 | el recurso solicitado no existe |
| `DuplicateResourceException` | 409 | se intenta crear un registro que ya existe |
| `UploadNotRevertableException` | 409 | reservada para revertir cargas, que aún no tiene endpoint |
| `InvalidCredentialsException` | 401 | correo o contraseña incorrectos |
| `InvalidTokenException` | 401 | token ausente, expirado o mal firmado |
| `BusinessRuleException` | 422 | la petición es válida pero viola una regla del dominio |
| `BreakpointNotFoundException` | 422 | no hay punto de corte para interpretar un resultado |
| `InvalidCsvFormatException` | 400 | el archivo de carga no tiene el formato esperado |

El manejador también captura las excepciones propias de Spring, para que un error del cliente nunca llegue como `500`:

| Excepción de Spring | Código |
|---|---|
| `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, parámetro faltante o con tipo inválido | 400 |
| `AccessDeniedException` (por ejemplo, un técnico que registra en un establecimiento que no tiene asignado) | 403 |
| `NoResourceFoundException` (ruta inexistente) | 404 |
| `HttpRequestMethodNotSupportedException` | 405 |
| `DataIntegrityViolationException` (se viola una restricción de la base) | 409 |
| `MaxUploadSizeExceededException` (archivo de más de 20 MB) | 413 |
| `HttpMediaTypeNotSupportedException` | 415 |

`AccessDeniedException` necesita su propio manejador porque `@PreAuthorize` la lanza dentro del controlador: la atrapa el `@ControllerAdvice` y no el `AccessDeniedHandler` de Spring Security. Cualquier otra excepción responde `500` con un mensaje genérico y se registra en el log, sin exponer la traza al cliente.

Manejar los errores de forma global garantiza respuestas consistentes para el cliente, evita filtrar detalles internos y concentra la lógica en un solo lugar.

---

## Medidas de seguridad

### Seguridad de datos

- **Autenticación sin estado con JWT.** El login entrega un token de acceso de una hora y un token de renovación. Un filtro valida el token en cada petición y carga el usuario en el contexto de seguridad.
- **Contraseñas con BCrypt.** Nunca se almacena ni se devuelve la contraseña en texto plano.
- **Cuatro roles.** `ADMIN`, `LAB_TECHNICIAN`, `EPIDEMIOLOGIST` y `PUBLIC_VIEWER`, guardados en la base y en el token. Los métodos sensibles se protegen con `@PreAuthorize`.
- **Registro con permisos mínimos.** Todo usuario nuevo se crea como `PUBLIC_VIEWER`; solo un administrador puede elevar su rol.
- **Control por establecimiento.** Un técnico de laboratorio solo puede registrar aislamientos y cargar archivos de los establecimientos que tiene asignados. Lo verifica `@PreAuthorize` con un evaluador propio sobre la relación `user_facility`.
- **Secretos fuera del código.** La clave JWT y las credenciales se leen de variables de entorno. El archivo `.env` está excluido del repositorio.
- **Datos anonimizados por diseño**, como se explicó en el modelo.

### Prevención de vulnerabilidades

- **Inyección SQL:** todas las consultas usan JPA con parámetros enlazados; no se concatena SQL.
- **XSS:** la API solo devuelve JSON y no renderiza HTML con datos de usuario.
- **CSRF:** la protección se desactiva de forma deliberada porque la API es sin estado y no usa cookies de sesión.
- **CORS:** configurado en Spring Security para controlar qué orígenes pueden llamar a la API desde un navegador.
- **Validación de entrada:** Bean Validation en todos los DTOs de entrada.

**Limitación conocida:** los tokens de renovación no se persisten, así que no pueden revocarse antes de expirar (ver issue #4).

---

## Eventos y asincronía

La ingesta de un archivo con miles de filas puede tardar. Si se procesara dentro de la petición HTTP, el cliente quedaría bloqueado y la conexión podría expirar. Por eso el endpoint de carga responde `202 Accepted` y delega el trabajo.

| Evento | Se publica cuando | Efecto |
|---|---|---|
| `UploadCompletedEvent` | una carga termina | correo al usuario con el resumen |
| `UploadFailedEvent` | una carga falla | correo al usuario con el motivo |
| `ResistanceAlertEvent` | una combinación supera el umbral | correo a epidemiología |

Los listeners usan `@TransactionalEventListener`, que espera a que la transacción se confirme antes de ejecutarse: así no se notifica una carga que luego se revierte. Los eventos llevan solo datos planos, nunca entidades, porque el listener corre en otro hilo fuera de la transacción.

Se configuran dos `ThreadPoolTaskExecutor` separados: uno para la ingesta y otro para las notificaciones. Si compartieran hilos, una carga larga retrasaría todos los correos.

---

## Despliegue

La API está desplegada en AWS en `https://3-85-201-152.sslip.io/api/v1`.

| Componente | Configuración |
|---|---|
| Aplicación | EC2 `t3.micro`, Amazon Linux 2023, Java 21, servicio `systemd` |
| Base de datos | RDS PostgreSQL 16, `db.t4g.micro`, conexión cifrada |
| Red | La base solo acepta conexiones desde el grupo de seguridad del servidor |
| HTTPS | Caddy como proxy inverso delante de Spring Boot, con certificado de Let's Encrypt |
| Configuración | Variables de entorno en el servidor, fuera del repositorio |

Prueba rápida: `GET https://3-85-201-152.sslip.io/api/v1/districts`

---

## Ejecución local

**Requisitos:** Java 21, Docker Desktop.

```bash
git clone https://github.com/alvaroquispec-code/atlas-ram-backend.git
cd atlas-ram-backend
cp .env.example .env          # completar contraseñas y JWT_SECRET
docker compose up -d          # PostgreSQL en el puerto 5433
export JWT_SECRET=...         # Spring no lee el .env: la variable debe estar en el entorno
export PATIENT_KEY_SECRET=... # openssl rand -base64 32
./mvnw spring-boot:run        # Flyway crea las tablas (db/migration)
docker cp db/seed/data.sql atlasram-db:/tmp/data.sql
docker exec atlasram-db psql -U atlasram -d atlasram -f /tmp/data.sql
```

### Variables de entorno

| Variable | Descripción |
|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de la base |
| `SPRING_DATASOURCE_USERNAME` | usuario de la base |
| `SPRING_DATASOURCE_PASSWORD` | contraseña de la base |
| `JWT_SECRET` | clave de firma en base64, mínimo 64 bytes |
| `JWT_EXPIRATION_MS` | vigencia del token de acceso |
| `PATIENT_KEY_SECRET` | secreto en base64 (mínimo 32 bytes) para seudonimizar el identificador de paciente con HMAC-SHA256 |
| `JPA_DDL_AUTO` | modo de Hibernate sobre el esquema; por defecto `validate` (el esquema lo maneja Flyway) |
| `MAIL_ENABLED` | activa el envío real de correos |
| `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` | servidor SMTP |

### Formato del CSV de carga

UTF-8, separado por comas o por punto y coma (Excel en español), con cabecera. Las columnas se reconocen
por su nombre, en cualquier orden; las que el sistema no usa se ignoran. Una fila = un antibiótico de un aislamiento.

| Columna | Obligatoria | Ejemplo | Notas |
|---|---|---|---|
| `fecha_toma` | sí | `2026-08-20` o `20/08/2026` | no puede ser futura |
| `tipo_muestra` | sí | `URINE`, `ORINA`, `SANGRE`, `LCR` | |
| `codigo_microorganismo` | sí | `eco` | códigos WHONET |
| `codigo_antibiotico` | sí | `MEM` | códigos WHONET |
| `cim` | * | `<=0.25`, `>=16`, `0,5`, `4/76` | con calificador; coma decimal aceptada |
| `interpretacion_laboratorio` | * | `S`, `I`, `R`, `RESISTENTE` | se usa si la CIM no basta para interpretar |
| `id_paciente` | recomendada | historia clínica | se guarda solo un seudónimo HMAC; permite la regla del primer aislamiento (CLSI M39) |
| `id_aislamiento` | recomendada | número de muestra | evita mezclar pacientes y registrar dos veces el mismo aislamiento |
| `codigo_establecimiento` | no | `HNCH` | si viene, debe ser el de la carga |
| `edad`, `sexo`, `ubigeo_residencia` | no | `47`, `F`, `150135` | |

\* Cada fila necesita una CIM interpretable o la interpretación del laboratorio.

### Endpoints principales

Todos bajo `/api/v1`. La documentación completa está en `postman_collection.json`.

| Método | Ruta | Acceso |
|---|---|---|
| `POST` | `/auth/register` | público |
| `POST` | `/auth/login` | público |
| `GET` | `/districts`, `/microorganisms`, `/antibiotics`, `/facilities` | público |
| `GET` | `/antibiogram` | público |
| `GET` | `/antibiogram/referral?districtId=` | público: antibiograma de los hospitales donde se atienden los pacientes del distrito (los 3 que más reciben) |
| `GET` | `/auth/me/facilities` | autenticado: establecimientos donde el usuario puede registrar |
| `GET` | `/isolates?facilityId&microorganismId&specimen&page&size&sort` | autenticado (filtros opcionales y combinables) |
| `POST` | `/uploads` | técnico o administrador |
| `GET` | `/uploads/{id}` | autenticado |

Usuarios de prueba del seed: `admin@atlasram.test`, contraseña `AtlasRam2026!`.

---

## GitHub y gestión del proyecto

El trabajo se dividió en cuatro módulos: dominio, seguridad, API y errores, y asincronía con despliegue. Cada integrante trabajó en ramas con prefijo `feat/`, `fix/`, `chore/` o `docs/`, que se integraron en `develop` mediante pull requests revisados por otro integrante. La rama `main` recibe solo versiones estables.

Los commits siguen la convención *Conventional Commits*. Las tareas pendientes y las limitaciones conocidas se registran como issues, organizados con labels por módulo y un milestone para la entrega de la semana 7.

GitHub Actions compila el proyecto en cada push y pull request, y verifica que no se suban archivos de secretos.

---

## Conclusión

### Logros

Atlas RAM convierte resultados dispersos de laboratorio en antibiogramas consultables, con una interpretación trazable a la versión del estándar clínico aplicado. El sistema procesa cargas masivas sin bloquear al usuario, alerta sobre resistencias elevadas y está desplegado y accesible públicamente.

### Aprendizajes clave

Aprendimos que las decisiones de modelado tienen consecuencias clínicas: guardar la CIM cruda en lugar de solo la interpretación es lo que hace posible reinterpretar datos históricos. También que la asincronía exige cuidado con las transacciones, y que coordinar cuatro módulos obliga a acordar contratos, como el nombre de una restricción, antes de escribir código.

### Trabajo futuro

- Persistir los tokens de renovación para permitir su revocación (#4).
- Validar los antibióticos reportados contra el panel declarado de cada establecimiento (#1).
- Umbrales de alerta por combinación de microorganismo y antibiótico.
- Procesamiento por lotes para archivos muy grandes.
- Reinterpretación masiva del histórico cuando se publique un nuevo estándar.

---

## Apéndices

### Datos de prueba

Los datos del seed son **sintéticos**. Los catálogos usan nomenclatura real (códigos WHONET, ubigeo del INEI), y las tasas de resistencia están calibradas con estudios peruanos publicados. No corresponden a pacientes reales. El detalle está en `db/seed/CALIBRACION.md`.

### Licencia

MIT.

### Referencias

1. Clinical and Laboratory Standards Institute. *M100: Performance Standards for Antimicrobial Susceptibility Testing*.
2. Clinical and Laboratory Standards Institute. *M39: Analysis and Presentation of Cumulative Antimicrobial Susceptibility Test Data*.
3. Organización Mundial de la Salud. *Global Antimicrobial Resistance and Use Surveillance System (GLASS)*.
4. Levy-Blitchtein S, Roca I, Plasencia-Rebata S, et al. *Emergence and spread of carbapenem-resistant Acinetobacter baumannii international clones II and III in Lima, Peru*. Emerging Microbes & Infections. 2018;7(1):119. doi:10.1038/s41426-018-0127-9
5. Krapp F, Cuicapuza D, Salvatierra G, et al. *Emerging carbapenem-resistant Klebsiella pneumoniae in a tertiary care hospital in Lima, Peru*. Microbiology Spectrum. 2025;13(2). doi:10.1128/spectrum.01825-24
6. Montañez-Valverde RA, Montenegro-Idrogo JJ, Arenas-Significación FR, Vásquez-Alva R. *Infección urinaria alta comunitaria por E. coli resistente a ciprofloxacino: características asociadas en pacientes de un hospital nacional en Perú*. Anales de la Facultad de Medicina. 2015;76(4):385-391.
7. Ley N.º 29733, Ley de Protección de Datos Personales del Perú.
