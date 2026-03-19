package com.erp.entitybuilder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String issuer = "erp-iam";
    private String audience = "erp-api";
    private long accessTokenExpirationSeconds = 900;   // 15 min
    private long refreshTokenExpirationSeconds = 604800; // 7 days

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public long getAccessTokenExpirationSeconds() { return accessTokenExpirationSeconds; }
    public void setAccessTokenExpirationSeconds(long accessTokenExpirationSeconds) { this.accessTokenExpirationSeconds = accessTokenExpirationSeconds; }

    public long getRefreshTokenExpirationSeconds() { return refreshTokenExpirationSeconds; }
    public void setRefreshTokenExpirationSeconds(long refreshTokenExpirationSeconds) { this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds; }
}

