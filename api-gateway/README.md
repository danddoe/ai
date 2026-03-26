# API Gateway (Spring Cloud Gateway)

BFF-style reverse proxy: single entry point on port **8000**, routing to **IAM** (8080), **entity-builder** (8081), and **global-search** (8082).

## Prerequisites

- Java 17
- IAM and entity-builder running (default URLs below)

## Run

```powershell
# From repo root, after CockroachDB + migrations are ready:
.\gradlew :iam:bootRun
.\gradlew :core-service:bootRun
.\gradlew :entity-builder:bootRun
.\gradlew :global-search:bootRun
.\gradlew :api-gateway:bootRun
```

Or:

```powershell
cd api-gateway
..\gradlew bootRun
```

## CORS

Global CORS allows `http://localhost:*` and `http://127.0.0.1:*` with **credentials** so browser SPAs (e.g. Vite on 5173) can call the gateway with cookies. Adjust in `application.yml` for production origins.

## Configuration

| Env var | Default | Purpose |
|---------|---------|---------|
| `GATEWAY_PORT` | `8000` | Listen port |
| `IAM_URL` | `http://localhost:8080` | IAM upstream |
| `ENTITY_BUILDER_URL` | `http://localhost:8081` | Entity-builder upstream |
| `CORE_SERVICE_URL` | `http://localhost:8084` | Core-service (master data + portal bootstrap) |
| `GATEWAY_TRUST_SECRET` | _(empty)_ | Shared with core-service `GATEWAY_TRUST_SECRET`: when set, gateway resolves `Host` → `X-Resolved-*` + `X-Gateway-Trusted` for downstream |
| `PORTAL_HOST_CACHE_TTL_SECONDS` | `60` | TTL for hostname → bootstrap cache in the gateway |

**Portal / CNAME (approach A):** JWT still carries only `tenant_id`. Custom host defaults come from core-service `portal_host_bindings` via `GET /v1/portal/bootstrap`. When `GATEWAY_TRUST_SECRET` is set on **both** gateway and core-service, authenticated calls through the gateway also receive trusted `X-Resolved-Tenant-Id` (and optional company/BU); core-service rejects JWTs whose tenant does not match.

## Routing

| Path | Upstream |
|------|----------|
| `/auth`, `/auth/**` | IAM |
| `/.well-known/**` | IAM |
| `/v1/portal`, `/v1/portal/**` | core-service |
| `/v1/countries`, `/v1/countries/**` | core-service |
| `/v1/tenants/*/companies`, `/v1/tenants/*/companies/**` | core-service |
| `/v1/tenants/*/portal-host-bindings`, `/v1/tenants/*/portal-host-bindings/**` | core-service |
| `/v1/tenants/*/business-units`, `/v1/tenants/*/business-units/**` | core-service |
| `/v1/tenants/*/regions`, `/v1/tenants/*/regions/**` | core-service |
| `/v1/tenants/*/locations`, `/v1/tenants/*/locations/**` | core-service |
| `/v1/tenants/*/properties`, `/v1/tenants/*/properties/**` | core-service |
| `/v1/tenants/*/property-units`, `/v1/tenants/*/property-units/**` | core-service |
| `/v1/entities`, `/v1/entities/**` | entity-builder |
| `/v1/entity-status`, `/v1/entity-status/**` | entity-builder (status label i18n) |
| `/v1/entity-relationships`, `/v1/entity-relationships/**` | entity-builder |
| `/v1/tenants/*/entities`, `/v1/tenants/*/entities/**` | entity-builder |
| `/v1/tenants/*/records`, `/v1/tenants/*/records/**` | entity-builder |
| `/v1/tenants/*/search`, `/v1/tenants/*/search/**` | entity-builder |
| `/v1/search`, `/v1/search/**` | global-search |
| `/v1/**` (everything else) | IAM |

JWT validation remains in IAM and entity-builder; the gateway forwards headers (including `Authorization`) as-is.

## Locale / i18n headers

The portal sends `Accept-Language` and **`X-User-Locale`** (explicit override) on API calls. Spring Cloud Gateway forwards incoming request headers to upstream routes by default; custom filters here do **not** strip these. No gateway-side locale negotiation is applied—downstream services interpret headers.

**Resolution order** (application convention): `X-User-Locale` (if present) → future user/tenant preference from IAM → `Accept-Language` → fallback `en`.

## vs Kong

This module is a **native JVM** alternative to `kong/` (Docker). Use one or the other, not both on the same port.
