package com.erp.coreservice.web.v1;

import com.erp.coreservice.domain.Country;
import com.erp.coreservice.service.CountryService;
import com.erp.coreservice.web.v1.dto.OrgDtos;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/countries")
public class CountriesController {

    private final CountryService countryService;

    public CountriesController(CountryService countryService) {
        this.countryService = countryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('master_data:countries:read') or @coreServiceSecurity.hasElevatedAccess()")
    public List<OrgDtos.CountryDto> list() {
        return countryService.listAll().stream().map(CountriesController::toDto).toList();
    }

    private static OrgDtos.CountryDto toDto(Country c) {
        return new OrgDtos.CountryDto(c.getCountryCode(), c.getName(), c.getAlpha3(), c.getNumericCode());
    }
}
