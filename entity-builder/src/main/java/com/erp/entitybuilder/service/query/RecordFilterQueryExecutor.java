package com.erp.entitybuilder.service.query;

import com.erp.entitybuilder.config.PlatformTenantProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RecordFilterQueryExecutor {

    private final RecordFilterSqlBuilder sqlBuilder;
    private final PlatformTenantProperties platformTenantProperties;

    @PersistenceContext
    private EntityManager entityManager;

    public RecordFilterQueryExecutor(RecordFilterSqlBuilder sqlBuilder, PlatformTenantProperties platformTenantProperties) {
        this.sqlBuilder = sqlBuilder;
        this.platformTenantProperties = platformTenantProperties;
    }

    public long countMatching(UUID tenantId, UUID entityId, ResolvedFilter filter) {
        StringBuilder where = new StringBuilder(entityVisibilitySqlPrefix());
        List<Object> params = visibilityParamsFirst(tenantId, entityId);
        if (filter != null) {
            where.append(" AND ");
            sqlBuilder.append(where, params, filter);
        }
        String sql = "SELECT COUNT(*) FROM entity_records er WHERE " + where;
        Query q = entityManager.createNativeQuery(sql);
        bindAll(q, params);
        Object single = q.getSingleResult();
        if (single instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(single.toString());
    }

    private String entityVisibilitySqlPrefix() {
        if (platformTenantProperties.isConfigured()) {
            return "er.entity_id = ? AND (er.tenant_id = ? OR (er.record_scope = 'STANDARD_RECORD' AND er.tenant_id = ?))";
        }
        return "er.entity_id = ? AND er.tenant_id = ?";
    }

    private List<Object> visibilityParamsFirst(UUID requestTenantId, UUID entityId) {
        List<Object> params = new ArrayList<>();
        params.add(entityId);
        params.add(requestTenantId);
        if (platformTenantProperties.isConfigured()) {
            params.add(platformTenantProperties.getTenantId());
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    public List<UUID> findRecordIds(
            UUID tenantId,
            UUID entityId,
            ResolvedFilter filter,
            int offset,
            int limit,
            String orderColumn,
            boolean ascending
    ) {
        StringBuilder where = new StringBuilder(entityVisibilitySqlPrefix());
        List<Object> params = visibilityParamsFirst(tenantId, entityId);
        if (filter != null) {
            where.append(" AND ");
            sqlBuilder.append(where, params, filter);
        }
        String dir = ascending ? "ASC" : "DESC";
        String sql = "SELECT er.id FROM entity_records er WHERE " + where + " ORDER BY " + orderColumn + " " + dir + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        Query q = entityManager.createNativeQuery(sql);
        bindAll(q, params);
        List<?> rows = q.getResultList();
        List<UUID> ids = new ArrayList<>(rows.size());
        for (Object row : rows) {
            if (row instanceof UUID u) {
                ids.add(u);
            } else if (row instanceof String s) {
                ids.add(UUID.fromString(s));
            } else {
                ids.add(UUID.fromString(row.toString()));
            }
        }
        return ids;
    }

    private static void bindAll(Query q, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            q.setParameter(i + 1, params.get(i));
        }
    }
}
