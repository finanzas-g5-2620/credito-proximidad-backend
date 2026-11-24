# Crédito Proximidad Backend

Spring Boot REST API for an academic project that models cash sales, commercial credit and collections for neighborhood stores. It manages tenant accounts, products and stock, customer credit terms, checkout, French installment schedules, monthly statements, payments and reporting.

## Stack and structure

Java 21, Spring Boot 4, Spring Security with JWT and BCrypt, Spring Data JPA, PostgreSQL 18, Flyway, springdoc OpenAPI and Maven. In Docker, Nginx serves the Angular UI in Spanish and proxies `/api/v1` to Spring Boot. The API and database stay on the Docker network.

| Path | Responsibility |
| --- | --- |
| `src/main/java/pe/upc/credito/domain` | Business rules and financial calculations |
| `src/main/java/pe/upc/credito/application` | Use cases, services and API request and response models |
| `src/main/java/pe/upc/credito/infrastructure` | REST controllers, security, persistence and startup |
| `src/main/resources` | Configuration, Flyway schema and demo fixtures |
| `src/test`, `scripts` | Backend tests and optional local database fixtures |

## Run locally

Check out the [frontend](https://github.com/finanzas-g5-2620/credito-proximidad-frontend) and backend into sibling directories. From this repository, run:

```sh
docker compose up -d --build --wait
```

Open the [application](http://localhost:4200), [Swagger UI](http://localhost:4200/api/v1/swagger-ui.html) or [health endpoint](http://localhost:4200/api/v1/actuator/health). The local seed password is `password`. Example accounts are `superadmin`, `pepe.admin`, `luis.admin`, `maria.quispe` and `carlos.ruiz`. Demo fixtures cover both stores, past sales, receipts and collection scenarios. Run `docker compose stop` to stop the stack while retaining its volume.

Run `mvn test` with Java 21, or build the backend image to run Maven checks in Docker.

## Deploy

Copy `.env.deploy.example` to `.env.deploy`, fill in the database and JWT secrets, and configure the initial demo or administrator credentials. Run:

```sh
docker compose --env-file .env.deploy -f docker-compose.yml -f docker-compose.deploy.yml up -d --build --wait
```

Serve the loopback frontend through a reverse proxy with HTTPS. See the [deployment guide](docs/deployment.md) for demo settings, clean startup, smoke checks and database backups.

## License

[MIT](LICENSE).
