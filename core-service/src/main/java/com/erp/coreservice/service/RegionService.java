package com.erp.coreservice.service;

import com.erp.coreservice.domain.Region;
import com.erp.coreservice.repository.RegionRepository;
import com.erp.coreservice.web.ApiException;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RegionService {

    private final RegionRepository regionRepository;

    public RegionService(RegionRepository regionRepository) {
        this.regionRepository = regionRepository;
    }

    @Transactional
    public Region create(UUID tenantId, OrgDtos.CreateRegionRequest req) {
        if (req.getParentRegionId() != null) {
            regionRepository.findByRegionIdAndTenantId(req.getParentRegionId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent region not found"));
        }
        Region r = new Region();
        r.setTenantId(tenantId);
        r.setParentRegionId(req.getParentRegionId());
        r.setRegionCode(req.getRegionCode().trim());
        r.setRegionName(req.getRegionName().trim());
        Region saved = regionRepository.save(r);
        HierarchyCycleChecker.assertNoRegionParentCycle(
                regionRepository, tenantId, saved.getRegionId(), saved.getParentRegionId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Region get(UUID tenantId, UUID regionId) {
        return regionRepository.findByRegionIdAndTenantId(regionId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Region not found"));
    }

    @Transactional(readOnly = true)
    public Page<Region> list(UUID tenantId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        return regionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(p, size));
    }

    @Transactional
    public Region patch(UUID tenantId, UUID regionId, OrgDtos.PatchRegionRequest req) {
        Region r = get(tenantId, regionId);
        if (req.isClearParentRegion()) {
            r.setParentRegionId(null);
        } else if (req.getParentRegionId() != null) {
            regionRepository.findByRegionIdAndTenantId(req.getParentRegionId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent region not found"));
            HierarchyCycleChecker.assertNoRegionParentCycle(regionRepository, tenantId, regionId, req.getParentRegionId());
            r.setParentRegionId(req.getParentRegionId());
        }
        if (req.getRegionCode() != null && !req.getRegionCode().isBlank()) {
            r.setRegionCode(req.getRegionCode().trim());
        }
        if (req.getRegionName() != null && !req.getRegionName().isBlank()) {
            r.setRegionName(req.getRegionName().trim());
        }
        return regionRepository.save(r);
    }
}
