package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.EntityField;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Allocates {@link com.erp.entitybuilder.domain.EntityRecord#getBusinessDocumentNumber()} when
 * a {@link FieldTypes#DOCUMENT_NUMBER} field defines non-{@link DocumentNumberGenerationPolicy.Strategy#MANUAL} config.
 */
@Service
public class DocumentNumberGenerationService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyyMM");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentNumberGenerationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Picks the first {@code document_number} field (by {@code sortOrder}), reads its policy, and generates a value if auto strategy.
     */
    public Optional<String> generateIfAbsent(UUID tenantId, UUID entityId, List<EntityField> fields) {
        Optional<EntityField> docField = fields.stream()
                .filter(FieldTypes::isDocumentNumber)
                .min(Comparator.comparingInt(EntityField::getSortOrder));
        if (docField.isEmpty()) {
            return Optional.empty();
        }
        DocumentNumberGenerationPolicy policy = DocumentNumberGenerationPolicy.fromFieldConfig(
                docField.get().getConfig(), objectMapper);
        if (!policy.isAutoGenerate()) {
            return Optional.empty();
        }
        return Optional.of(switch (policy.getStrategy()) {
            case MANUAL -> throw new IllegalStateException("unreachable");
            case TIMESTAMP -> formatTimestamp(policy);
            case TENANT_SEQUENCE -> formatSequential(tenantId, entityId, "", policy);
            case MONTHLY_SEQUENCE -> {
                ZonedDateTime z = Instant.now().atZone(policy.getTimeZone());
                String period = z.format(MONTH_KEY);
                yield formatSequential(tenantId, entityId, period, policy);
            }
        });
    }

    private static String formatTimestamp(DocumentNumberGenerationPolicy policy) {
        ZonedDateTime z = Instant.now().atZone(ZoneId.of("UTC"));
        String core = z.format(TS_FORMAT);
        int rnd = ThreadLocalRandom.current().nextInt(1000);
        return policy.getPrefix() + core + String.format("%03d", rnd);
    }

    private String formatSequential(UUID tenantId, UUID entityId, String periodKey, DocumentNumberGenerationPolicy policy) {
        long n = nextSequenceValue(tenantId, entityId, periodKey == null ? "" : periodKey);
        String suffix = String.format("%0" + policy.getSequenceWidth() + "d", n);
        if (policy.getStrategy() == DocumentNumberGenerationPolicy.Strategy.MONTHLY_SEQUENCE) {
            return policy.getPrefix() + periodKey + suffix;
        }
        return policy.getPrefix() + suffix;
    }

    /**
     * Atomically increment and return the new sequence value (1-based).
     */
    public long nextSequenceValue(UUID tenantId, UUID entityId, String periodKey) {
        String pk = periodKey == null ? "" : periodKey;
        String sql = """
                INSERT INTO entity_document_number_sequences (id, tenant_id, entity_id, period_key, last_value, updated_at)
                VALUES (gen_random_uuid(), ?, ?, ?, 1, now())
                ON CONFLICT (tenant_id, entity_id, period_key) DO UPDATE SET
                    last_value = entity_document_number_sequences.last_value + 1,
                    updated_at = now()
                RETURNING last_value
                """;
        Long v = jdbcTemplate.queryForObject(sql, Long.class, tenantId, entityId, pk);
        if (v == null) {
            throw new IllegalStateException("sequence allocation returned null");
        }
        return v;
    }
}
