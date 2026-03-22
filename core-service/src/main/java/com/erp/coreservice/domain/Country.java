package com.erp.coreservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "countries")
public class Country {

    @Id
    @Column(name = "country_code", nullable = false, length = 2)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String countryCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String alpha3;

    @Column(name = "numeric_code", length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String numericCode;

    @Column(name = "slug", length = 128)
    private String slug;

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAlpha3() {
        return alpha3;
    }

    public void setAlpha3(String alpha3) {
        this.alpha3 = alpha3;
    }

    public String getNumericCode() {
        return numericCode;
    }

    public void setNumericCode(String numericCode) {
        this.numericCode = numericCode;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }
}
