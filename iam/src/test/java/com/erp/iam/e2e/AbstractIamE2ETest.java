package com.erp.iam.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractIamE2ETest {

    protected static final String DEFAULT_HOST = "localhost";
    protected static final int DEFAULT_PORT = 26257;

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
        testDbName = "iam_e2e_" + UUID.randomUUID().toString().replace("-", "");
        testDbJdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + testDbName + "?sslmode=disable";

        // If CockroachDB isn't running, skip E2E tests (keeps gradle build rerunnable on any machine).
        try (Connection c = DriverManager.getConnection(adminJdbcUrl, "root", "");
             Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + testDbName);
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    "CockroachDB not reachable at " + host + ":" + port + ". Start it first (see cockroachdb/README.md).");
            return;
        }

        // Use command-line args so these override application.yml (default properties do not).
        appContext = new SpringApplicationBuilder(com.erp.iam.IamApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + testDbJdbcUrl,
                        "--spring.datasource.username=root",
                        "--spring.datasource.password=",
                        "--spring.flyway.enabled=true",
                        // deterministic JWT for tests
                        "--JWT_SECRET=iam-e2e-hs256-secret-key-should-be-32-bytes-minimum-1234"
                );

        int httpPort = ((WebServerApplicationContext) appContext).getWebServer().getPort();
        baseUrl = "http://localhost:" + httpPort;
        restTemplate = new RestTemplate();

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
}

