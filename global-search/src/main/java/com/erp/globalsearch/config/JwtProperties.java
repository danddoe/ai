package com.erp.globalsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer = "erp-iam";
    private String audience = "erp-api";

    public String getIssuer() {
        return issuer == null || issuer.isBlank() ? "erp-iam" : issuer.trim();
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience == null || audience.isBlank() ? "erp-api" : audience.trim();
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
