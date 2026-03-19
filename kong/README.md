# Kong Gateway BFF

Kong Gateway acts as a BFF (Backend for Frontend) API gateway, routing requests from a single entry point to IAM and entity-builder services.

**Alternative (no Docker):** the **`api-gateway`** module uses Spring Cloud Gateway on the JVM — see [../api-gateway/README.md](../api-gateway/README.md).

## Prerequisites

- Docker and Docker Compose
- IAM running on port 8080
- Entity-builder running on port 8081
- CockroachDB (required by the backend services)

## Quick Start

```powershell
# From project root, start backends first:
.\cockroachdb\local\start-single-node.ps1
.\gradlew :iam:bootRun          # in another terminal
.\gradlew :entity-builder:bootRun  # in another terminal

# Then start Kong:
cd kong
.\start.ps1
```

## Usage

- **Frontend base URL**: `http://localhost:8000`
- **Example**: `curl http://localhost:8000/auth/login` → proxied to IAM
- **Example**: `curl http://localhost:8000/v1/entities` → proxied to entity-builder

## Routing

| Path | Target |
|------|--------|
| `/auth` | IAM (login, refresh, logout) |
| `/.well-known` | IAM (JWKS) |
| `/v1/entities` | entity-builder |
| `/v1/entity-relationships` | entity-builder |
| `/v1/tenants/{id}/entities/*` | entity-builder |
| `/v1/tenants/{id}/records/*` | entity-builder |
| `/v1/*` (remaining) | IAM (tenants, users, roles, permissions) |

## Commands

- **Start**: `.\start.ps1`
- **Stop**: `.\stop.ps1`

## Configuration

- `kong.yml` — declarative config (services, routes)
- `docker-compose.yml` — Kong container (DB-less mode)
