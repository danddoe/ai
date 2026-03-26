# global-search (omnibox BFF)

Spring Boot service (default port **8082**) that implements **scatter-gather** global search for the ERP portal.

## Endpoints

- `GET /v1/search/omnibox?q=...&limitPerCategory=12` — requires `Authorization: Bearer <accessToken>`. Runs **in parallel**:
  - IAM: `GET /v1/navigation/search`
  - entity-builder: `GET /v1/tenants/{tenantId}/search/records` (tenant from JWT)
- Response: `{ "navigation": [...], "records": [...], "deepHistory": [...] }`  
  `deepHistory` is always empty in v1 (see below).

## Configuration

| Env / property | Default | Purpose |
|----------------|---------|---------|
| `SERVER_PORT` | 8082 | HTTP port |
| `IAM_URL` | http://localhost:8080 | IAM base URL |
| `ENTITY_BUILDER_URL` | http://localhost:8081 | entity-builder base URL |
| `GLOBAL_SEARCH_UPSTREAM_TIMEOUT_MS` | 2000 | Per-upstream HTTP timeout |
| `JWT_SECRET`, `JWT_ISSUER`, `JWT_AUDIENCE` | same as other services | JWT validation |
| JWT contract doc | — | [`design/JWT_access_token_contract.md`](../design/JWT_access_token_contract.md) |

## Troubleshooting

- **`500` on `/v1/search/omnibox`:** Ensure **global-search** is running (gateway default `http://localhost:8082`). `JWT_SECRET` / issuer / audience must match IAM. IAM and entity-builder must be reachable at `IAM_URL` / `ENTITY_BUILDER_URL`.
- **`500` on `/auth/login` or `/v1/form-layout-templates`:** Those routes hit **IAM** and **entity-builder** directly — not global-search. Check those services are up, DB connectivity (Cockroach for entity-builder), and gateway env vars `IAM_URL` / `ENTITY_BUILDER_URL`.

## Phase 4 (deep text)

Implement `DeepSearchClient` (e.g. ClickHouse or Elasticsearch) and register it as the primary bean instead of `NoOpDeepSearchClient`. Keep responses mapped to the same `OmniboxItem` shape with category `deep_history`.

## Run

```bash
./gradlew :global-search:bootRun --args="--spring.profiles.active=default-bootstrap"
```

Route through **api-gateway** (port 8000): `GLOBAL_SEARCH_URL` is wired in `api-gateway` `application.yml`.
