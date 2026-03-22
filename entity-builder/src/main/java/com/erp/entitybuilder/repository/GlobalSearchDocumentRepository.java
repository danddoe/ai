package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.GlobalSearchDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlobalSearchDocumentRepository extends JpaRepository<GlobalSearchDocument, UUID> {

    Optional<GlobalSearchDocument> findByTenantIdAndSourceTypeAndSourceRecordId(
            UUID tenantId, String sourceType, UUID sourceRecordId);

    @Modifying
    @Query("DELETE FROM GlobalSearchDocument g WHERE g.tenantId = :tenantId AND g.sourceType = :sourceType AND g.sourceRecordId = :recordId")
    int deleteByTenantIdAndSourceTypeAndSourceRecordId(
            @Param("tenantId") UUID tenantId,
            @Param("sourceType") String sourceType,
            @Param("recordId") UUID recordId);

    @Query(value = """
            SELECT id FROM global_search_documents
            WHERE tenant_id = :tenantId
              AND search_text ILIKE :pattern ESCAPE '!'
            ORDER BY updated_at DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<UUID> findIdsForTenantSearch(
            @Param("tenantId") UUID tenantId,
            @Param("pattern") String pattern,
            @Param("lim") int limit);
}
