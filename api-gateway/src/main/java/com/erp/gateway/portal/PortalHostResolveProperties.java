package com.erp.gateway.portal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.portal-host")
public class PortalHostResolveProperties {

    private String coreServiceUrl = "http://localhost:8084";

    private int cacheTtlSeconds = 60;

    /**
     * Must match core-service {@code app.gateway.trust-secret}. Empty disables resolution filter.
     */
    private String trustSecret = "";

    public String getCoreServiceUrl() {
        return coreServiceUrl;
    }

    public void setCoreServiceUrl(String coreServiceUrl) {
        this.coreServiceUrl = coreServiceUrl;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(int cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public String getTrustSecret() {
        return trustSecret;
    }

    public void setTrustSecret(String trustSecret) {
        this.trustSecret = trustSecret != null ? trustSecret : "";
    }
}
