package com.erp.entitybuilder.service;

import com.erp.audit.AuditResourceTypes;
import com.erp.entitybuilder.domain.EntityRecord;
import com.erp.entitybuilder.repository.EntityDefinitionRepository;
import com.erp.entitybuilder.repository.EntityRecordRepository;
import com.erp.entitybuilder.web.ApiException;
import com.erp.entitybuilder.web.v1.dto.AuditEventDtos;
import com.erp.entitybuilder.web.v1.dto.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EntityRecordAuditService {

    private static final Logger log = LoggerFactory.getLogger(EntityRecordAuditService.class);
    private static final String SOURCE_ENTITY_BUILDER = "entity-builder";

    private final JdbcTemplate jdbcTemplate;
    private final EntityRecordRepository recordRepository;
    private final EntityDefinitionRepository entityRepository;
    private final ObjectMapper objectMapper;

    public EntityRecordAuditService(
            JdbcTemplate jdbcTemplate,
            EntityRecordRepository recordRepository,
            EntityDefinitionRepository entityRepository,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.recordRepository = recordRepository;
        this.entityRepository = entityRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDtos.AuditEventDto> listForRecord(
            UUID tenantId,
            UUID entityId,
            UUID recordId,
            int page,
            int pageSize,
            Instant from,
            Instant to,
            String actionPrefix
    ) {
        assertRecordInScope(tenantId, entityId, recordId);
        return queryPage(tenantId, recordId, page, pageSize, from, to, actionPrefix);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDtos.AuditEventDto> listForEntity(
            UUID tenantId,
            UUID entityId,
            int page,
            int pageSize,
            Instant from,
            Instant to,
            String actionPrefix
    ) {
        entityRepository.findById(entityId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Entity not found"));
        return queryPageEntityWide(tenantId, entityId, page, pageSize, from, to, actionPrefix);
    }

    private void assertRecordInScope(UUID tenantId, UUID entityId, UUID recordId) {
        EntityRecord r = recordRepository.findById(recordId)
                .filter(rec -> tenantId.equals(rec.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found"));
        if (!entityId.equals(r.getEntityId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Record not found");
        }
    }

    private PageResponse<AuditEventDtos.AuditEventDto> queryPage(
            UUID tenantId,
            UUID recordId,
            int page,
            int pageSize,
            Instant from,
            Instant to,
            String actionPrefix
    ) {
        int p = Math.max(1, page);
        int size = clampPageSize(pageSize);
        int offset = (p - 1) * size;

        List<Object> countArgs = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                """
                WHERE tenant_id = ? AND resource_type = ? AND resource_id = ?
                AND (source_service = ? OR source_service IS NULL)
                """
        );
        countArgs.add(tenantId);
        countArgs.add(AuditResourceTypes.ENTITY_RECORD);
        countArgs.add(recordId);
        countArgs.add(SOURCE_ENTITY_BUILDER);
        appendTimeAndAction(where, countArgs, from, to, actionPrefix);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*)::bigint FROM audit_log " + where,
                Long.class,
                countArgs.toArray()
        );
        long t = total != null ? total : 0L;

        List<Object> dataArgs = new ArrayList<>(countArgs);
        dataArgs.add(size);
        dataArgs.add(offset);

        String sql = """
                SELECT id, created_at, actor_id, action, operation, resource_type, resource_id,
                       correlation_id, source_service, payload::text AS payload_json
                FROM audit_log
                """
                + where
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";

        List<AuditEventDtos.AuditEventDto> items = jdbcTemplate.query(sql, auditRowMapper(), dataArgs.toArray());
        enrichActorLabels(tenantId, items);
        return new PageResponse<>(items, p, size, t);
    }

    private PageResponse<AuditEventDtos.AuditEventDto> queryPageEntityWide(
            UUID tenantId,
            UUID entityId,
            int page,
            int pageSize,
            Instant from,
            Instant to,
            String actionPrefix
    ) {
        int p = Math.max(1, page);
        int size = clampPageSize(pageSize);
        int offset = (p - 1) * size;
        String entityIdStr = entityId.toString();

        List<Object> countArgs = new ArrayList<>();
        StringBuilder where = new StringBuilder(
                """
                WHERE tenant_id = ? AND resource_type = ?
                AND (source_service = ? OR source_service IS NULL)
                AND payload->'context'->>'entityId' = ?
                """
        );
        countArgs.add(tenantId);
        countArgs.add(AuditResourceTypes.ENTITY_RECORD);
        countArgs.add(SOURCE_ENTITY_BUILDER);
        countArgs.add(entityIdStr);
        appendTimeAndAction(where, countArgs, from, to, actionPrefix);

        Long total = jdbcTemplate.queryForObject(
                "SELECT count(*)::bigint FROM audit_log " + where,
                Long.class,
                countArgs.toArray()
        );
        long t = total != null ? total : 0L;

        List<Object> dataArgs = new ArrayList<>(countArgs);
        dataArgs.add(size);
        dataArgs.add(offset);

        String sql = """
                SELECT id, created_at, actor_id, action, operation, resource_type, resource_id,
                       correlation_id, source_service, payload::text AS payload_json
                FROM audit_log
                """
                + where
                + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?";

        List<AuditEventDtos.AuditEventDto> items = jdbcTemplate.query(sql, auditRowMapper(), dataArgs.toArray());
        enrichActorLabels(tenantId, items);
        return new PageResponse<>(items, p, size, t);
    }

    /**
     * Prefer {@code payload.actor} (written with each event). Otherwise join IAM {@code users} /
     * {@code tenant_users} when they share the database with {@code audit_log}.
     */
    private void enrichActorLabels(UUID tenantId, List<AuditEventDtos.AuditEventDto> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (AuditEventDtos.AuditEventDto e : items) {
            String fromPayload = actorLabelFromPayload(e.getPayload());
            if (fromPayload != null) {
                e.setActorLabel(fromPayload);
            }
        }
        Set<UUID> actorIds = new HashSet<>();
        for (AuditEventDtos.AuditEventDto e : items) {
            if (e.getActorId() != null && (e.getActorLabel() == null || e.getActorLabel().isBlank())) {
                actorIds.add(e.getActorId());
            }
        }
        if (actorIds.isEmpty()) {
            return;
        }
        List<UUID> idList = new ArrayList<>(actorIds);
        String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));
        String sql = """
                SELECT u.id AS user_id, u.email, u.display_name AS user_display_name,
                       tu.display_name AS tenant_display_name
                FROM users u
                LEFT JOIN tenant_users tu ON tu.user_id = u.id AND tu.tenant_id = ?
                WHERE u.id IN (""" + placeholders + ")";
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.addAll(idList);
        Map<UUID, String> labels;
        try {
            labels = jdbcTemplate.query(
                    sql,
                    rs -> {
                        Map<UUID, String> m = new HashMap<>();
                        while (rs.next()) {
                            UUID id = rs.getObject("user_id", UUID.class);
                            String tenantDn = rs.getString("tenant_display_name");
                            String userDn = rs.getString("user_display_name");
                            String email = rs.getString("email");
                            String label = firstNonBlank(tenantDn, userDn, email);
                            if (id != null && label != null) {
                                m.put(id, label);
                            }
                        }
                        return m;
                    },
                    args.toArray());
        } catch (DataAccessException ex) {
            if (isMissingIamUsersRelation(ex)) {
                log.trace("Skipping audit actor JDBC enrichment (IAM users not in this database): {}", ex.getMessage());
                return;
            }
            throw ex;
        }
        for (AuditEventDtos.AuditEventDto e : items) {
            if (e.getActorId() != null && (e.getActorLabel() == null || e.getActorLabel().isBlank())) {
                String label = labels.get(e.getActorId());
                if (label != null) {
                    e.setActorLabel(label);
                }
            }
        }
    }

    private static String actorLabelFromPayload(JsonNode payload) {
        if (payload == null || payload.isNull() || !payload.has("actor")) {
            return null;
        }
        JsonNode actor = payload.get("actor");
        if (actor == null || !actor.isObject()) {
            return null;
        }
        return firstNonBlank(
                textNode(actor, "tenantDisplayName"),
                textNode(actor, "displayName"),
                textNode(actor, "email")
        );
    }

    private static String textNode(JsonNode parent, String field) {
        JsonNode n = parent.get(field);
        if (n == null || !n.isTextual()) {
            return null;
        }
        String t = n.asText();
        return t == null || t.isBlank() ? null : t.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static boolean isMissingIamUsersRelation(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException se) {
                if ("42P01".equals(se.getSQLState())) {
                    return true;
                }
                String msg = se.getMessage();
                if (msg != null && msg.contains("does not exist") && msg.contains("users")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void appendTimeAndAction(
            StringBuilder where,
            List<Object> args,
            Instant from,
            Instant to,
            String actionPrefix
    ) {
        if (from != null) {
            where.append(" AND created_at >= ?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND created_at <= ?");
            args.add(Timestamp.from(to));
        }
        if (actionPrefix != null && !actionPrefix.isBlank()) {
            where.append(" AND action LIKE ?");
            args.add(actionPrefix.trim() + "%");
        }
    }

    private RowMapper<AuditEventDtos.AuditEventDto> auditRowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private AuditEventDtos.AuditEventDto mapRow(ResultSet rs) throws SQLException {
        AuditEventDtos.AuditEventDto dto = new AuditEventDtos.AuditEventDto();
        dto.setId(rs.getObject("id", UUID.class));
        Timestamp ts = rs.getTimestamp("created_at");
        dto.setCreatedAt(ts != null ? ts.toInstant() : null);
        dto.setActorId(rs.getObject("actor_id", UUID.class));
        dto.setAction(rs.getString("action"));
        dto.setOperation(rs.getString("operation"));
        dto.setResourceType(rs.getString("resource_type"));
        dto.setResourceId(rs.getObject("resource_id", UUID.class));
        dto.setCorrelationId(rs.getObject("correlation_id", UUID.class));
        dto.setSourceService(rs.getString("source_service"));
        String pj = rs.getString("payload_json");
        if (pj != null && !pj.isBlank()) {
            try {
                dto.setPayload(objectMapper.readTree(pj));
            } catch (Exception e) {
                dto.setPayload(objectMapper.nullNode());
            }
        } else {
            dto.setPayload(objectMapper.nullNode());
        }
        return dto;
    }

    private static int clampPageSize(int pageSize) {
        if (pageSize <= 0) {
            return 50;
        }
        return Math.min(pageSize, 200);
    }
}
