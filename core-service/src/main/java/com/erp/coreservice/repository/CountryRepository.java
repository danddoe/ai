package com.erp.coreservice.repository;

import com.erp.coreservice.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryRepository extends JpaRepository<Country, String> {
}
