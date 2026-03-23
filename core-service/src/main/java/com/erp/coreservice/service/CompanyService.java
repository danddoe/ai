package com.erp.coreservice.service;

import com.erp.coreservice.audit.CoreDomainAuditService;
import com.erp.coreservice.audit.CoreEntityAuditSnapshots;
import com.erp.coreservice.domain.Company;
import com.erp.coreservice.domain.CompanyHierarchyHistory;
import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.repository.BusinessUnitRepository;
import com.erp.coreservice.repository.CompanyHierarchyHistoryRepository;
import com.erp.coreservice.repository.CompanyRepository;
import com.erp.coreservice.web.ApiException;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyHierarchyHistoryRepository companyHierarchyHistoryRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final CoreDomainAuditService coreDomainAuditService;

    public CompanyService(
            CompanyRepository companyRepository,
            CompanyHierarchyHistoryRepository companyHierarchyHistoryRepository,
            BusinessUnitRepository businessUnitRepository,
            CoreDomainAuditService coreDomainAuditService
    ) {
        this.companyRepository = companyRepository;
        this.companyHierarchyHistoryRepository = companyHierarchyHistoryRepository;
        this.businessUnitRepository = businessUnitRepository;
        this.coreDomainAuditService = coreDomainAuditService;
    }

    @Transactional
    public Company create(UUID tenantId, OrgDtos.CreateCompanyRequest req) {
        if (req.getParentCompanyId() != null) {
            companyRepository.findByCompanyIdAndTenantId(req.getParentCompanyId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent company not found"));
        }
        Company c = new Company();
        c.setTenantId(tenantId);
        c.setParentCompanyId(req.getParentCompanyId());
        c.setCompanyName(req.getCompanyName().trim());
        c.setOwnershipPct(req.getOwnershipPct());
        c.setBaseCurrency(req.getBaseCurrency().trim().toUpperCase());
        if (c.getBaseCurrency().length() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "baseCurrency must be ISO 4217 (3 letters)");
        }
        if (req.getSlug() != null && !req.getSlug().isBlank()) {
            c.setSlug(req.getSlug().trim());
        }
        if (req.getAlias() != null && !req.getAlias().isBlank()) {
            c.setAlias(req.getAlias().trim());
        }
        Company saved = companyRepository.save(c);
        HierarchyCycleChecker.assertNoCompanyParentCycle(
                companyRepository, tenantId, saved.getCompanyId(), saved.getParentCompanyId());
        openCompanyHistory(tenantId, saved.getParentCompanyId(), saved.getCompanyId(), saved.getOwnershipPct(), LocalDate.now());
        coreDomainAuditService.companyCreated(tenantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Company get(UUID tenantId, UUID companyId) {
        return companyRepository.findByCompanyIdAndTenantId(companyId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Company not found"));
    }

    @Transactional(readOnly = true)
    public Page<Company> list(UUID tenantId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        return companyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(p, size));
    }

    @Transactional
    public Company patch(UUID tenantId, UUID companyId, OrgDtos.PatchCompanyRequest req) {
        Company c = get(tenantId, companyId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.company(c);
        UUID oldParent = c.getParentCompanyId();
        BigDecimal oldOwnership = c.getOwnershipPct();
        boolean hierarchyChanged = false;

        if (req.isClearParentCompany()) {
            if (oldParent != null) {
                c.setParentCompanyId(null);
                hierarchyChanged = true;
            }
        } else if (req.getParentCompanyId() != null) {
            companyRepository.findByCompanyIdAndTenantId(req.getParentCompanyId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent company not found"));
            if (!Objects.equals(oldParent, req.getParentCompanyId())) {
                HierarchyCycleChecker.assertNoCompanyParentCycle(
                        companyRepository, tenantId, companyId, req.getParentCompanyId());
                c.setParentCompanyId(req.getParentCompanyId());
                hierarchyChanged = true;
            }
        }

        if (req.isClearOwnershipPct()) {
            if (oldOwnership != null) {
                c.setOwnershipPct(null);
                hierarchyChanged = true;
            }
        } else if (req.getOwnershipPct() != null) {
            if (!sameBigDecimal(oldOwnership, req.getOwnershipPct())) {
                c.setOwnershipPct(req.getOwnershipPct());
                hierarchyChanged = true;
            }
        }

        if (req.getCompanyName() != null && !req.getCompanyName().isBlank()) {
            c.setCompanyName(req.getCompanyName().trim());
        }
        if (req.isClearSlug()) {
            c.setSlug(null);
        } else if (req.getSlug() != null) {
            String s = req.getSlug().trim();
            c.setSlug(s.isEmpty() ? null : s);
        }
        if (req.isClearAlias()) {
            c.setAlias(null);
        } else if (req.getAlias() != null) {
            String a = req.getAlias().trim();
            c.setAlias(a.isEmpty() ? null : a);
        }
        if (req.getBaseCurrency() != null && !req.getBaseCurrency().isBlank()) {
            String bc = req.getBaseCurrency().trim().toUpperCase();
            if (bc.length() != 3) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "baseCurrency must be ISO 4217 (3 letters)");
            }
            c.setBaseCurrency(bc);
        }

        if (req.isClearDefaultPortalBu()) {
            c.setDefaultPortalBuId(null);
        } else if (req.getDefaultPortalBuId() != null) {
            assertDefaultPortalBuForCompany(tenantId, companyId, req.getDefaultPortalBuId());
            c.setDefaultPortalBuId(req.getDefaultPortalBuId());
        }

        Company saved = companyRepository.save(c);
        if (hierarchyChanged) {
            LocalDate today = LocalDate.now();
            closeOpenCompanyHistory(tenantId, companyId, today);
            openCompanyHistory(tenantId, saved.getParentCompanyId(), saved.getCompanyId(), saved.getOwnershipPct(), today);
        }
        coreDomainAuditService.companyUpdated(tenantId, auditBefore, saved);
        return saved;
    }

    private void assertDefaultPortalBuForCompany(UUID tenantId, UUID companyId, UUID buId) {
        BusinessUnit bu = businessUnitRepository.findByBuIdAndTenantId(buId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Business unit not found"));
        if (!bu.getCompanyId().equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "defaultPortalBuId must belong to this company");
        }
    }

    private static boolean sameBigDecimal(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    private void closeOpenCompanyHistory(UUID tenantId, UUID childCompanyId, LocalDate endDate) {
        companyHierarchyHistoryRepository
                .findByTenantIdAndChildCompanyIdAndEndDateIsNull(tenantId, childCompanyId)
                .ifPresent(h -> {
                    h.setEndDate(endDate);
                    companyHierarchyHistoryRepository.save(h);
                });
    }

    private void openCompanyHistory(
            UUID tenantId,
            UUID parentCompanyId,
            UUID childCompanyId,
            BigDecimal ownershipPct,
            LocalDate startDate
    ) {
        CompanyHierarchyHistory h = new CompanyHierarchyHistory();
        h.setTenantId(tenantId);
        h.setParentCompanyId(parentCompanyId);
        h.setChildCompanyId(childCompanyId);
        h.setOwnershipPct(ownershipPct);
        h.setStartDate(startDate);
        h.setEndDate(null);
        companyHierarchyHistoryRepository.save(h);
    }
}
