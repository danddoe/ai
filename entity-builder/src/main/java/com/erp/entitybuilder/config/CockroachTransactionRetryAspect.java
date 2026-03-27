package com.erp.entitybuilder.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * CockroachDB often surfaces serializable restarts as {@code ReadWithinUncertaintyIntervalError}
 * ("restart transaction"), which Hibernate wraps as {@link org.hibernate.exception.LockAcquisitionException}.
 * Retries must run the whole transaction again from an outer boundary — not an inner join of an existing
 * transaction — so this advice wraps HTTP controller entry points only (see IAM {@code AuthRefreshRetryService}
 * for the same class of errors on a single service call).
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CockroachTransactionRetryAspect {

    private static final Logger log = LoggerFactory.getLogger(CockroachTransactionRetryAspect.class);

    private static final int MAX_ATTEMPTS = 10;
    private static final long INITIAL_BACKOFF_MS = 50L;
    private static final long MAX_BACKOFF_MS = 800L;

    @Around("execution(public * com.erp.entitybuilder.web..*.*(..))")
    public Object retryCockroachSerialization(ProceedingJoinPoint pjp) throws Throwable {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return pjp.proceed();
            } catch (Exception ex) {
                if (attempt == MAX_ATTEMPTS || !isCockroachRetryable(ex)) {
                    throw ex;
                }
                log.debug(
                        "CockroachDB retryable transaction error (attempt {}/{}): {}",
                        attempt,
                        MAX_ATTEMPTS,
                        ex.toString()
                );
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }
        throw new IllegalStateException("unreachable");
    }

    static boolean isCockroachRetryable(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConcurrencyFailureException || t instanceof PessimisticLockingFailureException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                if (msg.contains("restart transaction") || msg.contains("ReadWithinUncertaintyInterval")) {
                    return true;
                }
                if (msg.contains("TransactionRetryWithProtoRefreshError")) {
                    return true;
                }
            }
        }
        return false;
    }
}
