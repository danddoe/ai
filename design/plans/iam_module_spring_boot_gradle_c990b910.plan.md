---
name: IAM Module Spring Boot Gradle
overview: IAM as a dedicated Gradle module in a Java Spring Boot backend, with Gradle multi-module build and Spring Boot DevTools for automatic reload on class changes during development.
todos: []
isProject: false
---

# IAM Module – Gradle + Spring Boot with Auto-Reload

## Decisions

- **IAM**: Implemented in its **own Gradle module** (e.g. `iam` or `modules/iam`) within the same repo.
- **Build**: **Gradle** (Kotlin DSL or Groovy) for the whole project; root project can host shared config and additional modules (e.g. future ERP modules).
- **Backend**: **Java Spring Boot** for the IAM service and any other backend code.
- **Development**: **Spring Boot DevTools** for automatic restart when classes change (faster dev cycle).

---

## 1. Project layout (Gradle multi-module)

```
c:\project\ai\
├── settings.gradle(.kts)          # Include subprojects: iam, (optional) shared, app
├── build.gradle(.kts)             # Root: common plugins, versions, dependency management
├── gradle/
│   └── libs.versions.toml         # Optional: version catalog
├── iam/                           # IAM module (own directory)
│   ├── build.gradle(.kts)         # Spring Boot plugin, dependencies (web, security, jdbc/jpa, cockroach)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/.../IamApplication.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/  # Flyway migrations
│   │   └── test/
│   └── ...
├── (optional) shared/             # Shared DTOs, JWT contract, if needed later
└── (optional) erp-api/            # Future ERP module
```

- **Root** `build.gradle.kts`: Apply `io.spring.dependency-management` and optionally `org.springframework.boot` only to subprojects that are apps; declare common versions.
- **iam/build.gradle.kts**: Apply `org.springframework.boot`, `java`, dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa` or `spring-boot-starter-jdbc`, CockroachDB driver (PostgreSQL-compatible), Flyway, JWT (e.g. `jjwt`), and **spring-boot-devtools** (with scope `developmentOnly`).

---

## 2. IAM module structure (Spring Boot)

```
iam/
├── build.gradle.kts
└── src/main/
    ├── java/.../iam/
    │   ├── IamApplication.java
    │   ├── config/                # Security, JWT, datasource
    │   ├── domain/ or entity/     # JPA entities: Tenant, User, TenantUser, Role, Permission, etc.
    │   ├── repository/            # Spring Data JPA or JdbcTemplate
    │   ├── service/               # Auth, user, role, tenant_users
    │   ├── web/ or controller/    # REST: auth, users, roles, tenant-users
    │   ├── security/              # JWT filter, tenant context, UserPrincipal
    │   └── migration/             # Optional; or use Flyway only
    └── resources/
        ├── application.yml       # Server port, datasource, JWT, Flyway
        └── db/migration/
            ├── V1__create_tenants.sql
            ├── V2__create_users.sql
            ├── V3__create_tenant_users.sql
            ├── V4__create_roles_permissions.sql
            └── ...
```

- **Database**: Same CockroachDB schema as before (tenants, users, tenant_users, roles, permissions, role_permissions, user_roles, refresh_tokens, audit_log). Flyway scripts in `iam/src/main/resources/db/migration/`.
- **JWT**: Issued and validated inside the IAM module; JWKS or public key for future ERP modules.

---

## 3. Gradle build files (essentials)

**Root `settings.gradle.kts`:**

- `rootProject.name = "erp"` (or "ai")
- `include("iam")` (and later `include("erp-api")` if needed)

**Root `build.gradle.kts`:**

- Plugins: `java`, `io.spring.dependency-management` (and optionally `org.springframework.boot` with `apply false`).
- `dependencyManagement { imports { mavenBom(SpringBoot.BOM) } }` so subprojects get consistent versions.
- Subproject application of Boot can live in the `iam` module.

**iam/build.gradle.kts:**

- `plugins { java, id("org.springframework.boot"), id("io.spring.dependency-management") }`
- Dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa` (or `-jdbc`), `org.postgresql` (CockroachDB), `flyway-core` / `flyway-database-postgresql`, `jjwt-api` + `jjwt-impl` + `jjwt-jackson`, **developmentOnly("org.springframework.boot:spring-boot-devtools")**
- `java { sourceCompatibility = JavaVersion.VERSION_17 }` (or 21)

---

## 4. Auto-reload (Spring Boot DevTools)

- **spring-boot-devtools** in the IAM module (`developmentOnly`) provides:
  - **Automatic restart** when classpath changes (e.g. recompiled classes). Triggered by saving a file if your IDE compiles on save, or after `./gradlew :iam:bootRun` and a rebuild.
  - **Restart vs reload**: DevTools uses two classloaders; only “restart” classloader is restarted, so startup is faster than a full process restart.
- **Optional**: In `application.yml` under `spring.devtools.restart`, you can set `enabled: true` (default) and `additional-paths` if you want to watch extra directories.
- **Development workflow**: Run `./gradlew :iam:bootRun` (or use IDE run); change a class and let the project recompile (e.g. Build → Recompile in IDE, or Gradle build); DevTools will restart the app so changes are picked up without manually stopping/starting.

---

## 5. Implementation outline


| Step | Task                                                                                                                                                                  |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1    | Add root Gradle files: `settings.gradle.kts`, root `build.gradle.kts` (dependency management, no Boot application at root).                                           |
| 2    | Create **iam** module: `iam/build.gradle.kts` with Spring Boot, DevTools, Web, Security, JPA/JDBC, PostgreSQL driver, Flyway, JWT.                                    |
| 3    | Add Flyway migrations in **iam** for CockroachDB (tenants → users → tenant_users → roles → permissions → role_permissions → user_roles → refresh_tokens → audit_log). |
| 4    | Implement entities, repositories, services, and REST controllers in **iam** (auth, tenant_users, users, roles).                                                       |
| 5    | Configure JWT issuance and validation and optional JWKS endpoint in **iam**.                                                                                          |
| 6    | Document JWT claims and how other modules (e.g. future ERP) will consume the token.                                                                                   |


---

## 6. Deliverables summary

- **Root**: Gradle multi-project with `settings.gradle.kts` and root `build.gradle.kts`; **iam** included as a module.
- **iam module**: Standalone Spring Boot app in its own directory; Flyway migrations; domain, repositories, services, REST API; JWT + DevTools.
- **Dev experience**: Run `./gradlew :iam:bootRun`; edit classes and recompile for automatic reload without full restart.

