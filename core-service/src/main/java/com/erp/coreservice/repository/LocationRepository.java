package com.erp.coreservice.repository;

import com.erp.coreservice.domain.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {

    Optional<Location> findByLocationIdAndTenantId(UUID locationId, UUID tenantId);

    Page<Location> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
