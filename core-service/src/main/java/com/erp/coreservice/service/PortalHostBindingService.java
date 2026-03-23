package com.erp.coreservice.service;

import com.erp.coreservice.audit.CoreDomainAuditService;
import com.erp.coreservice.audit.CoreEntityAuditSnapshots;
import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.domain.Company;
import com.erp.coreservice.domain.PortalHostBinding;
import com.erp.coreservice.domain.PortalHostBindingScope;
import com.erp.coreservice.portal.PortalBootstrapContext;
import com.erp.coreservice.portal.PortalHostnameNormalizer;
import com.erp.coreservice.repository.BusinessUnitRepository;
import com.erp.coreservice.repository.CompanyRepository;
import com.erp.coreservice.repository.PortalHostBindingRepository;
import com.erp.coreservice.web.ApiException;
import com.erp.coreservice.web.v1.dto.PortalDtos;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PortalHostBindingService {

    private final PortalHostBindingRepository bindingRepository;
    private final CompanyRepository companyRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final CoreDomainAuditService coreDomainAuditService;

    public PortalHostBindingService(
            PortalHostBindingRepository bindingRepository,
            CompanyRepository companyRepository,
            BusinessUnitRepository businessUnitRepository,
            CoreDomainAuditService coreDomainAuditService
    ) {
        this.bindingRepository = bindingRepository;
        this.companyRepository = companyRepository;
        this.businessUnitRepository = businessUnitRepository;
        this.coreDomainAuditService = coreDomainAuditService;
    }

    @Transactional(readOnly = true)
    public PortalBootstrapContext resolveBootstrap(String rawHost) {
        String host = PortalHostnameNormalizer.normalize(rawHost);
        if (host == null) {
            return PortalBootstrapContext.empty();
        }
        try {
            return bindingRepository.findByHostname(host)
                    .map(this::toBootstrapContext)
                    .orElse(PortalBootstrapContext.empty());
        } catch (RuntimeException ignored) {
            return PortalBootstrapContext.empty();
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<PortalHostBinding> listForTenant(UUID tenantId) {
        return bindingRepository.findByTenantIdOrderByHostnameAsc(tenantId);
    }

    private PortalBootstrapContext toBootstrapContext(PortalHostBinding b) {
        return switch (b.getScope()) {
            case TENANT -> new PortalBootstrapContext(b.getTenantId(), null, null, PortalHostBindingScope.TENANT);
            case COMPANY -> resolveCompanyScope(b);
            case BUSINESS_UNIT -> resolveBusinessUnitScope(b);
        };
    }

    private PortalBootstrapContext resolveCompanyScope(PortalHostBinding b) {
        Company c = companyRepository.findByCompanyIdAndTenantId(b.getCompanyId(), b.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Company missing for portal binding"));
        UUID defaultBu = c.getDefaultPortalBuId();
        return new PortalBootstrapContext(c.getTenantId(), c.getCompanyId(), defaultBu, PortalHostBindingScope.COMPANY);
    }

    private PortalBootstrapContext resolveBusinessUnitScope(PortalHostBinding b) {
        BusinessUnit bu = businessUnitRepository.findByBuIdAndTenantId(b.getBuId(), b.getTenantId())
                .orElseThrow(() -> new IllegalStateException("Business unit missing for portal binding"));
        if (!bu.getCompanyId().equals(b.getCompanyId())) {
            throw new IllegalStateException("Portal binding company/bu mismatch");
        }
        return new PortalBootstrapContext(bu.getTenantId(), bu.getCompanyId(), bu.getBuId(), PortalHostBindingScope.BUSINESS_UNIT);
    }

    @Transactional(readOnly = true)
    public PortalHostBinding get(UUID tenantId, UUID bindingId) {
        PortalHostBinding b = bindingRepository.findById(bindingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Binding not found"));
        if (!b.getTenantId().equals(tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Binding not found");
        }
        return b;
    }

    @Transactional
    public PortalHostBinding create(UUID tenantId, PortalDtos.CreatePortalHostBindingRequest req) {
        String hostname = PortalHostnameNormalizer.normalize(req.getHostname());
        if (hostname == null || hostname.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "hostname is required");
        }
        if (bindingRepository.findByHostname(hostname).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Hostname already bound");
        }
        validateScopePayload(tenantId, req.getScope(), req.getCompanyId(), req.getBuId());
        PortalHostBinding b = new PortalHostBinding();
        b.setHostname(hostname);
        b.setScope(req.getScope());
        b.setTenantId(tenantId);
        b.setCompanyId(req.getScope() == PortalHostBindingScope.TENANT ? null : req.getCompanyId());
        b.setBuId(req.getScope() == PortalHostBindingScope.BUSINESS_UNIT ? req.getBuId() : null);
        b.setVerifiedAt(req.getVerifiedAt());
        PortalHostBinding saved = bindingRepository.save(b);
        coreDomainAuditService.portalHostBindingCreated(tenantId, saved);
        return saved;
    }

    @Transactional
    public PortalHostBinding patch(UUID tenantId, UUID bindingId, PortalDtos.PatchPortalHostBindingRequest req) {
        PortalHostBinding b = get(tenantId, bindingId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.portalHostBinding(b);
        if (req.getHostname() != null) {
            String hostname = PortalHostnameNormalizer.normalize(req.getHostname());
            if (hostname == null || hostname.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "hostname is invalid");
            }
            if (!hostname.equals(b.getHostname()) && bindingRepository.existsByHostnameAndBindingIdNot(hostname, bindingId)) {
                throw new ApiException(HttpStatus.CONFLICT, "conflict", "Hostname already bound");
            }
            b.setHostname(hostname);
        }
        PortalHostBindingScope scope = req.getScope() != null ? req.getScope() : b.getScope();
        UUID companyId = req.getCompanyId() != null ? req.getCompanyId() : b.getCompanyId();
        UUID buId = req.getBuId() != null ? req.getBuId() : b.getBuId();
        if (req.getScope() != null || req.getCompanyId() != null || req.getBuId() != null) {
            validateScopePayload(tenantId, scope, companyId, buId);
            b.setScope(scope);
            b.setCompanyId(scope == PortalHostBindingScope.TENANT ? null : companyId);
            b.setBuId(scope == PortalHostBindingScope.BUSINESS_UNIT ? buId : null);
        }
        if (req.isClearVerifiedAt()) {
            b.setVerifiedAt(null);
        } else if (req.getVerifiedAt() != null) {
            b.setVerifiedAt(req.getVerifiedAt());
        }
        PortalHostBinding saved = bindingRepository.save(b);
        coreDomainAuditService.portalHostBindingUpdated(tenantId, auditBefore, saved);
        return saved;
    }

    @Transactional
    public void delete(UUID tenantId, UUID bindingId) {
        PortalHostBinding b = get(tenantId, bindingId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.portalHostBinding(b);
        UUID id = b.getBindingId();
        bindingRepository.delete(b);
        coreDomainAuditService.portalHostBindingDeleted(tenantId, id, auditBefore);
    }

    private void validateScopePayload(UUID tenantId, PortalHostBindingScope scope, UUID companyId, UUID buId) {
        switch (scope) {
            case TENANT -> {
                if (companyId != null || buId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "TENANT scope must not include company or bu");
                }
            }
            case COMPANY -> {
                if (companyId == null || buId != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "COMPANY scope requires companyId");
                }
                companyRepository.findByCompanyIdAndTenantId(companyId, tenantId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company not found"));
            }
            case BUSINESS_UNIT -> {
                if (companyId == null || buId == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "BUSINESS_UNIT scope requires companyId and buId");
                }
                BusinessUnit bu = businessUnitRepository.findByBuIdAndTenantId(buId, tenantId)
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Business unit not found"));
                if (!bu.getCompanyId().equals(companyId)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Business unit does not belong to company");
                }
            }
        }
    }

    public static PortalDtos.PortalHostBindingDto toDto(PortalHostBinding b) {
        return new PortalDtos.PortalHostBindingDto(
                b.getBindingId(),
                b.getHostname(),
                b.getScope(),
                b.getTenantId(),
                b.getCompanyId(),
                b.getBuId(),
                b.getVerifiedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt()
        );
    }
}
