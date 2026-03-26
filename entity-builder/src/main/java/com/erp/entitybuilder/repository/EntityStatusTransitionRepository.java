package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.EntityStatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EntityStatusTransitionRepository extends JpaRepository<EntityStatusTransition, UUID> {

    boolean existsByTenantIdAndFromStatusIdAndToStatusId(UUID tenantId, UUID fromStatusId, UUID toStatusId);
}
