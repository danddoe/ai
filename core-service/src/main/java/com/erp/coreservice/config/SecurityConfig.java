package com.erp.coreservice.config;

import com.erp.coreservice.security.GatewayJwtTenantResolvedHeaderConsistencyFilter;
import com.erp.coreservice.security.GatewayResolvedHeaderWrappingFilter;
import com.erp.coreservice.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(GatewayTrustProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public GatewayResolvedHeaderWrappingFilter gatewayResolvedHeaderWrappingFilter(GatewayTrustProperties gatewayTrustProperties) {
        return new GatewayResolvedHeaderWrappingFilter(gatewayTrustProperties);
    }

    @Bean
    public GatewayJwtTenantResolvedHeaderConsistencyFilter gatewayJwtTenantResolvedHeaderConsistencyFilter(
            GatewayTrustProperties gatewayTrustProperties
    ) {
        return new GatewayJwtTenantResolvedHeaderConsistencyFilter(gatewayTrustProperties);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            GatewayResolvedHeaderWrappingFilter gatewayResolvedHeaderWrappingFilter,
            GatewayJwtTenantResolvedHeaderConsistencyFilter gatewayJwtTenantResolvedHeaderConsistencyFilter
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/v1/portal/bootstrap").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayResolvedHeaderWrappingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(gatewayJwtTenantResolvedHeaderConsistencyFilter, JwtAuthenticationFilter.class);
        return http.build();
    }
}
