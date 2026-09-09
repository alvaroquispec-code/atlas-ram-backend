# Puesta en marcha

## Requisitos

- JDK 21
- Docker y Docker Compose
- Maven (o el wrapper `./mvnw`)

## Primeros pasos

```bash
git clone <url-del-repo>
cd <repo>

cp .env.example .env
# rellenar .env — generar el secreto JWT con:
openssl rand -base64 64

docker compose up -d          # levanta PostgreSQL y pgAdmin
./mvnw spring-boot:run
```

- API: http://localhost:8080
- pgAdmin: http://localhost:5050 (admin@atlasram.local / admin)

## Comandos útiles

```bash
./mvnw clean compile      # compilar
./mvnw test               # tests
./mvnw package            # empaquetar
docker compose down -v    # borrar la base y empezar de cero
docker compose logs -f db # ver logs de PostgreSQL
```

## Si algo falla

**El puerto 5432 está ocupado:** tienes otro PostgreSQL corriendo. Apágalo o cambia el puerto en `docker-compose.yml`.

**La app no conecta a la base:** revisa que `docker compose ps` muestre `healthy` y que las credenciales del `.env` coincidan con las del compose.

**Tests fallan en CI pero pasan en local:** casi siempre son variables de entorno faltantes en el workflow, o tests que dependen del orden de ejecución.
