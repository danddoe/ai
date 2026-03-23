package com.erp.coreservice.e2e;

import com.erp.coreservice.CoreServiceApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractCoreServiceE2ETest {

    protected static final String DEFAULT_HOST = "localhost";
    protected static final int DEFAULT_PORT = 26257;
    /** Must match core-service JwtService signing key for this test JVM */
    protected static final String JWT_SECRET = "erp-iam-dev-secret-key-at-least-256-bits-long-for-hs256";

    /** Permissions for exercising every master-data REST surface in one tenant. */
    protected static final List<String> ALL_MASTER_DATA_PERMS = List.of(
            "master_data:countries:read",
            "master_data:companies:read",
            "master_data:companies:write",
            "master_data:business_units:read",
            "master_data:business_units:write",
            "master_data:regions:read",
            "master_data:regions:write",
            "master_data:locations:read",
            "master_data:locations:write",
            "master_data:properties:read",
            "master_data:properties:write",
            "master_data:property_units:read",
            "master_data:property_units:write"
    );

    protected String host;
    protected int port;

    protected String testDbName;
    protected String testDbJdbcUrl;

    protected ConfigurableApplicationContext appContext;
    protected RestTemplate restTemplate;
    protected JdbcTemplate jdbcTemplate;
    protected String baseUrl;

    @BeforeAll
    protected void startAppAgainstExistingCockroach() {
        host = System.getenv().getOrDefault("COCKROACH_HOST", DEFAULT_HOST);
        port = Integer.parseInt(System.getenv().getOrDefault("COCKROACH_PORT", String.valueOf(DEFAULT_PORT)));

        String adminJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/defaultdb?sslmode=disable";
        testDbName = "core_service_e2e_" + UUID.randomUUID().toString().replace("-", "");
        testDbJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + testDbName + "?sslmode=disable";

        try (Connection c = DriverManager.getConnection(adminJdbcUrl, "root", "");
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + testDbName);
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "CockroachDB not reachable at " + host + ":" + port + ". Start it first (see cockroachdb/README.md).");
            return;
        }

        appContext = new SpringApplicationBuilder(CoreServiceApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + testDbJdbcUrl,
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=",
                        "--spring.flyway.enabled=true",
                        "--app.jwt.hmac-secret=" + JWT_SECRET
                );

        int httpPort = ((WebServerApplicationContext) appContext).getWebServer().getPort();
        baseUrl = "http://localhost:" + httpPort;
        var requestFactory = new HttpComponentsClientHttpRequestFactory(HttpClients.createDefault());
        restTemplate = new RestTemplate(requestFactory);
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            protected boolean hasError(HttpStatusCode statusCode) {
                return false;
            }
        });

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(testDbJdbcUrl);
        ds.setUsername("root");
        ds.setPassword("");
        jdbcTemplate = new JdbcTemplate(ds);
    }

    @AfterAll
    protected void stopAppAndDropDb() {
        if (appContext != null) {
            appContext.close();
        }

        if (testDbName != null) {
            String adminJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/defaultdb?sslmode=disable";
            try (Connection c = DriverManager.getConnection(adminJdbcUrl, "root", "");
                 Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS " + testDbName);
            } catch (Exception ignored) {
            }
        }
    }

    protected String tenantPath(UUID tenantId, String subPath) {
        return baseUrl + "/v1/tenants/" + tenantId + subPath;
    }

    protected HttpHeaders authHeaders(UUID userId, UUID tenantId, List<String> permissions) {
        String token = createAccessToken(userId, tenantId, permissions);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    protected String createAccessToken(UUID userId, UUID tenantId, List<String> permissions) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tenant_id", tenantId.toString())
                .claim("email", "e2e@test.example")
                .claim("roles", List.of())
                .claim("permissions", permissions != null ? permissions : List.of())
                .issuer("erp-iam")
                .audience().add("erp-api").and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(key)
                .compact();
    }
}
