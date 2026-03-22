package com.erp.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthCookieProperties {

    /**
     * When true: login/refresh set an httpOnly cookie with the refresh token and omit refreshToken from JSON.
     * Refresh and logout accept the token from that cookie if the body omits refreshToken.
     * Default true (see {@code application.yml}); IAM E2E disables via CLI.
     */
    private boolean refreshTokenInCookie = true;

    private String refreshCookieName = "erp_refresh";

    /** Use true behind HTTPS in production. */
    private boolean refreshCookieSecure = false;

    public boolean isRefreshTokenInCookie() {
        return refreshTokenInCookie;
    }

    public void setRefreshTokenInCookie(boolean refreshTokenInCookie) {
        this.refreshTokenInCookie = refreshTokenInCookie;
    }

    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    public void setRefreshCookieName(String refreshCookieName) {
        this.refreshCookieName = refreshCookieName;
    }

    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    public void setRefreshCookieSecure(boolean refreshCookieSecure) {
        this.refreshCookieSecure = refreshCookieSecure;
    }
}
