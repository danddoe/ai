---
name: IAM Module with Gradle
overview: IAM as a dedicated Gradle subproject in its own directory, with a Gradle-based multi-module build so the root builds the whole project and the IAM module can be developed and tested independently.
todos: []
isProject: false
---

# IAM for Multi-Tenant ERP — Gradle Multi-Module Layout

## Scope and assumptions

- **Build**: **Gradle** (root and subprojects). Language: **Java** (or Kotlin if you prefer; the layout is the same).
- **IAM**: Lives in its **own directory/module** so it can be built, tested, and deployed independently and stay isolated from future ERP modules.
- **Database**: CockroachDB; schema unchanged (tenants, users, tenant_users, roles, permissions, role_permissions, user_roles, refresh_tokens, audit_log). JWT and multi-tenant model unchanged.

---

## 1. Project structure (Gradle multi-module)

IAM is a **Gradle subproject** under its own directory. Root aggregates modules and can add more (e.g. `erp-core`, `inventory`) later.

```
c:\project\ai\
├── build.gradle.kts              # Root: aggregates subprojects, shared config
├── settings.gradle.kts           # Include 'iam' (and future modules)
├── gradle/
│   └── libs.versions.toml        # Optional: version catalog
├── iam/                          # IAM module (own directory)
│   ├── build.gradle.kts          # IAM-specific deps and config
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/.../iam/
│       │   │       ├── IamApplication.java
│       │   │       ├── config/
│       │   │       ├── db/       # DataSource, migrations (Flyway)
│       │   │       ├── domain/   # Entities (tenant, user, tenant_user, role, ...)
│       │   │       ├── repository/
│       │   │       ├── service/
│       │   │       ├── web/      # Auth, users, roles, tenant-users APIs
│       │   │       └── security/ # JWT issuer, validation, tenant context
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/ # Flyway SQL (V1__iam_tables.sql, ...)
│       └── test/
│           └── java/
└── (future modules, e.g. erp-core/)
```

- **Root** `settings.gradle.kts`: `rootProject.name = "erp"` and `include("iam")` (and later `include("erp-core")` etc.).
- **Root** `build.gradle.kts`: Apply common plugins (Java, idea) to all subprojects; optional shared versions and conventions.
- **iam** `build.gradle.kts`: Apply `application` or `spring-boot` plugin, declare dependencies (Spring Boot Web, Security, JPA or JdbcTemplate, Flyway, PostgreSQL/CockroachDB driver, JWT libs). Main class points to IAM’s `IamApplication`.

---

## 2. Gradle build files (essentials)

### Root: `settings.gradle.kts`

```kotlin
rootProject.name = "erp"
include("iam")
```

### Root: `build.gradle.kts`

- Plugins: `java`, `idea` (and optionally `io.spring.dependency-management` at root for BOM).
- Subproject convention: e.g. set Java toolchain (17) and group/version for all subprojects.
- No application plugin at root if each runnable app is in a module (e.g. `iam`).

### IAM module: `iam/build.gradle.kts`

- Plugins: `java`, `org.springframework.boot`, `io.spring.dependency-management` (or use root BOM).
- Dependencies: Spring Boot Starter Web, Spring Boot Starter Security, Spring Boot Starter Jdbc (or Data), Flyway, P