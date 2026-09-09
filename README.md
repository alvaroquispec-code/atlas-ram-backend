# Atlas RAM

Plataforma de antibiogramas acumulados para terapia empírica informada.

**CS 2031 — Desarrollo Basado en Plataformas · UTEC · 2026-2**

| Integrante | Código | GitHub |
|---|---|---|
| Álvaro Felipe Quispe Carrillo | 202510375 | |
| Cosme Salazar, Adriano Alonso | 202520096 | |
| Carrión Díaz, Joaquim Alexander | 202510461 | |
| Fabián Isaías Sánchez Zedano | 202410250 | |

**Deployment:** <!-- URL cuando esté desplegado -->

---

## Índice

1. [Introducción](#introducción)
2. [Identificación del problema](#identificación-del-problema)
3. [Descripción de la solución](#descripción-de-la-solución)
4. [Modelo de entidades](#modelo-de-entidades)
5. [Manejo de errores](#manejo-de-errores)
6. [Medidas de seguridad](#medidas-de-seguridad)
7. [Eventos y asincronía](#eventos-y-asincronía)
8. [GitHub y gestión del proyecto](#github-y-gestión-del-proyecto)
9. [Instalación y ejecución](#instalación-y-ejecución)
10. [Conclusión](#conclusión)
11. [Apéndices](#apéndices)

---

## Introducción

### Contexto

<!-- TODO: por qué surge esta necesidad. Reutilizar la propuesta y ampliar. -->

### Objetivos del proyecto

<!-- TODO: objetivos específicos y verificables -->

---

## Identificación del problema

### Descripción del problema

<!-- TODO -->

### Justificación

<!-- TODO: por qué es relevante resolverlo -->

---

## Descripción de la solución

### Funcionalidades implementadas

<!-- TODO: lista de lo que REALMENTE quedó implementado, y cómo cada una
     contribuye a resolver el problema. No prometer lo que no está. -->

### Tecnologías utilizadas

| Categoría | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.x |
| Seguridad | Spring Security + JWT |
| Persistencia | Spring Data JPA / Hibernate |
| Base de datos | PostgreSQL 16 |
| Testing | JUnit 5, Mockito |
| Documentación de API | Postman |
| CI | GitHub Actions |
| Contenedores | Docker Compose |
| Despliegue | <!-- TODO --> |

---

## Modelo de entidades

<!-- TODO: insertar el diagrama ER como imagen en docs/ -->

### Descripción de entidades

<!-- TODO: por cada entidad, sus atributos clave y su rol.
     Explicar especialmente:
     - por qué SusceptibilityResult es entidad y no un @ManyToMany simple
     - por qué el distrito se asocia a establecimiento y a aislamiento
     - los fetch types elegidos y por qué -->

---

## Manejo de errores

<!-- TODO: listar las excepciones personalizadas y qué representa cada una.
     Explicar el @ControllerAdvice y el formato del ErrorResponseDTO.
     Tabla de status codes usados. -->

---

## Medidas de seguridad

### Seguridad de datos

<!-- TODO: BCrypt, JWT, gestión de roles, variables de entorno -->

### Prevención de vulnerabilidades

<!-- TODO: inyección SQL (consultas parametrizadas de JPA), XSS, CSRF,
     validación de entrada con @Valid, CORS -->

---

## Eventos y asincronía

<!-- TODO: qué eventos se publican, quién los escucha, y por qué cada uno
     debe ser asíncrono. Justificar con el caso de uso, no en abstracto. -->

---

## GitHub y gestión del proyecto

### Gestión de tareas

<!-- TODO: cómo usaron issues, labels, milestones y el project board -->

### GitHub Actions

<!-- TODO: qué corre el pipeline, cuándo se dispara y qué bloquea -->

Ver [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de ramas y commits.

---

## Instalación y ejecución

Ver [SETUP.md](SETUP.md).

### Variables de entorno

Ver [.env.example](.env.example).

### Endpoints

La colección completa está en [`postman_collection.json`](postman_collection.json).

<!-- TODO: tabla resumen de los endpoints principales -->

---

## Conclusión

### Logros del proyecto

<!-- TODO -->

### Aprendizajes clave

<!-- TODO: honesto y específico. Qué costó, qué se entendió recién al hacerlo. -->

### Trabajo futuro

<!-- TODO: acá va la reinterpretación histórica de puntos de corte,
     el frontend, el mapa, la app móvil. -->

---

## Apéndices

### Licencia

<!-- TODO: MIT, Apache 2.0, etc. -->

### Referencias

<!-- TODO -->
