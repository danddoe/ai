package com.erp.coreservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gateway")
public class GatewayTrustProperties {

    /**
     * Shared secret the API gateway sends as {@code X-Gateway-Trusted} so core-service can trust
     * {@code X-Resolved-*} headers. Empty = do not trust resolved headers for JWT cross-check.
     */
    private String trustSecret = "";

    public String getTrustSecret() {
        return trustSecret;
    }

    public void setTrustSecret(String trustSecret) {
        this.trustSecret = trustSecret != null ? trustSecret : "";
    }
}
