package com.erp.iam.e2e;

import com.erp.iam.web.dto.LoginRequest;
import com.erp.iam.web.dto.RefreshRequest;
import com.erp.iam.web.dto.TokenResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.HttpClientErrorException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IamAuthE2ETest extends AbstractIamE2ETest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UUID tenantId;
    private String tenantSlug;
    private UUID userId;
    private String email;
    private String password;

    @BeforeEach
    void bootstrapTenantAndUser() {
        // If the test was skipped (DB not reachable), appContext/jdbcTemplate will be null.
        Assumptions.assumeTrue(jdbcTemplate != null, "E2E skipped: no CockroachDB connection");

        tenantId = UUID.randomUUID();
        tenantSlug = "t-" + tenantId.toString().substring(0, 8);

        userId = UUID.randomUUID();
        email = "user-" + userId.toString().substring(0, 8) + "@example.com";
        password = "e2e-" + UUID.randomUUID() + "-Pw1";
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(
                "INSERT INTO tenants (id, name, slug, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId, "Test Tenant", tenantSlug, "ACTIVE", now, now
        );

        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, display_name, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, email, passwordEncoder.encode(password), "Test User", "ACTIVE", now, now
        );

        jdbcTemplate.update(
                "INSERT INTO tenant_users (id, tenant_id, user_id, status, joined_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), tenantId, userId, "ACTIVE", now, now, now
        );
    }

    @Test
    void endToEnd_login_refresh_logout() {
        LoginRequest login = new LoginRequest();
        login.setTenantSlugOrId(tenantSlug);
        login.setEmail(email);
        login.setPassword(password);

        ResponseEntity<TokenResponse> loginResp = restTemplate.postForEntity(baseUrl + "/auth/login", login, TokenResponse.class);
        assertThat(loginResp.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(loginResp.getBody()).isNotNull();
        assertThat(loginResp.getBody().getAccessToken()).isNotBlank();
        assertThat(loginResp.getBody().getRefreshToken()).isNotBlank();

        String refreshToken1 = loginResp.getBody().getRefreshToken();

        RefreshRequest refresh1 = new RefreshRequest();
        refresh1.setRefreshToken(refreshToken1);
        ResponseEntity<TokenResponse> refreshResp = restTemplate.postForEntity(baseUrl + "/auth/refresh", refresh1, TokenResponse.class);
        assertThat(refreshResp.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(refreshResp.getBody()).isNotNull();
        assertThat(refreshResp.getBody().getAccessToken()).isNotBlank();
        assertThat(refreshResp.getBody().getRefreshToken()).isNotBlank();

        String refreshToken2 = refreshResp.getBody().getRefreshToken();
        assertThat(refreshToken2).isNotEqualTo(refreshToken1);

        RefreshRequest logoutReq = new RefreshRequest();
        logoutReq.setRefreshToken(refreshToken2);
        ResponseEntity<Void> logoutResp = restTemplate.postForEntity(baseUrl + "/auth/logout", logoutReq, Void.class);
        assertThat(logoutResp.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.NO_CONTENT);

        RefreshRequest refreshAfterLogout = new RefreshRequest();
        refreshAfterLogout.setRefreshToken(refreshToken2);

        try {
            restTemplate.postForEntity(baseUrl + "/auth/refresh", refreshAfterLogout, Map.class);
            Assertions.fail("Expected 401 after logout");
        } catch (HttpClientErrorException.Unauthorized e) {
            assertThat(e.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
        }
    }
}
