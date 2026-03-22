package com.erp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Inserts into {@code audit_log} using the caller's transaction (same {@link JdbcTemplate} / DataSource).
 */
public class AuditLogWriter {

    private static final String INSERT = """
            INSERT INTO audit_log (
                id, tenant_id, actor_id, action, resource_type, resource_id, payload,
                source_service, operation, correlation_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void append(AuditEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event.payload());
        } catch (Exception e) {
            throw new IllegalStateException("audit payload serialization failed", e);
        }
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        try {
            pg.setValue(json);
        } catch (SQLException e) {
            throw new IllegalStateException("audit jsonb wrap failed", e);
        }
        jdbcTemplate.update(
                INSERT,
                UUID.randomUUID(),
                event.tenantId(),
                event.actorId(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                pg,
                event.sourceService(),
                event.operation(),
                event.correlationId()
        );
    }
}
