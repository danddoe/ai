package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import com.erp.entitybuilder.domain.EntityFieldStatuses;
import com.erp.entitybuilder.repository.EntityFieldRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Second DB round-trip for list pages: concatenates scalar values for fields marked
 * {@code includeInListSummaryDisplay}. Skips when any summary field is a reference or PII (portal resolves those).
 */
@Component
public class RecordListScalarSummaryService {

    private static final String SEP = " - ";

    private final NamedParameterJdbcTemplate namedJdbc;
    private final EntityFieldRepository fieldRepository;

    public RecordListScalarSummaryService(
            NamedParameterJdbcTemplate namedJdbc,
            EntityFieldRepository fieldRepository
    ) {
        this.namedJdbc = namedJdbc;
        this.fieldRepository = fieldRepository;
    }

    private static final RowMapper<ScalarRow> ROW_MAPPER = (rs, rowNum) -> new ScalarRow(
            rs.getObject("record_id", UUID.class),
            rs.getInt("sort_order"),
            rs.getString("field_type"),
            rs.getString("value_text"),
            rs.getBigDecimal("value_number"),
            rs.getObject("value_date", Timestamp.class),
            rs.getObject("value_boolean", Boolean.class)
    );

    public Map<UUID, String> loadScalarSummaries(
            UUID tenantId,
            UUID entityId,
            List<UUID> recordIds,
            boolean piiReadPermission
    ) {
        if (recordIds == null || recordIds.isEmpty()) {
            return Map.of();
        }
        List<EntityField> summaryFields = fieldRepository
                .findByEntityIdAndIncludeInListSummaryDisplayIsTrueAndStatusOrderBySortOrderAscNameAsc(
                        entityId,
                        EntityFieldStatuses.ACTIVE
                );
        if (summaryFields.isEmpty()) {
            return Map.of();
        }
        boolean hasReference = summaryFields.stream()
                .anyMatch(f -> "reference".equalsIgnoreCase(f.getFieldType()));
        boolean hasPii = summaryFields.stream().anyMatch(EntityField::isPii);
        if (hasReference || hasPii || !piiReadPermission) {
            return Map.of();
        }

        List<UUID> fieldIds = summaryFields.stream().map(EntityField::getId).distinct().toList();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("tenantId", tenantId);
        params.addValue("entityId", entityId);
        params.addValue("recordIds", recordIds);
        params.addValue("fieldIds", fieldIds);

        String sql = """
                SELECT v.record_id, f.sort_order, f.field_type, v.value_text, v.value_number, v.value_date, v.value_boolean
                FROM entity_record_values v
                INNER JOIN entity_fields f ON f.id = v.field_id
                INNER JOIN entity_records r ON r.id = v.record_id
                WHERE r.tenant_id = :tenantId
                  AND r.entity_id = :entityId
                  AND f.entity_id = :entityId
                  AND f.include_in_list_summary_display = TRUE
                  AND f.status = 'ACTIVE'
                  AND f.id IN (:fieldIds)
                  AND v.record_id IN (:recordIds)
                """;

        List<ScalarRow> rows = namedJdbc.query(sql, params, ROW_MAPPER);
        Map<UUID, List<ScalarRow>> byRecord = new HashMap<>();
        for (ScalarRow row : rows) {
            byRecord.computeIfAbsent(row.recordId(), k -> new ArrayList<>()).add(row);
        }

        Map<UUID, String> out = new HashMap<>();
        for (Map.Entry<UUID, List<ScalarRow>> e : byRecord.entrySet()) {
            List<ScalarRow> parts = e.getValue().stream()
                    .sorted(Comparator.comparingInt(ScalarRow::sortOrder))
                    .toList();
            String joined = parts.stream()
                    .map(RecordListScalarSummaryService::formatScalar)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(SEP));
            if (!joined.isEmpty()) {
                out.put(e.getKey(), joined);
            }
        }
        return out;
    }

    private static String formatScalar(ScalarRow row) {
        String ft = row.fieldType() == null ? "" : row.fieldType().trim().toLowerCase();
        return switch (ft) {
            case "boolean" -> {
                if (row.valueBoolean() == null) {
                    yield "—";
                }
                yield Boolean.TRUE.equals(row.valueBoolean()) ? "Yes" : "No";
            }
            case "number" -> row.valueNumber() != null ? row.valueNumber().stripTrailingZeros().toPlainString() : "—";
            case "date", "datetime" -> {
                Instant i = row.valueDate() == null ? null : row.valueDate().toInstant();
                yield i != null ? i.toString() : "—";
            }
            default -> {
                String t = row.valueText();
                yield t != null && !t.isBlank() ? t : "—";
            }
        };
    }

    private record ScalarRow(
            UUID recordId,
            int sortOrder,
            String fieldType,
            String valueText,
            BigDecimal valueNumber,
            Timestamp valueDate,
            Boolean valueBoolean
    ) {}
}
