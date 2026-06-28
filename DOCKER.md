# Docker Setup

This project can be run on another laptop with Docker Compose. Docker will build the Spring Boot API image and start the required PostgreSQL and Redis containers.

## Requirements

Install these first:

- Git
- Docker Desktop, or Docker Engine with Docker Compose

Verify Docker is working:

```sh
docker --version
docker compose version
```

## Run On A New Laptop

Clone the GitHub repository:

```sh
git clone <your-github-repository-url>
cd <repository-folder>
```

Create the local environment file:

```sh
cp .env.example .env
```

Update `.env` if needed. For local testing, the default values are enough. Do not commit `.env` to GitHub.

Start the app, PostgreSQL, and Redis:

```sh
docker compose up --build
```

The API will be available at:

```text
http://localhost:8080
```

If you changed `APP_PORT` in `.env`, use that port instead.

## Useful Commands

Run containers in the background:

```sh
docker compose up --build -d
```

View logs:

```sh
docker compose logs -f
```

Stop containers:

```sh
docker compose down
```

Stop containers and remove the PostgreSQL data volume:

```sh
docker compose down -v
```

Rebuild after pulling new code:

```sh
git pull
docker compose up --build
```

## What Docker Compose Starts

- `app`: Spring Boot API built from the local `Dockerfile`
- `postgres`: PostgreSQL database
- `redis`: Redis cache/session service

The app container connects to PostgreSQL and Redis using Docker service names, so your friend does not need to install PostgreSQL or Redis directly on their laptop.

## Build Only

Build only the app Docker image:

```sh
docker build -t tribeo-app:latest .
```

Run the full project with Compose instead of `docker run`, because the app needs PostgreSQL and Redis.

## Troubleshooting

### Port already in use

If port `8080`, `5432`, or `6379` is already used on the laptop, change these values in `.env`:

```env
APP_PORT=8081
POSTGRES_PORT=5433
REDIS_PORT=6380
```

Then run:

```sh
docker compose up --build
```

### PostgreSQL password authentication failed

If the app logs show:

```text
FATAL: password authentication failed for user "tribeo"
```

PostgreSQL is probably using an existing Docker volume that was initialized with older credentials. Changing `.env` later does not update credentials inside an existing Postgres data directory.

For local development where you do not need to keep database data, recreate the volume:

```sh
docker compose down -v
docker compose up --build
```

If you need to keep the existing data, set `POSTGRES_USER` and `POSTGRES_PASSWORD` in `.env` back to the credentials used when the volume was first created, or change the password inside Postgres manually.

### Docker build fails while downloading dependencies

Check the internet connection and run again:

```sh
docker compose up --build
```

The first build can take a few minutes because Maven dependencies are downloaded inside Docker.

### App starts before database is ready

The Compose file includes health checks for PostgreSQL and Redis. If startup still fails, stop and start again:

```sh
docker compose down
docker compose up --build
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
