package com.erp.coreservice.security;

import com.erp.coreservice.config.GatewayTrustProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wraps the request so {@code X-Resolved-*} are only visible when {@code X-Gateway-Trusted} matches.
 * Registered in {@link com.erp.coreservice.config.SecurityConfig} before JWT parsing.
 */
public class GatewayResolvedHeaderWrappingFilter extends OncePerRequestFilter {

    private final GatewayTrustProperties gatewayTrustProperties;

    public GatewayResolvedHeaderWrappingFilter(GatewayTrustProperties gatewayTrustProperties) {
        this.gatewayTrustProperties = gatewayTrustProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String secret = gatewayTrustProperties.getTrustSecret();
        HttpServletRequest wrapped = new GatewayResolvedHeadersRequestWrapper(request, secret);
        filterChain.doFilter(wrapped, response);
    }
}
