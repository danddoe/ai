package com.erp.coreservice.service;

import com.erp.coreservice.audit.CoreDomainAuditService;
import com.erp.coreservice.audit.CoreEntityAuditSnapshots;
import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.domain.BuHierarchyHistory;
import com.erp.coreservice.repository.BuHierarchyHistoryRepository;
import com.erp.coreservice.repository.BusinessUnitRepository;
import com.erp.coreservice.repository.CompanyRepository;
import com.erp.coreservice.web.ApiException;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class BusinessUnitService {

    private final BusinessUnitRepository businessUnitRepository;
    private final BuHierarchyHistoryRepository buHierarchyHistoryRepository;
    private final CompanyRepository companyRepository;
    private final CoreDomainAuditService coreDomainAuditService;

    public BusinessUnitService(
            BusinessUnitRepository businessUnitRepository,
            BuHierarchyHistoryRepository buHierarchyHistoryRepository,
            CompanyRepository companyRepository,
            CoreDomainAuditService coreDomainAuditService
    ) {
        this.businessUnitRepository = businessUnitRepository;
        this.buHierarchyHistoryRepository = buHierarchyHistoryRepository;
        this.companyRepository = companyRepository;
        this.coreDomainAuditService = coreDomainAuditService;
    }

    @Transactional
    public BusinessUnit create(UUID tenantId, OrgDtos.CreateBusinessUnitRequest req) {
        companyRepository.findByCompanyIdAndTenantId(req.getCompanyId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company not found"));
        if (req.getParentBuId() != null) {
            BusinessUnit parent = businessUnitRepository.findByBuIdAndTenantId(req.getParentBuId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent business unit not found"));
            if (!parent.getCompanyId().equals(req.getCompanyId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent BU must belong to the same company");
            }
        }
        BusinessUnit bu = new BusinessUnit();
        bu.setTenantId(tenantId);
        bu.setCompanyId(req.getCompanyId());
        bu.setParentBuId(req.getParentBuId());
        bu.setBuType(req.getBuType().trim());
        bu.setBuName(req.getBuName().trim());
        BusinessUnit saved = businessUnitRepository.save(bu);
        HierarchyCycleChecker.assertNoBusinessUnitParentCycle(
                businessUnitRepository, tenantId, saved.getBuId(), saved.getParentBuId());
        openBuHistory(tenantId, saved.getParentBuId(), saved.getBuId(), LocalDate.now());
        coreDomainAuditService.businessUnitCreated(tenantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public BusinessUnit get(UUID tenantId, UUID buId) {
        return businessUnitRepository.findByBuIdAndTenantId(buId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Business unit not found"));
    }

    @Transactional(readOnly = true)
    public Page<BusinessUnit> list(UUID tenantId, UUID companyId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        var pr = PageRequest.of(p, size);
        if (companyId != null) {
            companyRepository.findByCompanyIdAndTenantId(companyId, tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company not found"));
            return businessUnitRepository.findByTenantIdAndCompanyIdOrderByCreatedAtDesc(tenantId, companyId, pr);
        }
        return businessUnitRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pr);
    }

    @Transactional
    public BusinessUnit patch(UUID tenantId, UUID buId, OrgDtos.PatchBusinessUnitRequest req) {
        BusinessUnit bu = get(tenantId, buId);
        Map<String, Object> auditBefore = CoreEntityAuditSnapshots.businessUnit(bu);
        UUID oldParent = bu.getParentBuId();
        boolean hierarchyChanged = false;

        if (req.isClearParentBu()) {
            if (oldParent != null) {
                bu.setParentBuId(null);
                hierarchyChanged = true;
            }
        } else if (req.getParentBuId() != null) {
            BusinessUnit parent = businessUnitRepository.findByBuIdAndTenantId(req.getParentBuId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent business unit not found"));
            if (!parent.getCompanyId().equals(bu.getCompanyId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent BU must belong to the same company");
            }
            if (!Objects.equals(oldParent, req.getParentBuId())) {
                HierarchyCycleChecker.assertNoBusinessUnitParentCycle(
                        businessUnitRepository, tenantId, buId, req.getParentBuId());
                bu.setParentBuId(req.getParentBuId());
                hierarchyChanged = true;
            }
        }

        if (req.getBuType() != null && !req.getBuType().isBlank()) {
            bu.setBuType(req.getBuType().trim());
        }
        if (req.getBuName() != null && !req.getBuName().isBlank()) {
            bu.setBuName(req.getBuName().trim());
        }

        BusinessUnit saved = businessUnitRepository.save(bu);
        if (hierarchyChanged) {
            LocalDate today = LocalDate.now();
            closeOpenBuHistory(tenantId, buId, today);
            openBuHistory(tenantId, saved.getParentBuId(), saved.getBuId(), today);
        }
        coreDomainAuditService.businessUnitUpdated(tenantId, auditBefore, saved);
        return saved;
    }

    private void closeOpenBuHistory(UUID tenantId, UUID childBuId, LocalDate endDate) {
        buHierarchyHistoryRepository.findByTenantIdAndChildBuIdAndEndDateIsNull(tenantId, childBuId)
                .ifPresent(h -> {
                    h.setEndDate(endDate);
                    buHierarchyHistoryRepository.save(h);
                });
    }

    private void openBuHistory(UUID tenantId, UUID parentBuId, UUID childBuId, LocalDate startDate) {
        BuHierarchyHistory h = new BuHierarchyHistory();
        h.setTenantId(tenantId);
        h.setParentBuId(parentBuId);
        h.setChildBuId(childBuId);
        h.setStartDate(startDate);
        h.setEndDate(null);
        buHierarchyHistoryRepository.save(h);
    }
}
