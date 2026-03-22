package com.erp.coreservice.service;

import com.erp.coreservice.domain.Country;
import com.erp.coreservice.repository.CountryRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CountryService {

    private final CountryRepository countryRepository;

    public CountryService(CountryRepository countryRepository) {
        this.countryRepository = countryRepository;
    }

    @Transactional(readOnly = true)
    public List<Country> listAll() {
        return countryRepository.findAll(Sort.by("name"));
    }
}
