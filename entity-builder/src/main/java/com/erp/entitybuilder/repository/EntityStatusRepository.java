package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityStatus;
import com.erp.entitybuilder.domain.RecordScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntityStatusRepository extends JpaRepository<EntityStatus, UUID> {

    Optional<EntityStatus> findByTenantIdAndRecordScopeAndCode(UUID tenantId, RecordScope recordScope, String code);

    List<EntityStatus> findByTenantIdAndRecordScope(UUID tenantId, RecordScope recordScope);
}
