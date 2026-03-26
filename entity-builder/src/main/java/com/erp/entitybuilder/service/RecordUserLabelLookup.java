package com.erp.entitybuilder.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves IAM user ids to display labels (tenant display name, user display name, or email)
 * using the same query pattern as {@link EntityRecordAuditService} actor enrichment.
 */
@Component
public class RecordUserLabelLookup {

    private static final Logger log = LoggerFactory.getLogger(RecordUserLabelLookup.class);

    private final JdbcTemplate jdbcTemplate;

    public RecordUserLabelLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns a map of user id → label for ids present in {@code userIds}. Missing ids are omitted.
     */
    public Map<UUID, String> labelsFor(UUID tenantId, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<UUID> unique = new HashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Map.of();
        }
        List<UUID> idList = new ArrayList<>(unique);
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
        try {
            return jdbcTemplate.query(
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
                log.trace("Skipping record user label lookup (IAM users not in this database): {}", ex.getMessage());
                return Map.of();
            }
            throw ex;
        }
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
}
