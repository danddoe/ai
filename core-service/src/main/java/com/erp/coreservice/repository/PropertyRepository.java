package com.erp.coreservice.repository;

import com.erp.coreservice.domain.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository extends JpaRepository<Property, UUID> {

    Optional<Property> findByPropertyIdAndTenantId(UUID propertyId, UUID tenantId);

    Page<Property> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
