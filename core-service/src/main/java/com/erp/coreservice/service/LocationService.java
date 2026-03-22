package com.erp.coreservice.service;

import com.erp.coreservice.domain.Location;
import com.erp.coreservice.repository.CountryRepository;
import com.erp.coreservice.repository.LocationRepository;
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
public class LocationService {

    private final LocationRepository locationRepository;
    private final CountryRepository countryRepository;
    private final RegionRepository regionRepository;

    public LocationService(
            LocationRepository locationRepository,
            CountryRepository countryRepository,
            RegionRepository regionRepository
    ) {
        this.locationRepository = locationRepository;
        this.countryRepository = countryRepository;
        this.regionRepository = regionRepository;
    }

    @Transactional
    public Location create(UUID tenantId, OrgDtos.CreateLocationRequest req) {
        String cc = req.getCountryCode().trim().toUpperCase();
        if (cc.length() != 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "countryCode must be ISO 3166-1 alpha-2");
        }
        countryRepository.findById(cc)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown country code"));
        if (req.getRegionId() != null) {
            regionRepository.findByRegionIdAndTenantId(req.getRegionId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Region not found"));
        }
        Location loc = new Location();
        loc.setTenantId(tenantId);
        loc.setLocationName(req.getLocationName().trim());
        loc.setAddressLine1(trimOrNull(req.getAddressLine1()));
        loc.setCity(trimOrNull(req.getCity()));
        loc.setStateProvince(trimOrNull(req.getStateProvince()));
        loc.setPostalCode(trimOrNull(req.getPostalCode()));
        loc.setCountryCode(cc);
        loc.setRegionId(req.getRegionId());
        return locationRepository.save(loc);
    }

    @Transactional(readOnly = true)
    public Location get(UUID tenantId, UUID locationId) {
        return locationRepository.findByLocationIdAndTenantId(locationId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Location not found"));
    }

    @Transactional(readOnly = true)
    public Page<Location> list(UUID tenantId, int page, int pageSize) {
        int p = Math.max(0, page - 1);
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 200);
        return locationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(p, size));
    }

    @Transactional
    public Location patch(UUID tenantId, UUID locationId, OrgDtos.PatchLocationRequest req) {
        Location loc = get(tenantId, locationId);
        if (req.getLocationName() != null && !req.getLocationName().isBlank()) {
            loc.setLocationName(req.getLocationName().trim());
        }
        if (req.getAddressLine1() != null) {
            loc.setAddressLine1(trimOrNull(req.getAddressLine1()));
        }
        if (req.getCity() != null) {
            loc.setCity(trimOrNull(req.getCity()));
        }
        if (req.getStateProvince() != null) {
            loc.setStateProvince(trimOrNull(req.getStateProvince()));
        }
        if (req.getPostalCode() != null) {
            loc.setPostalCode(trimOrNull(req.getPostalCode()));
        }
        if (req.getCountryCode() != null && !req.getCountryCode().isBlank()) {
            String cc = req.getCountryCode().trim().toUpperCase();
            if (cc.length() != 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "countryCode must be ISO 3166-1 alpha-2");
            }
            countryRepository.findById(cc)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Unknown country code"));
            loc.setCountryCode(cc);
        }
        if (req.isClearRegion()) {
            loc.setRegionId(null);
        } else if (req.getRegionId() != null) {
            regionRepository.findByRegionIdAndTenantId(req.getRegionId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Region not found"));
            loc.setRegionId(req.getRegionId());
        }
        return locationRepository.save(loc);
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
