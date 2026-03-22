package com.erp.coreservice.repository;

import com.erp.coreservice.domain.PropertyUnit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyUnitRepository extends JpaRepository<PropertyUnit, UUID> {

    Optional<PropertyUnit> findByUnitIdAndTenantId(UUID unitId, UUID tenantId);

    Page<PropertyUnit> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<PropertyUnit> findByTenantIdAndPropertyIdOrderByCreatedAtDesc(UUID tenantId, UUID propertyId, Pageable pageable);
}
