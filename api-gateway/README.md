# API Gateway (Spring Cloud Gateway)

BFF-style reverse proxy: single entry point on port **8000**, routing to **IAM** (8080) and **entity-builder** (8081).

## Prerequisites

- Java 17
- IAM and entity-builder running (default URLs below)

## Run

```powershell
# From repo root, after CockroachDB + migrations are ready:
.\gradlew :iam:bootRun
.\gradlew :entity-builder:bootRun
.\gradlew :api-gateway:bootRun
```

Or:

```powershell
cd api-gateway
..\gradlew bootRun
```

## Configuration

| Env var | Default | Purpose |
|---------|---------|---------|
| `GATEWAY_PORT` | `8000` | Listen port |
| `IAM_URL` | `http://localhost:8080` | IAM upstream |
| `ENTITY_BUILDER_URL` | `http://localhost:8081` | Entity-builder upstream |

## Routing

| Path | Upstream |
|------|----------|
| `/auth`, `/auth/**` | IAM |
| `/.well-known/**` | IAM |
| `/v1/entities`, `/v1/entities/**` | entity-builder |
| `/v1/entity-relationships`, `/v1/entity-relationships/**` | entity-builder |
| `/v1/tenants/*/entities`, `/v1/tenants/*/entities/**` | entity-builder |
| `/v1/tenants/*/records`, `/v1/tenants/*/records/**` | entity-builder |
| `/v1/**` (everything else) | IAM |

JWT validation remains in IAM and entity-builder; the gateway forwards headers (including `Authorization`) as-is.

## vs Kong

This module is a **native JVM** alternative to `kong/` (Docker). Use one or the other, not both on the same port.
