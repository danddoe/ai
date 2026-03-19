package com.erp.entitybuilder.repository;

import com.erp.entitybuilder.domain.IdempotencyRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, UUID> {
    Optional<IdempotencyRequest> findByTenantIdAndUserIdAndMethodAndRouteTemplateAndIdempotencyKey(
            UUID tenantId,
            UUID userId,
            String method,
            String routeTemplate,
            String idempotencyKey
    );
}

