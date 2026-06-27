# Docker Deployment

This project includes a multi-stage Docker build for the Spring Boot API and a Docker Compose setup with PostgreSQL.

## Local Run

Copy the example environment file and fill in real secrets:

```sh
cp .env.example .env
```

Start the app and database:

```sh
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

Stop the containers:

```sh
docker compose down
```

Stop the containers and remove the database volume:

```sh
docker compose down -v
```

## Build Only

```sh
docker build -t tribeo-app:latest .
```

## Production Notes

- Set strong values for `POSTGRES_PASSWORD`, `SPRING_SECURITY_USER_PASSWORD`, and `SPRING_APP_JWT_SECRET`.
- `SPRING_APP_JWT_SECRET` must be Base64 encoded. Generate one with:

```sh
openssl rand -base64 32
```

- Keep `.env` private. It is intentionally ignored by Git.
- Use `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` or a migration tool for mature production deployments.
- Set `FRONTEND_URL` to the deployed frontend origin so CORS and redirects use the right host.
