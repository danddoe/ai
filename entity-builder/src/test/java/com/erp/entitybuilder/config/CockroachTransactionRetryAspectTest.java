package com.erp.entitybuilder.config;

import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class CockroachTransactionRetryAspectTest {

    @Test
    void detectsReadWithinUncertaintyInCauseChain() {
        var inner = new Exception(
                "ERROR: restart transaction: TransactionRetryWithProtoRefreshError: ReadWithinUncertaintyIntervalError: ..."
        );
        assertThat(CockroachTransactionRetryAspect.isCockroachRetryable(new RuntimeException(inner))).isTrue();
    }

    @Test
    void detectsConcurrencyFailure() {
        assertThat(CockroachTransactionRetryAspect.isCockroachRetryable(new ConcurrencyFailureException("x"))).isTrue();
    }

    @Test
    void ignoresUnrelatedErrors() {
        assertThat(CockroachTransactionRetryAspect.isCockroachRetryable(new IllegalStateException("duplicate key"))).isFalse();
    }
}
