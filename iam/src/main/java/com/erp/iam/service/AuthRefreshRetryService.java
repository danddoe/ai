package com.erp.iam.service;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * Retries refresh outside {@link AuthService#refresh} so each attempt runs in its own transaction.
 * CockroachDB may still surface transient conflicts as {@link PessimisticLockingFailureException}.
 */
@Service
public class AuthRefreshRetryService {

    private final AuthService authService;

    public AuthRefreshRetryService(AuthService authService) {
        this.authService = authService;
    }

    @Retryable(
            retryFor = PessimisticLockingFailureException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 50, multiplier = 2, maxDelay = 800)
    )
    public AuthService.TokenResult refresh(String refreshTokenValue) {
        return authService.refresh(refreshTokenValue);
    }
}
