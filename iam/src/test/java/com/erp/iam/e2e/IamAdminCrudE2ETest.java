package com.erp.iam.e2e;

import com.erp.iam.web.dto.LoginRequest;
import com.erp.iam.web.dto.TokenResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IamAdminCrudE2ETest extends AbstractIamE2ETest {

    @Test
    void endToEnd_adminCrud_and_docsEndpoints() {
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        // Bootstrap a "superadmin" user in tenantA with iam:security:admin + iam:tenants:admin + iam:permissions:admin.
        UUID tenantAId = UUID.randomUUID();
        String tenantASlug = "t-" + tenantAId.toString().substring(0, 8);
        UUID adminUserId = UUID.randomUUID();
        String adminEmail = "admin-" + adminUserId.toString().substring(0, 8) + "@example.com";
        String adminPassword = "P@ssw0rd-123";
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                tenantAId, "Tenant A", tenantASlug, "ACTIVE", now, now
        );
        // password hash for BCryptPasswordEncoder with the same value as in test
        String passwordHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(adminPassword);
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                adminUserId, adminEmail, passwordHash, "Super Admin", "ACTIVE", now, now
        );
        jdbcTemplate.update(
                "INSERT INTO tenant_users (id, tenant_id, user_id, status, joined_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantAId, adminUserId, "ACTIVE", now, now, now
        );

        UUID roleId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO roles (id, tenant_id, name, description, is_system, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                roleId, tenantAId, "SUPERADMIN", "Super admin role", true, now, now
        );
        jdbcTemplate.update(
                "INSERT INTO user_roles (id, tenant_id, user_id, role_id, created_at) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantAId, adminUserId, roleId, now
        );

        // Attach permissions to role by code lookup (seeded by Flyway).
        List<String> requiredPerms = List.of(
                "iam:security:admin",
                "iam:tenants:admin",
                "iam:permissions:admin",
                "iam:roles:write",
                "iam:roles:read",
                "iam:tenant_users:write",
                "iam:tenant_users:read"
        );
        for (String code : requiredPerms) {
            UUID pid = jdbcTemplate.queryForObject("SELECT id FROM permissions WHERE code = ?", UUID.class, code);
            assertThat(pid).as("permission id for %s", code).isNotNull();
            jdbcTemplate.update(
                    "INSERT INTO role_permissions (id, tenant_id, role_id, permission_id, created_at) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), tenantAId, roleId, pid, now
            );
        }

        // Login to obtain bearer token.
        LoginRequest login = new LoginRequest();
        login.setTenantSlugOrId(tenantASlug);
        login.setEmail(adminEmail);
        login.setPassword(adminPassword);

        ResponseEntity<TokenResponse> loginResp = restTemplate.postForEntity(baseUrl + "/auth/login", login, TokenResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = loginResp.getBody().getAccessToken();
        assertThat(accessToken).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Create a tenant (cross-tenant).
        Map<String, Object> createTenantBody = Map.of("name", "Tenant B", "slug", "tenant-b-" + tenantAId.toString().substring(0, 6), "status", "ACTIVE");
        ResponseEntity<Map> tenantCreateResp = restTemplate.exchange(
                baseUrl + "/v1/tenants",
                HttpMethod.POST,
                new HttpEntity<>(createTenantBody, headers),
                Map.class
        );
        assertThat(tenantCreateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tenantBId = String.valueOf(tenantCreateResp.getBody().get("id"));
        assertThat(tenantBId).isNotBlank();

        // Create a role in Tenant B.
        Map<String, Object> createRoleBody = Map.of("name", "MANAGER", "description", "Manager role", "system", false);
        ResponseEntity<Map> roleCreateResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantBId + "/roles",
                HttpMethod.POST,
                new HttpEntity<>(createRoleBody, headers),
                Map.class
        );
        assertThat(roleCreateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tenantBRoleId = String.valueOf(roleCreateResp.getBody().get("id"));
        assertThat(tenantBRoleId).isNotBlank();

        // List permissions and attach first two to role (replace set).
        ResponseEntity<Map> permsResp = restTemplate.exchange(
                baseUrl + "/v1/permissions?page=1&pageSize=10",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                Map.class
        );
        assertThat(permsResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> permItems = (List<Map<String, Object>>) permsResp.getBody().get("items");
        assertThat(permItems).isNotEmpty();
        List<String> permissionIds = permItems.stream().limit(2).map(p -> String.valueOf(p.get("id"))).toList();

        Map<String, Object> replaceRolePerms = Map.of("permissionIds", permissionIds);
        ResponseEntity<Void> replaceRolePermsResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantBId + "/roles/" + tenantBRoleId + "/permissions",
                HttpMethod.PUT,
                new HttpEntity<>(replaceRolePerms, headers),
                Void.class
        );
        assertThat(replaceRolePermsResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Create a global user, add to Tenant B, assign role.
        Map<String, Object> createUserBody = Map.of("email", "u-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com", "displayName", "User B", "password", "P@ssw0rd-456", "status", "ACTIVE");
        ResponseEntity<Map> userCreateResp = restTemplate.exchange(
                baseUrl + "/v1/users",
                HttpMethod.POST,
                new HttpEntity<>(createUserBody, headers),
                Map.class
        );
        assertThat(userCreateResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String userBId = String.valueOf(userCreateResp.getBody().get("id"));
        assertThat(userBId).isNotBlank();

        Map<String, Object> addMemberBody = Map.of("userId", userBId, "displayName", "User B (Tenant)", "status", "ACTIVE");
        ResponseEntity<Map> addMemberResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantBId + "/members",
                HttpMethod.POST,
                new HttpEntity<>(addMemberBody, headers),
                Map.class
        );
        assertThat(addMemberResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> replaceUserRoles = Map.of("roleIds", List.of(tenantBRoleId));
        ResponseEntity<Void> replaceUserRolesResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantBId + "/users/" + userBId + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(replaceUserRoles, headers),
                Void.class
        );
        assertThat(replaceUserRolesResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify the assignment.
        ResponseEntity<List> listUserRolesResp = restTemplate.exchange(
                baseUrl + "/v1/tenants/" + tenantBId + "/users/" + userBId + "/roles",
                HttpMethod.GET,
                new HttpEntity<>(null, headers),
                List.class
        );
        assertThat(listUserRolesResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listUserRolesResp.getBody()).isNotEmpty();

        // Docs endpoints for AI consumption.
        ResponseEntity<Map> openApiResp = restTemplate.getForEntity(baseUrl + "/v3/api-docs", Map.class);
        assertThat(openApiResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApiResp.getBody()).containsKey("openapi");

        ResponseEntity<Map> aiGuideResp = restTemplate.getForEntity(baseUrl + "/v1/ai/guide", Map.class);
        assertThat(aiGuideResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aiGuideResp.getBody()).containsKey("workflows");
    }
}

