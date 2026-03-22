package com.erp.entitybuilder.service;

import com.erp.entitybuilder.domain.GlobalSearchDocument;
import com.erp.entitybuilder.repository.GlobalSearchDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class GlobalSearchIndexService {

    private static final int SEARCH_TEXT_MAX = 8192;

    private final GlobalSearchDocumentRepository repository;

    public GlobalSearchIndexService(GlobalSearchDocumentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsertEntityRecord(
            UUID tenantId,
            UUID recordId,
            UUID entityId,
            String normalizedSearchVector,
            String displayTitle,
            String entityName,
            String entitySlug
    ) {
        String title = displayTitle != null && !displayTitle.isBlank() ? displayTitle : recordId.toString();
        String subtitle = entityName != null ? entityName : "";

        String ctx = ((entityName != null ? entityName : "") + " " + (entitySlug != null ? entitySlug : ""))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        String vec = normalizedSearchVector != null ? normalizedSearchVector : "";
        // Title and record id are matched by omnibox ILIKE on search_text; the vector alone can miss labels users type.
        String titleSearch = title.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String idCompact = recordId.toString().toLowerCase(Locale.ROOT).replace("-", "");
        String combined = (vec + " " + ctx + " " + titleSearch + " " + recordId.toString().toLowerCase(Locale.ROOT) + " " + idCompact)
                .replaceAll("\\s+", " ")
                .trim();
        if (combined.length() > SEARCH_TEXT_MAX) {
            combined = combined.substring(0, SEARCH_TEXT_MAX);
        }

        String routePath = "/entities/" + entityId + "/records/" + recordId;

        Optional<GlobalSearchDocument> existing = repository.findByTenantIdAndSourceTypeAndSourceRecordId(
                tenantId, GlobalSearchDocument.SOURCE_ENTITY_RECORD, recordId);

        GlobalSearchDocument row = existing.orElseGet(GlobalSearchDocument::new);
        row.setTenantId(tenantId);
        row.setSourceType(GlobalSearchDocument.SOURCE_ENTITY_RECORD);
        row.setSourceEntityId(entityId);
        row.setSourceRecordId(recordId);
        row.setTitle(title);
        row.setSubtitle(subtitle);
        row.setRoutePath(routePath);
        row.setSearchText(combined);
        repository.save(row);
    }

    @Transactional
    public void deleteEntityRecord(UUID tenantId, UUID recordId) {
        repository.deleteByTenantIdAndSourceTypeAndSourceRecordId(
                tenantId, GlobalSearchDocument.SOURCE_ENTITY_RECORD, recordId);
    }
}
