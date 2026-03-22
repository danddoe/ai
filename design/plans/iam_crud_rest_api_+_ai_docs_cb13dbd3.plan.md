---
name: IAM CRUD REST API + AI docs
overview: Add a versioned REST API to create/update/delete IAM data (tenants, users, memberships, roles, permissions, assignments) with cross-tenant admin support. Ship OpenAPI + an additional machine-readable agent guide designed for LLM tool use (stable schemas, examples, error shapes, workflows).
todos:
  - id: define-permissions
    content: Decide and add superadmin + tenant-level permission codes for tenants/permissions/admin operations (new Flyway migration + bootstrap role if desired).
    status: completed
  - id: crud-controllers
    content: Implement v1 controllers + DTOs for tenants/users/memberships/roles/permissions and join-table assignment endpoints.
    status: completed
  - id: authz-and-validation
    content: Add @PreAuthorize checks, input validation, and transactional service methods; enforce system role constraints and tenant scoping rules.
    status: completed
  - id: ai-consumable-docs
    content: Add springdoc OpenAPI with examples and stable schemas; implement /v1/ai/guide JSON workflow descriptor; update iam/README.md.
    status: completed
  - id: tests
    content: Add focused tests for CRUD flows, authz, and OpenAPI/ai guide endpoints.
    status: completed
isProject: false
---

## Goals

- Provide **CRUD** for IAM data: `Tenant`, `User`, `TenantUser` (membership), `Role`, `Permission`, `UserRole`, `RolePermission`.
- Support **cross-tenant administration** by putting `tenantId` in routes and enforcing a privileged permission/role.
- Produce **AI-consumable docs**: OpenAPI with strong schemas + examples, plus a small JSON “agent guide” that describes workflows, required permissions, and safe usage patterns.

## Current state (what exists)

- Auth is already implemented in `[iam/src/main/java/com/erp/iam/web/AuthController.java](iam/src/main/java/com/erp/iam/web/AuthController.java)` (`/auth/login|refresh|logout`).
- Tenant context comes from JWT claim `tenant_id` via `[iam/src/main/java/com/erp/iam/security/TenantPrincipal.java](iam/src/main/java/com/erp/iam/security/TenantPrincipal.java)`.
- Seeded permissions currently include:
  - `iam:users:read|write`, `iam:roles:read|write`, `iam:tenant_users:read|write` in `[iam/src/main/resources/db/migration/V7__seed_permissions.sql](iam/src/main/resources/db/migration/V7__seed_permissions.sql)`.

## API surface (v1)

All endpoints below are under `**/v1`**.

### Tenants (cross-tenant admin)

- `GET /v1/tenants?status=&q=&page=&pageSize=`
- `POST /v1/tenants` (create tenant)
- `GET /v1/tenants/{tenantId}`
- `PATCH /v1/tenants/{tenantId}` (name, slug, status)
- `DELETE /v1/tenants/{tenantId}` (hard delete; cascades)

### Users (global identity)

- `GET /v1/users?email=&status=&q=&page=&pageSize=`
- `POST /v1/users` (create user with password or invite workflow)
- `GET /v1/users/{userId}`
- `PATCH /v1/users/{userId}` (displayName, status, email, password reset)
- `DELETE /v1/users/{userId}`

### Tenant memberships (tenant-scoped by path)

- `GET /v1/tenants/{tenantId}/members?status=&page=&pageSize=`
- `POST /v1/tenants/{tenantId}/members` (add existing user by `userId` or create+add by email)
- `GET /v1/tenants/{tenantId}/members/{userId}`
- `PATCH /v1/tenants/{tenantId}/members/{userId}` (status, displayName)
- `DELETE /v1/tenants/{tenantId}/members/{userId}`

### Roles (tenant-scoped by path)

- `GET /v1/tenants/{tenantId}/roles?page=&pageSize=`
- `POST /v1/tenants/{tenantId}/roles`
- `GET /v1/tenants/{tenantId}/roles/{roleId}`
- `PATCH /v1/tenants/{tenantId}/roles/{roleId}`
- `DELETE /v1/tenants/{tenantId}/roles/{roleId}` (block if `is_system=true` unless superadmin)

### Permissions (global registry)

- `GET /v1/permissions?q=&page=&pageSize=`
- `POST /v1/permissions` (optional; if you want permissions immutable, keep read-only)
- `GET /v1/permissions/{permissionId}`
- `PATCH /v1/permissions/{permissionId}`
- `DELETE /v1/permissions/{permissionId}`

### Assignments (join tables)

- `PUT /v1/tenants/{tenantId}/roles/{roleId}/permissions` (replace set)
- `GET /v1/tenants/{tenantId}/roles/{roleId}/permissions`
- `PUT /v1/tenants/{tenantId}/users/{userId}/roles` (replace set)
- `GET /v1/tenants/{tenantId}/users/{userId}/roles`

## Authorization model

- Introduce one **superadmin** gate for cross-tenant/registry operations (e.g. `iam:tenants:admin`, `iam:permissions:admin`), while retaining your existing tenant-level permissions.
- Enforce with Spring Security method guards (`@PreAuthorize`) on controllers/services.
- Add missing permissions to seed file (new Flyway migration) and optionally create a `SUPERADMIN` system role for bootstrap.

## Error and response conventions (LLM-friendly)

- Standard JSON error envelope everywhere, e.g.:
  - `{ "error": { "code": "not_found", "message": "User not found", "details": {...}, "requestId": "..." } }`
- Consistent pagination shape for list endpoints:
  - `{ "items": [...], "page": 1, "pageSize": 50, "total": 123 }`
- Prefer **PATCH** for partial updates, **PUT** for “replace set” assignment endpoints.
- Idempotency:
  - For create endpoints that may be called by agents, support `Idempotency-Key` header (optional) or document safe retry semantics.

## “AI consumption” documentation deliverables

- **OpenAPI 3.x** served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` (via springdoc).
- Rich examples in schema/operations, strong `operationId` naming, explicit error responses.
- Add `GET /v1/ai/guide` returning a small **machine-readable JSON** describing:
  - Base URL, auth scheme, required headers
  - Allowed workflows (create tenant → create user → add membership → assign roles → assign permissions)
  - Permission requirements per workflow
  - Safety rules (never log tokens, avoid deleting system roles, etc.)
  - Pointers to the OpenAPI document URL(s)

## Implementation approach (code structure)

- Add controllers under `com.erp.iam.web.v1`:
  - `TenantsController`, `UsersController`, `TenantMembersController`, `RolesController`, `PermissionsController`, `RolePermissionsController`, `UserRolesController`, `AiGuideController`.
- Add DTOs under `com.erp.iam.web.v1.dto` to avoid leaking JPA entities directly.
- Add service layer (`com.erp.iam.service.*`) methods for transactional writes and validation.
- Reuse existing repositories; add new query methods as needed.
- Add a global exception handler (`@RestControllerAdvice`) for consistent errors.

## Files you’ll likely change/add

- Dependencies/docs:
  - `[iam/build.gradle](iam/build.gradle)` (add springdoc-openapi)
  - `[iam/README.md](iam/README.md)` (document new endpoints + docs URLs)
- New controllers/DTOs/services:
  - `iam/src/main/java/com/erp/iam/web/v1/`**
  - `iam/src/main/java/com/erp/iam/service/IamAdminService.java` (or split services)
  - `iam/src/main/java/com/erp/iam/web/ErrorHandlingAdvice.java`
- New Flyway migration(s):
  - `iam/src/main/resources/db/migration/V8__seed_admin_permissions.sql` (and possibly a system role bootstrap adjustment)

## Test plan

- Add a small set of MVC tests (or e2e tests similar to your existing ones) that:
  - Create tenant, create user, add member, create role, attach permissions, assign role to user
  - Verify auth/permissions: forbidden without required permission, allowed with it
  - Verify OpenAPI endpoint reachable and contains expected paths/operationIds

