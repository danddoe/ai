package com.erp.audit;

public final class AuditResourceTypes {

    public static final String ENTITY_RECORD = "EntityRecord";

    /** Same as {@link #fromDataPath} for {@code loan_application}; kept for call-site readability. */
    public static final String LOAN_APPLICATION = fromDataPath("loan_application");

    /**
     * Derives {@code audit_log.resource_type} from the {@link AuditableResource#path()} suffix:
     * <ul>
     *   <li>{@code core.{snake}} → {@link #coreMasterData} on the part after {@code core.}</li>
     *   <li>otherwise → PascalCase from dot- and underscore-separated segments (e.g. {@code loan_application} → {@code LoanApplication})</li>
     * </ul>
     */
    public static String fromDataPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must be non-blank");
        }
        String p = path.trim();
        if (p.startsWith("core.")) {
            return coreMasterData(p.substring("core.".length()));
        }
        return pascalSnakePath(p);
    }

    private static String pascalSnakePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String dotPart : path.split("\\.")) {
            if (dotPart.isEmpty()) {
                continue;
            }
            for (String seg : dotPart.split("_")) {
                if (seg.isEmpty()) {
                    continue;
                }
                sb.append(Character.toUpperCase(seg.charAt(0)));
                if (seg.length() > 1) {
                    sb.append(seg, 1, seg.length());
                }
            }
        }
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("path has no segments: " + path);
        }
        return sb.toString();
    }

    /**
     * {@code audit_log.resource_type} for core-service master rows: {@code Core} + PascalCase from
     * snake_case {@code resourceKey} (e.g. {@code business_unit} → {@code CoreBusinessUnit}).
     */
    public static String coreMasterData(String resourceKey) {
        if (resourceKey == null || resourceKey.isBlank()) {
            throw new IllegalArgumentException("resourceKey must be non-blank");
        }
        String key = resourceKey.trim();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder("Core");
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p, 1, p.length());
            }
        }
        return sb.toString();
    }

    private AuditResourceTypes() {}
}
