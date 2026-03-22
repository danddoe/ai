---
name: IAM Module Groovy Gradle
overview: IAM as a dedicated Gradle module (Groovy build scripts) in a Java Spring Boot backend, with Spring Boot DevTools for automatic reload during development.
todos: []
isProject: false
---

# IAM Module – Gradle (Groovy) + Spring Boot with Auto-Reload

## Decisions

- **Gradle**: Use **Groovy** for build scripts: `build.gradle` and `settings.gradle` (not Kotlin DSL).
- **IAM**: Implemented in its **own Gradle module** (e.g. `iam`) within the same repo.
- **Backend**: **Java Spring Boot** for the IAM service.
- **Development**: **Spring Boot DevTools** for automatic restart when classes change.

---

## 1. Project layout (Gradle multi-module, Groovy)

```
c:\project\ai\
├── settings.gradle                 # Include subprojects: iam
├── build.gradle                    # Root: common plugins, dependency management (Groovy)
├── gradle/
│   └── wrapper/                    # gradlew, gradle-wrapper.properties
├── iam/
│   ├── build.gradle                # Spring Boot, dependencies, DevTools (Groovy)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/.../iam/
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/   # Flyway
│   │   └── test/
│   └── ...
└── (optional) erp-api/             # Future ERP module
```

---

## 2. Gradle build files (Groovy syntax)

**Root `settings.gradle`:**

```groovy
rootProject.name = 'erp'   // or 'ai'
include 'iam'
```

**Root `build.gradle`:**

- Apply `io.spring.dependency-management` plugin.
- Use `dependencyManagement { imports { mavenBom(...) } }` for Spring Boot BOM so subprojects get consistent versions.
- Optionally apply `java` and common config to all subprojects.

**iam/build.gradle:**

- Plugins: `java`, `id 'org.springframework.boot'`, `id 'io.spring.dependency-management'`.
- Dependencies in Groovy: `implementation 'org.springframework.boot:spring-boot-starter-web'`, `implementation 'org.springframework.boot:spring-boot-starter-security'`, JPA or JDBC, PostgreSQL driver, Flyway, JWT (e.g. jjwt), and `**developmentOnly 'org.springframework.boot:spring-boot-devtools'`**.
- Java version: `sourceCompatibility = '17'` (or 21) inside a `java { }` block.

All build logic uses **Groovy** (e.g. single-quoted strings, no semicolons, `'org.springframework.boot:spring-boot-starter-web'`).

---

## 3. IAM module structure

Unchanged from prior plan: Spring Boot app under `iam/` with `IamApplication.java`, `config/`, `domain/`, `repository/`, `service/`, `web/`, `security/`, and Flyway migrations in `iam/src/main/resources/db/migration/`. Database schema: tenants, users, tenant_users, roles, permissions, role_permissions, user_roles, refresh_tokens, audit_log (CockroachDB).

---

## 4. Auto-reload (DevTools)

Same as before: `spring-boot-devtools` in the IAM module (`developmentOnly`) gives automatic restart on classpath changes; run `./gradlew :iam:bootRun` and recompile for fast reload during development.

---

## 5. Deliverables summary

- **Root**: `settings.gradle` and `build.gradle` written in **Groovy**; include `iam` module.
- **iam module**: `iam/build.gradle` in **Groovy**; Spring Boot, DevTools, Flyway, JWT; same IAM features and DB schema as previously planned.
- **No Kotlin DSL**: All Gradle files are `.gradle` (Groovy), not `.gradle.kts`.

