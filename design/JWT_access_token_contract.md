# JWT access token contract (ERP IAM)

Single reference for **HMAC-signed** access and refresh tokens issued by **IAM** and validated by **resource servers** (entity-builder, global-search, etc.).

## Algorithm and headers

| Item | Value |
|------|--------|
| Algorithm | **HS256** (HMAC SHA-256) |
| Signing key | Raw bytes of `JWT_SECRET` string (UTF-8), via JJWT `Keys.hmacShaKeyFor` (requires ≥ 256 bits of secret material for HS256) |

## Environment variables (all services must align)

| Variable | Required | Default (dev only) | Purpose |
|----------|----------|--------------------|---------|
| `JWT_SECRET` | Strongly recommended in prod | `erp-iam-dev-secret-key-at-least-256-bits-long-for-hs256` | Shared HMAC secret for sign + verify |
| `JWT_ISSUER` | Optional | `erp-iam` | `iss` claim; parser **must** require this issuer |
| `JWT_AUDIENCE` | Optional | `erp-api` | `aud` claim (single audience); parser **must** require this audience |

Spring apps map issuer/audience via `app.jwt.issuer` / `app.jwt.audience` (defaults above), typically fed from `JWT_ISSUER` / `JWT_AUDIENCE` in deployment config.

**Production:** Set `JWT_SECRET` to a cryptographically random value (≥ 32 bytes as UTF-8 string); do not rely on the dev default.

## Standard claims (JJWT / JWT registered names)

| Claim | Access token | Refresh token | Format / notes |
|-------|--------------|---------------|----------------|
| `sub` | Yes | Yes | String UUID of **user** (`userId`) |
| `iss` | Yes | Yes | Issuer (default `erp-iam`) |
| `aud` | Yes | Yes | Audience (default `erp-api`) |
| `iat` | Yes | Yes | Issued-at |
| `exp` | Yes | Yes | Expiration |

## Custom claims

| Claim | Access | Refresh | Type | Notes |
|-------|--------|---------|------|-------|
| `tenant_id` | Yes | Yes | String UUID | Active tenant for the request |
| `email` | Yes | No | String | User email |
| `roles` | Yes | No | JSON array of strings | Role names; may be **empty** `[]`; may be **omitted** in edge tooling — resource servers must tolerate **missing** or **empty** without throwing |
| `permissions` | Yes | No | JSON array of strings | Permission codes (e.g. `entity_builder:schema:read`); same null-safety as `roles` |
| `jti` | No | Yes | String UUID | Refresh token identifier |
| `type` | No | Yes | String `"refresh"` | Distinguishes refresh from access |

## Resource server expectations

1. Reject tokens with wrong signature, `iss`, `aud`, or expired `exp`.
2. **Access** API requests use the **access** token (no `type=refresh`, or treat refresh as non-access per service policy).
3. Parsing **must not** assume `roles` / `permissions` are non-null JSON arrays; normalize missing/`null` to empty lists before building `SimpleGrantedAuthority` or similar.
4. `sub` and `tenant_id` are required for access tokens in APIs that are tenant-scoped; reject or map to 401 if missing.

## TCK (Technology Compatibility Kit)

Module [`jwt-contract-tck`](../jwt-contract-tck) provides:

- [`JwtContractConstants`](../jwt-contract-tck/src/main/java/com/erp/jwt/tck/JwtContractConstants.java) — canonical default secret string and issuer/audience literals (must match IAM defaults).
- [`JwtTckTokenFactory`](../jwt-contract-tck/src/main/java/com/erp/jwt/tck/JwtTckTokenFactory.java) — mints tokens using the same shape as IAM for tests.

Each Spring service that validates JWTs includes a **`JwtAccessTokenContractTest`** that:

1. Assumes `JWT_SECRET` is unset, blank, or equal to the dev default (skips otherwise to avoid false failures in custom envs).
2. Parses a TCK-minted access token with that service’s `JwtService`.

Run contract tests:

```bash
./gradlew :jwt-contract-tck:test :iam:test --tests '*JwtAccessTokenContractTest*' :entity-builder:test --tests '*JwtAccessTokenContractTest*' :global-search:test --tests '*JwtAccessTokenContractTest*'
```

## Related code

- Issuer: [`iam` `JwtService#createAccessToken`](../iam/src/main/java/com/erp/iam/service/JwtService.java)
- Parsers: `JwtService` in **entity-builder**, **global-search** (and IAM for refresh/login flows)
