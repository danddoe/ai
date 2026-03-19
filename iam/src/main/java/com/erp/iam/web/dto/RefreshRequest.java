package com.erp.iam.web.dto;

/**
 * refreshToken is optional when the client sends the refresh JWT in the httpOnly cookie ({@code app.auth.refresh-token-in-cookie}).
 */
public class RefreshRequest {

    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
