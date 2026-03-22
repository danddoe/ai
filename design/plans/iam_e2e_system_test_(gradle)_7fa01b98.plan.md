---
name: IAM E2E System Test (Gradle)
overview: Add a rerunnable end-to-end IAM system test that runs via `gradle build`, using Testcontainers to start a disposable CockroachDB, booting the Spring Boot IAM module on a random port, bootstrapping a tenant/user/membership, and exercising login/refresh/logout.
todos:
  - id: gradle-testcontainers
    content: Add Testcontainers dependencies to iam/build.gradle
    status: completed
  - id: e2e-test-class
    content: Create IamAuthE2ETest that boots app, bootstraps data, and validates login/refresh/logout flow
    status: completed
isProject: false
---

# IAM rerunnable E2E system test (Gradle build)

## Goal

Make `./gradlew :iam:build` run a **repeatable end-to-end test** that:

- Starts an ephemeral **CockroachDB** via **Testcontainers**
- Boots the **IAM Spring Boot app** on a random port
- Bootstraps **tenant + user + tenant_users membership (+ optional role assignment)**
- Exercises:
  - `POST /auth/login`
  - `POST /auth/refresh`
  - `POST /auth/logout`

## Key design

- **Database provisioning**: Testcontainers runs `cockroachdb/cockroach:`* with `start-single-node --insecure`, exposing SQL on a random local port.
- **Spring wiring**: Use `@DynamicPropertySource` to inject `spring.datasource.url/username/password` so Flyway migrations run automatically against the container.
- **System test style**: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` (or `WebTestClient`) to call the live HTTP endpoints.
- **Rerunnability**: Each test bootstraps its own data (unique tenant slug + email), so tests can rerun without relying on ordering or external state.

## Files to add/change

- Update Gradle deps in `[c:\project\ai\iam\build.gradle](c:\project\ai\iam\build.gradle)`:
  - Add Testcontainers dependencies:
    - `testImplementation 'org.testcontainers:junit-jupiter'`
    - `testImplementation 'org.testcontainers:postgresql'` (Cockroach speaks Postgres wire protocol)
- Add a system test class:
  - `[c:\project\ai\iam\src\test\java\com\erp\iam\e2e\IamAuthE2ETest.java](c:\project\ai\iam\src\test\java\com\erp\iam\e2e\IamAuthE2ETest.java)`
    - Start container (Cockroach image)
    - Provide properties via `@DynamicPropertySource`
    - Insert tenant/user/tenant_users (and optionally `ADMIN` role assignment) via `JdbcTemplate`
    - Call endpoints and assert:
      - login returns access+refresh
      - refresh returns new access+refresh
      - logout revokes refresh token; subsequent refresh fails with 401

## Test data bootstrap approach

- Use `JdbcTemplate` to insert:
  - `tenants` row (unique `slug`)
  - `users` row with BCrypt-hashed password
  - `tenant_users` row linking them
  - (Optional) create `roles` + `user_roles` so `roles` claim is non-empty

## Execution

- Tests run under the default `test` task, so:
  - `./gradlew :iam:test`
  - `./gradlew :iam:build`

## Notes / constraints

- Requires **Docker** available on the machine running tests (Testcontainers).
- Uses insecure single-node CockroachDB for tests only.

