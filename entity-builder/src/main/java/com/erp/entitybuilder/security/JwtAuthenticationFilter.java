package com.erp.entitybuilder.security;

import com.erp.entitybuilder.service.JwtService;
import com.erp.entitybuilder.service.JwtService.JwtClaims;
import com.erp.entitybuilder.service.JwtService.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token)) {
                JwtClaims claims = jwtService.parseToken(token);
                if (!claims.isRefresh()) {
                    List<SimpleGrantedAuthority> authorities = Stream.concat(
                                    claims.getRoles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)),
                                    claims.getPermissions().stream().map(SimpleGrantedAuthority::new)
                            )
                            .collect(Collectors.toList());

                    TenantPrincipal principal = new TenantPrincipal(
                            claims.getUserId(),
                            claims.getTenantId(),
                            claims.getEmail(),
                            claims.getRoles(),
                            claims.getPermissions()
                    );

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (InvalidTokenException ignored) {
            // Leave context unauthenticated
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}

