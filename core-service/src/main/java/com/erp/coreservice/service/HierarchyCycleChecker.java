package com.erp.coreservice.service;

import com.erp.coreservice.domain.BusinessUnit;
import com.erp.coreservice.domain.Company;
import com.erp.coreservice.domain.Region;
import com.erp.coreservice.repository.BusinessUnitRepository;
import com.erp.coreservice.repository.CompanyRepository;
import com.erp.coreservice.repository.RegionRepository;
import com.erp.coreservice.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public final class HierarchyCycleChecker {

    private HierarchyCycleChecker() {
    }

    public static void assertNoCompanyParentCycle(
            CompanyRepository repo,
            UUID tenantId,
            UUID companyId,
            UUID newParentId
    ) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(companyId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company cannot be its own parent");
        }
        UUID current = newParentId;
        while (current != null) {
            if (current.equals(companyId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Company parent would create a cycle");
            }
            Company c = repo.findByCompanyIdAndTenantId(current, tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent company not found"));
            current = c.getParentCompanyId();
        }
    }

    public static void assertNoBusinessUnitParentCycle(
            BusinessUnitRepository repo,
            UUID tenantId,
            UUID buId,
            UUID newParentId
    ) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(buId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Business unit cannot be its own parent");
        }
        UUID current = newParentId;
        while (current != null) {
            if (current.equals(buId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Business unit parent would create a cycle");
            }
            BusinessUnit bu = repo.findByBuIdAndTenantId(current, tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent business unit not found"));
            current = bu.getParentBuId();
        }
    }

    public static void assertNoRegionParentCycle(
            RegionRepository repo,
            UUID tenantId,
            UUID regionId,
            UUID newParentId
    ) {
        if (newParentId == null) {
            return;
        }
        if (newParentId.equals(regionId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Region cannot be its own parent");
        }
        UUID current = newParentId;
        while (current != null) {
            if (current.equals(regionId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Region parent would create a cycle");
            }
            Region r = repo.findByRegionIdAndTenantId(current, tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent region not found"));
            current = r.getParentRegionId();
        }
    }
}
