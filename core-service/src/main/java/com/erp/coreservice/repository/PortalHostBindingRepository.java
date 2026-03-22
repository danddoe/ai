package com.erp.coreservice.repository;

import com.erp.coreservice.domain.PortalHostBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortalHostBindingRepository extends JpaRepository<PortalHostBinding, UUID> {

    Optional<PortalHostBinding> findByHostname(String hostname);

    boolean existsByHostnameAndBindingIdNot(String hostname, UUID bindingId);

    List<PortalHostBinding> findByTenantIdOrderByHostnameAsc(UUID tenantId);
}
