package com.erp.iam.web;

import com.erp.iam.config.AuthCookieProperties;
import com.erp.iam.config.JwtProperties;
import com.erp.iam.service.AuthRefreshRetryService;
import com.erp.iam.service.AuthService;
import com.erp.iam.service.AuthService.AuthException;
import com.erp.iam.service.AuthService.TokenResult;
import com.erp.iam.service.JwtService.InvalidTokenException;
import com.erp.iam.web.dto.LoginRequest;
import com.erp.iam.web.dto.RefreshRequest;
import com.erp.iam.web.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRefreshRetryService authRefreshRetryService;
    private final AuthCookieProperties authCookieProperties;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService,
                            AuthRefreshRetryService authRefreshRetryService,
                            AuthCookieProperties authCookieProperties,
                            JwtProperties jwtProperties) {
        this.authService = authService;
        this.authRefreshRetryService = authRefreshRetryService;
        this.authCookieProperties = authCookieProperties;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResult result = authService.login(
                request.getTenantSlugOrId(),
                request.getEmail(),
                request.getPassword()
        );
        String refreshForJson = authCookieProperties.isRefreshTokenInCookie() ? null : result.getRefreshToken();
        TokenResponse response = new TokenResponse(
                result.getAccessToken(),
                refreshForJson,
                result.getExpiresInSeconds()
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (authCookieProperties.isRefreshTokenInCookie()) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(result.getRefreshToken(), jwtProperties.getRefreshTokenExpirationSeconds()).toString());
        }
        return builder.body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        String refreshCookie = readCookie(httpRequest, authCookieProperties.getRefreshCookieName());
        String refreshValue = (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank())
                ? request.getRefreshToken()
                : refreshCookie;
        if (refreshValue == null || refreshValue.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        TokenResult result = authRefreshRetryService.refresh(refreshValue);
        String refreshForJson = authCookieProperties.isRefreshTokenInCookie() ? null : result.getRefreshToken();
        TokenResponse response = new TokenResponse(
                result.getAccessToken(),
                refreshForJson,
                result.getExpiresInSeconds()
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (authCookieProperties.isRefreshTokenInCookie()) {
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(result.getRefreshToken(), jwtProperties.getRefreshTokenExpirationSeconds()).toString());
        }
        return builder.body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        String refreshCookie = readCookie(httpRequest, authCookieProperties.getRefreshCookieName());
        String refreshValue = (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank())
                ? request.getRefreshToken()
                : refreshCookie;
        if (refreshValue != null && !refreshValue.isBlank()) {
            authService.logout(refreshValue);
        }
        if (authCookieProperties.isRefreshTokenInCookie()) {
            return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(authCookieProperties.getRefreshCookieName(), token)
                .httpOnly(true)
                .secure(authCookieProperties.isRefreshCookieSecure())
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(authCookieProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authCookieProperties.isRefreshCookieSecure())
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }

    private static String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorBody> handleAuthException(AuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorBody> handleInvalidToken(InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody(e.getMessage()));
    }

    public static class ErrorBody {
        private final String error;

        public ErrorBody(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}
