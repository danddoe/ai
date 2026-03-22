---
name: Kong Gateway BFF Setup
overview: Add Kong Gateway as a BFF (Backend for Frontend) API gateway that routes requests from a single entry point to the IAM (port 8080) and entity-builder (port 8081) services, with declarative configuration for local development.
todos: []
isProject: false
---

# Kong Gateway BFF Setup Plan

## Current Architecture

- **IAM** (`iam`): port 8080 — auth (`/auth/`*), JWKS (`/.well-known/jwks.json`), tenants, users, roles, permissions
- **Entity-builder**: port 8081 — entities, records, form layouts, relationships
- Both use JWT validation; backends already enforce auth

## Approach

Use **Kong Gateway** in **DB-less mode** with a declarative `kong.yml` so config lives in Git and no database is required. Kong will run in Docker and route to host services via `host.docker.internal`.

## Implementation

### 1. Create Kong directory structure

```
kong/
  kong.yml           # Declarative config (services, routes)
  docker-compose.yml # Kong container
  README.md          # Usage and routing reference
```

### 2. Declarative config (`kong/kong.yml`)

Define two upstream services and path-based routing:


| Path pattern               | Target service | Backend                   |
| -------------------------- | -------------- | ------------------------- |
| `/auth`                    | iam            | host.docker.internal:8080 |
| `/.well-known`             | iam            | host.docker.internal:8080 |
| `/v1/entities`             | entity-builder | host.docker.internal:8081 |
| `/v1/entity-relationships` | entity-builder | host.docker.internal:8081 |
| `/v1/tenants/*/entities`   | entity-builder | host.docker.internal:8081 |
| `/v1/tenants/*/records`    | entity-builder | host.docker.internal:8081 |
| `/v1/*` (remaining)        | iam            | host.docker.internal:8080 |


Use `_format_version: "3.0"` and order routes so more specific paths match first.

### 3. Docker Compose (`kong/docker-compose.yml`)

- Image: `kong/kong-gateway:3.13` (or latest OSS-compatible)
- DB-less: `KONG_DATABASE=off`, `KONG_DECLARATIVE_CONFIG=/kong/declarative/kong.yml`
- Ports: `8000` (proxy), `8001` (admin API, optional for db-less)
- Mount `kong.yml` into the container
- Use `host.docker.internal` for upstream URLs (works on Windows/Mac Docker Desktop)

### 4. Optional: Strip path vs preserve path

Kong can forward `/v1/entities` as-is to the backend. No path rewriting needed since both services use the same path structure.

### 5. Startup scripts (PowerShell)

- `kong/start.ps1` — `docker compose up -d`
- `kong/stop.ps1` — `docker compose down`

### 6. Documentation

- How to run: start CockroachDB, IAM, entity-builder, then Kong
- Frontend base URL: `http://localhost:8000`
- Example: `curl http://localhost:8000/auth/login` → IAM

## Key Files to Create

- [kong/kong.yml](kong/kong.yml) — services and routes
- [kong/docker-compose.yml](kong/docker-compose.yml) — Kong container
- [kong/README.md](kong/README.md) — setup and usage
- [kong/start.ps1](kong/start.ps1), [kong/stop.ps1](kong/stop.ps1) — convenience scripts

## Notes

- **JWT**: Backends already validate JWT; Kong can optionally add JWT plugin later for gateway-level validation.
- **CORS**: If the frontend is on a different origin, add CORS plugin to Kong or keep CORS in backends.
- **Kong OSS vs Enterprise**: Use `kong/kong-gateway` image; it runs without a license in free mode.

