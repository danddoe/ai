package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.PiiVaultEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PiiVaultRepository extends JpaRepository<PiiVaultEntry, UUID> {
    Optional<PiiVaultEntry> findByTenantIdAndRecordIdAndFieldId(UUID tenantId, UUID recordId, UUID fieldId);

    boolean existsByFieldId(UUID fieldId);
}

