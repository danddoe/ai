package com.erp.jwt.tck;

/**
 * Canonical literals shared with IAM defaults and resource-server parsers.
 * See {@code design/JWT_access_token_contract.md}.
 */
public final class JwtContractConstants {

    private JwtContractConstants() {}

    /** Same default string as IAM / entity-builder / global-search when {@code JWT_SECRET} env is unset. */
    public static final String DEFAULT_DEV_SECRET = "erp-iam-dev-secret-key-at-least-256-bits-long-for-hs256";

    public static final String DEFAULT_ISSUER = "erp-iam";

    public static final String DEFAULT_AUDIENCE = "erp-api";

    /** Typical access token TTL for contract tests (seconds). */
    public static final long DEFAULT_ACCESS_TTL_SECONDS = 900L;
}
