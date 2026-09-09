# Guía de trabajo del equipo

## Ramas

```
main          producción, protegida — solo recibe merges desde develop
develop       integración, protegida — todo PR entra acá
feat/<algo>   trabajo de cada uno
fix/<algo>    correcciones
```

Ejemplos: `feat/jwt-filter`, `feat/isolate-entity`, `fix/n-plus-one-antibiogram`

**Nadie hace push directo a `main` ni a `develop`.** Todo pasa por pull request.

## Commits

Formato: `<tipo>: <qué hace el commit, en imperativo y en inglés>`

El mensaje se escribe completando la frase "este commit va a...": `add`, `fix`,
`remove`. Nunca `added` ni `adding`.

### Tipos

**`feat`** — algo nuevo que antes no existía y que se nota desde fuera:
una entidad, un endpoint, un filtro, el login. Es el más frecuente al inicio.

```
feat: add Isolate entity with susceptibility relation
feat: add JWT authentication filter
feat: add endpoint to query cumulative antibiogram
```

**`fix`** — corriges algo que ya existía en la rama y funcionaba mal.
Si estás construyendo algo por primera vez y corriges tu propio error antes
de mergear, eso sigue siendo parte del `feat`.

```
fix: correct fetch type causing N+1 on antibiogram query
fix: return 409 instead of 500 on duplicate isolate
```

**`refactor`** — cambias cómo está escrito el código sin cambiar lo que hace.
Mover lógica del controller al service, partir un método largo, renombrar.
Quien use la app no nota diferencia.

```
refactor: extract MIC interpretation logic into separate service
refactor: replace field injection with constructor injection
```

**`test`** — agregas o modificas pruebas, sin tocar código de producción.

```
test: add integration tests for auth endpoints
test: cover breakpoint interpretation edge cases
```

**`docs`** — solo documentación: README, colección de Postman, comentarios.

```
docs: document upload endpoints in Postman collection
docs: add entity diagram to README
```

**`chore`** — mantenimiento que no es funcionalidad, arreglo ni documentación:
configuración, dependencias, CI, `.gitignore`.

```
chore: add PostgreSQL service to CI workflow
chore: bump Spring Boot to 3.3.4
```

### Dudas frecuentes

- **¿`feat` o `refactor`?** ¿Alguien que use la app nota algo nuevo?
  Sí → `feat`. No → `refactor`.
- **El commit toca varias cosas.** Señal de que deberían ser dos commits.
  Si de verdad no se pueden separar, usa el tipo de lo más importante.
- **¿Cuándo commitear?** Por unidad de trabajo terminada, no por sesión.
  Si creaste dos entidades y la relación entre ellas, son tres commits.

**Regla práctica:** si el mensaje necesita un "y", probablemente son dos commits.

Commits pequeños y frecuentes. La rúbrica evalúa el historial, y uno con
avance diario visible vale más que tres commits gigantes al final.

## Flujo diario

```bash
git checkout develop
git pull origin develop
git checkout -b feat/mi-cosa

# ... trabajar, commitear ...

git push -u origin feat/mi-cosa
# abrir PR hacia develop en GitHub
```

## Pull requests

- Asignar al menos un revisor del equipo.
- Quien revisa deja al menos un comentario real (una pregunta, una sugerencia). La rúbrica evalúa code review.
- No hacer merge de tu propio PR sin aprobación.
- Squash merge para mantener el historial limpio.

## Reparto de módulos

| Módulo | Responsable | Paquetes |
|---|---|---|
| security | | `config/security`, `auth`, `jwt` |
| domain | | `model`, `repository`, `dto`, `mapper` |
| api | | `controller`, `exception` |
| async | | `event`, `service/upload`, `service/mail` |
| infra | todos | CI, Docker, deploy, README |

## Secretos: lo más importante de este documento

Una clave subida al repositorio queda en el historial de Git **para siempre**,
aunque la borres después. La rúbrica penaliza explícitamente commitear `.env`,
contraseñas o API keys.

### Cómo trabajar

Cada uno, al clonar:

```bash
cp .env.example .env
# rellenar con sus propios valores
openssl rand -base64 64   # para generar JWT_SECRET
```

El `.env` vive solo en tu máquina y está en el `.gitignore`. El `.env.example`
sí se sube: es la plantilla, con los nombres de las variables y **sin valores**.

En el código nunca se escribe el valor, se lee la variable:

```java
@Value("${jwt.secret}")
private String jwtSecret;
```

Y en `application.properties`:

```properties
jwt.secret=${JWT_SECRET}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

### Lo que el .gitignore NO te salva

El `.gitignore` solo evita subir archivos con nombres conocidos. **No** protege
contra:

- Escribir la clave directamente en `application.properties`
  (`jwt.secret=abc123`). Ese archivo sí se sube.
- Archivos con nombres que no anticipamos (`credenciales.txt`, `keys.json`).
- Algo ya commiteado: agregarlo al `.gitignore` después no lo borra del
  historial.

El error más común es poner el secreto de JWT "temporalmente para probar" y
que se quede. Usa `${JWT_SECRET}` desde el primer minuto.

### Si ya subiste una clave

```bash
git rm --cached .env
git commit -m "chore: remove env file from tracking"
```

Y **regenera esa clave**. Si estuvo en GitHub, considérala comprometida
aunque el repo sea privado.

### Al revisar un PR

Antes de aprobar, mira el diff y busca cualquier cadena que parezca un secreto:
contraseñas, tokens, claves de API, URLs con credenciales. Es parte del
checklist de la plantilla de PR y es responsabilidad de quien revisa, no solo
de quien escribe.

---

## Reglas que vienen de la rúbrica

- Nombres en inglés, descriptivos.
- Métodos de máximo 20-30 líneas.
- Inyección por constructor, nunca `new` para componentes de Spring.
- Sin lógica de negocio en controllers.
- Nada de credenciales en el repositorio. Todo por variables de entorno.
