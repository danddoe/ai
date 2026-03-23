package com.erp.coreservice.web.v1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrgDtos {

    public record PageResponse<T>(java.util.List<T> items, int page, int pageSize, long total) {
    }

    public record CountryDto(String countryCode, String name, String alpha3, String numericCode) {
    }

    public record CompanyDto(
            UUID companyId,
            UUID tenantId,
            UUID parentCompanyId,
            String companyName,
            String slug,
            String alias,
            BigDecimal ownershipPct,
            String baseCurrency,
            UUID defaultPortalBuId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreateCompanyRequest {
        @NotBlank
        @Size(max = 255)
        private String companyName;

        private UUID parentCompanyId;

        private BigDecimal ownershipPct;

        @NotBlank
        @Size(min = 3, max = 3)
        private String baseCurrency;

        @Size(max = 128)
        private String slug;

        @Size(max = 255)
        private String alias;

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public UUID getParentCompanyId() {
            return parentCompanyId;
        }

        public void setParentCompanyId(UUID parentCompanyId) {
            this.parentCompanyId = parentCompanyId;
        }

        public BigDecimal getOwnershipPct() {
            return ownershipPct;
        }

        public void setOwnershipPct(BigDecimal ownershipPct) {
            this.ownershipPct = ownershipPct;
        }

        public String getBaseCurrency() {
            return baseCurrency;
        }

        public void setBaseCurrency(String baseCurrency) {
            this.baseCurrency = baseCurrency;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }
    }

    public static class PatchCompanyRequest {
        @Size(max = 255)
        private String companyName;

        @Size(max = 128)
        private String slug;

        @Size(max = 255)
        private String alias;

        @Size(min = 3, max = 3)
        private String baseCurrency;

        private UUID parentCompanyId;

        /** When true, store as root company (clears parent). */
        private boolean clearParentCompany;

        private BigDecimal ownershipPct;

        private boolean clearOwnershipPct;

        private UUID defaultPortalBuId;

        private boolean clearDefaultPortalBu;

        private boolean clearSlug;

        private boolean clearAlias;

        public String getCompanyName() {
            return companyName;
        }

        public void setCompanyName(String companyName) {
            this.companyName = companyName;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getBaseCurrency() {
            return baseCurrency;
        }

        public void setBaseCurrency(String baseCurrency) {
            this.baseCurrency = baseCurrency;
        }

        public UUID getParentCompanyId() {
            return parentCompanyId;
        }

        public void setParentCompanyId(UUID parentCompanyId) {
            this.parentCompanyId = parentCompanyId;
        }

        public boolean isClearParentCompany() {
            return clearParentCompany;
        }

        public void setClearParentCompany(boolean clearParentCompany) {
            this.clearParentCompany = clearParentCompany;
        }

        public BigDecimal getOwnershipPct() {
            return ownershipPct;
        }

        public void setOwnershipPct(BigDecimal ownershipPct) {
            this.ownershipPct = ownershipPct;
        }

        public boolean isClearOwnershipPct() {
            return clearOwnershipPct;
        }

        public void setClearOwnershipPct(boolean clearOwnershipPct) {
            this.clearOwnershipPct = clearOwnershipPct;
        }

        public UUID getDefaultPortalBuId() {
            return defaultPortalBuId;
        }

        public void setDefaultPortalBuId(UUID defaultPortalBuId) {
            this.defaultPortalBuId = defaultPortalBuId;
        }

        public boolean isClearDefaultPortalBu() {
            return clearDefaultPortalBu;
        }

        public void setClearDefaultPortalBu(boolean clearDefaultPortalBu) {
            this.clearDefaultPortalBu = clearDefaultPortalBu;
        }

        public boolean isClearSlug() {
            return clearSlug;
        }

        public void setClearSlug(boolean clearSlug) {
            this.clearSlug = clearSlug;
        }

        public boolean isClearAlias() {
            return clearAlias;
        }

        public void setClearAlias(boolean clearAlias) {
            this.clearAlias = clearAlias;
        }
    }

    public record BusinessUnitDto(
            UUID buId,
            UUID tenantId,
            UUID companyId,
            UUID parentBuId,
            String buType,
            String buName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreateBusinessUnitRequest {
        @NotNull
        private UUID companyId;

        private UUID parentBuId;

        @NotBlank
        @Size(max = 100)
        private String buType;

        @NotBlank
        @Size(max = 255)
        private String buName;

        public UUID getCompanyId() {
            return companyId;
        }

        public void setCompanyId(UUID companyId) {
            this.companyId = companyId;
        }

        public UUID getParentBuId() {
            return parentBuId;
        }

        public void setParentBuId(UUID parentBuId) {
            this.parentBuId = parentBuId;
        }

        public String getBuType() {
            return buType;
        }

        public void setBuType(String buType) {
            this.buType = buType;
        }

        public String getBuName() {
            return buName;
        }

        public void setBuName(String buName) {
            this.buName = buName;
        }
    }

    public static class PatchBusinessUnitRequest {
        @Size(max = 100)
        private String buType;

        @Size(max = 255)
        private String buName;

        private UUID parentBuId;

        private boolean clearParentBu;

        public String getBuType() {
            return buType;
        }

        public void setBuType(String buType) {
            this.buType = buType;
        }

        public String getBuName() {
            return buName;
        }

        public void setBuName(String buName) {
            this.buName = buName;
        }

        public UUID getParentBuId() {
            return parentBuId;
        }

        public void setParentBuId(UUID parentBuId) {
            this.parentBuId = parentBuId;
        }

        public boolean isClearParentBu() {
            return clearParentBu;
        }

        public void setClearParentBu(boolean clearParentBu) {
            this.clearParentBu = clearParentBu;
        }
    }

    public record RegionDto(
            UUID regionId,
            UUID tenantId,
            UUID parentRegionId,
            String regionCode,
            String regionName,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreateRegionRequest {
        private UUID parentRegionId;

        @NotBlank
        @Size(max = 64)
        private String regionCode;

        @NotBlank
        @Size(max = 255)
        private String regionName;

        public UUID getParentRegionId() {
            return parentRegionId;
        }

        public void setParentRegionId(UUID parentRegionId) {
            this.parentRegionId = parentRegionId;
        }

        public String getRegionCode() {
            return regionCode;
        }

        public void setRegionCode(String regionCode) {
            this.regionCode = regionCode;
        }

        public String getRegionName() {
            return regionName;
        }

        public void setRegionName(String regionName) {
            this.regionName = regionName;
        }
    }

    public static class PatchRegionRequest {
        @Size(max = 64)
        private String regionCode;

        @Size(max = 255)
        private String regionName;

        private UUID parentRegionId;

        private boolean clearParentRegion;

        public String getRegionCode() {
            return regionCode;
        }

        public void setRegionCode(String regionCode) {
            this.regionCode = regionCode;
        }

        public String getRegionName() {
            return regionName;
        }

        public void setRegionName(String regionName) {
            this.regionName = regionName;
        }

        public UUID getParentRegionId() {
            return parentRegionId;
        }

        public void setParentRegionId(UUID parentRegionId) {
            this.parentRegionId = parentRegionId;
        }

        public boolean isClearParentRegion() {
            return clearParentRegion;
        }

        public void setClearParentRegion(boolean clearParentRegion) {
            this.clearParentRegion = clearParentRegion;
        }
    }

    public record LocationDto(
            UUID locationId,
            UUID tenantId,
            String locationName,
            String addressLine1,
            String city,
            String stateProvince,
            String postalCode,
            String countryCode,
            UUID regionId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreateLocationRequest {
        @NotBlank
        @Size(max = 255)
        private String locationName;

        @Size(max = 500)
        private String addressLine1;

        @Size(max = 255)
        private String city;

        @Size(max = 100)
        private String stateProvince;

        @Size(max = 32)
        private String postalCode;

        @NotBlank
        @Size(min = 2, max = 2)
        private String countryCode;

        private UUID regionId;

        public String getLocationName() {
            return locationName;
        }

        public void setLocationName(String locationName) {
            this.locationName = locationName;
        }

        public String getAddressLine1() {
            return addressLine1;
        }

        public void setAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getStateProvince() {
            return stateProvince;
        }

        public void setStateProvince(String stateProvince) {
            this.stateProvince = stateProvince;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }

        public UUID getRegionId() {
            return regionId;
        }

        public void setRegionId(UUID regionId) {
            this.regionId = regionId;
        }
    }

    public static class PatchLocationRequest {
        @Size(max = 255)
        private String locationName;

        @Size(max = 500)
        private String addressLine1;

        @Size(max = 255)
        private String city;

        @Size(max = 100)
        private String stateProvince;

        @Size(max = 32)
        private String postalCode;

        @Size(min = 2, max = 2)
        private String countryCode;

        private UUID regionId;

        private boolean clearRegion;

        public String getLocationName() {
            return locationName;
        }

        public void setLocationName(String locationName) {
            this.locationName = locationName;
        }

        public String getAddressLine1() {
            return addressLine1;
        }

        public void setAddressLine1(String addressLine1) {
            this.addressLine1 = addressLine1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getStateProvince() {
            return stateProvince;
        }

        public void setStateProvince(String stateProvince) {
            this.stateProvince = stateProvince;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }

        public UUID getRegionId() {
            return regionId;
        }

        public void setRegionId(UUID regionId) {
            this.regionId = regionId;
        }

        public boolean isClearRegion() {
            return clearRegion;
        }

        public void setClearRegion(boolean clearRegion) {
            this.clearRegion = clearRegion;
        }
    }

    public record PropertyDto(
            UUID propertyId,
            UUID tenantId,
            UUID companyId,
            UUID locationId,
            String propertyName,
            String propertyType,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreatePropertyRequest {
        @NotNull
        private UUID companyId;

        @NotNull
        private UUID locationId;

        @NotBlank
        @Size(max = 255)
        private String propertyName;

        @NotBlank
        @Size(max = 100)
        private String propertyType;

        public UUID getCompanyId() {
            return companyId;
        }

        public void setCompanyId(UUID companyId) {
            this.companyId = companyId;
        }

        public UUID getLocationId() {
            return locationId;
        }

        public void setLocationId(UUID locationId) {
            this.locationId = locationId;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyType() {
            return propertyType;
        }

        public void setPropertyType(String propertyType) {
            this.propertyType = propertyType;
        }
    }

    public static class PatchPropertyRequest {
        @Size(max = 255)
        private String propertyName;

        @Size(max = 100)
        private String propertyType;

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getPropertyType() {
            return propertyType;
        }

        public void setPropertyType(String propertyType) {
            this.propertyType = propertyType;
        }
    }

    public record PropertyUnitDto(
            UUID unitId,
            UUID tenantId,
            UUID propertyId,
            String unitNumber,
            BigDecimal squareFootage,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public static class CreatePropertyUnitRequest {
        @NotNull
        private UUID propertyId;

        @NotBlank
        @Size(max = 64)
        private String unitNumber;

        private BigDecimal squareFootage;

        @NotBlank
        @Size(max = 50)
        private String status;

        public UUID getPropertyId() {
            return propertyId;
        }

        public void setPropertyId(UUID propertyId) {
            this.propertyId = propertyId;
        }

        public String getUnitNumber() {
            return unitNumber;
        }

        public void setUnitNumber(String unitNumber) {
            this.unitNumber = unitNumber;
        }

        public BigDecimal getSquareFootage() {
            return squareFootage;
        }

        public void setSquareFootage(BigDecimal squareFootage) {
            this.squareFootage = squareFootage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class PatchPropertyUnitRequest {
        @Size(max = 64)
        private String unitNumber;

        private BigDecimal squareFootage;

        @Size(max = 50)
        private String status;

        private boolean clearSquareFootage;

        public String getUnitNumber() {
            return unitNumber;
        }

        public void setUnitNumber(String unitNumber) {
            this.unitNumber = unitNumber;
        }

        public BigDecimal getSquareFootage() {
            return squareFootage;
        }

        public void setSquareFootage(BigDecimal squareFootage) {
            this.squareFootage = squareFootage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isClearSquareFootage() {
            return clearSquareFootage;
        }

        public void setClearSquareFootage(boolean clearSquareFootage) {
            this.clearSquareFootage = clearSquareFootage;
        }
    }
}
