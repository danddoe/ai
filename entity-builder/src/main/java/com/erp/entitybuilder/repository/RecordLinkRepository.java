package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.RecordLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordLinkRepository extends JpaRepository<RecordLink, UUID> {
    List<RecordLink> findByTenantIdAndFromRecordIdAndRelationshipId(UUID tenantId, UUID fromRecordId, UUID relationshipId);
    List<RecordLink> findByTenantIdAndFromRecordId(UUID tenantId, UUID fromRecordId);
    boolean existsByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(UUID tenantId, UUID fromRecordId, UUID relationshipId, UUID toRecordId);
    Optional<RecordLink> findFirstByTenantIdAndFromRecordIdAndRelationshipId(UUID tenantId, UUID fromRecordId, UUID relationshipId);
    Optional<RecordLink> findByTenantIdAndFromRecordIdAndRelationshipIdAndToRecordId(UUID tenantId, UUID fromRecordId, UUID relationshipId, UUID toRecordId);
    void deleteByTenantIdAndFromRecordIdAndRelationshipId(UUID tenantId, UUID fromRecordId, UUID relationshipId);
    void deleteByTenantIdAndFromRecordId(UUID tenantId, UUID fromRecordId);
}

