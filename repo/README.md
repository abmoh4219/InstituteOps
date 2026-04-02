# InstituteOps

Docker-first setup for the full InstituteOps stack (app + MySQL + automated tests).

## Start app + database + automated tests

```bash
docker compose up --build
```

What this starts:

- `mysql`: MySQL 8.4
- `app`: Spring Boot application at `http://localhost:8080`
- `tests`: `run_test.sh` (executes `./mvnw -B verify -Pintegration-tests`)

`tests` service logs stream directly in terminal during `docker compose up` (Surefire/Failsafe output is visible).

## Reset database and rerun

```bash
docker compose down -v
docker compose up --build
```

## Test log viewing

If you want to re-follow only test output:

```bash
docker compose logs -f tests
```

## URLs

- App login page: `http://localhost:8080/login`
- API example: `http://localhost:8080/api/internal/ping`

## Login credentials

- `sysadmin` / `Admin@123`
- `registrar` / `Registrar@123`
- `instructor` / `Instructor@123`
- `inventory` / `Inventory@123`
- `approver` / `Approver@123`
- `store` / `Store@123`
- `student1` / `Student@123`

## Internal API client

- Key: `local-sync-client`
- Secret: `internal-secret`
