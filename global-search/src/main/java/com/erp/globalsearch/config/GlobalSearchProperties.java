package com.erp.globalsearch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "global-search")
public class GlobalSearchProperties {

    private String iamBaseUrl = "http://localhost:8080";
    private String entityBuilderBaseUrl = "http://localhost:8081";
    private long upstreamTimeoutMs = 2000;
    private int defaultLimitPerCategory = 12;

    public String getIamBaseUrl() {
        return iamBaseUrl;
    }

    public void setIamBaseUrl(String iamBaseUrl) {
        this.iamBaseUrl = iamBaseUrl;
    }

    public String getEntityBuilderBaseUrl() {
        return entityBuilderBaseUrl;
    }

    public void setEntityBuilderBaseUrl(String entityBuilderBaseUrl) {
        this.entityBuilderBaseUrl = entityBuilderBaseUrl;
    }

    public long getUpstreamTimeoutMs() {
        return upstreamTimeoutMs;
    }

    public void setUpstreamTimeoutMs(long upstreamTimeoutMs) {
        this.upstreamTimeoutMs = upstreamTimeoutMs;
    }

    public int getDefaultLimitPerCategory() {
        return defaultLimitPerCategory;
    }

    public void setDefaultLimitPerCategory(int defaultLimitPerCategory) {
        this.defaultLimitPerCategory = defaultLimitPerCategory;
    }
}
