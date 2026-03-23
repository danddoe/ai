package com.erp.audit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves {@link AuditableResource} from a domain class (cached). Supports runtime subclasses
 * (e.g. Hibernate proxies) because the annotation is {@link Inherited}.
 */
public final class AuditableResourceSupport {

    private static final ConcurrentHashMap<Class<?>, Resolved> CACHE = new ConcurrentHashMap<>();

    private AuditableResourceSupport() {
    }

    private record Resolved(String path, String resourceType) {
    }

    public static String path(Class<?> entityType) {
        return resolve(entityType).path();
    }

    public static String action(Class<?> entityType, String verb) {
        return AuditActions.dataPath(resolve(entityType).path(), verb);
    }

    public static String resourceType(Class<?> entityType) {
        return resolve(entityType).resourceType();
    }

    private static Resolved resolve(Class<?> entityType) {
        if (entityType == null) {
            throw new IllegalArgumentException("entityType required");
        }
        return CACHE.computeIfAbsent(entityType, AuditableResourceSupport::readResolved);
    }

    private static Resolved readResolved(Class<?> start) {
        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            AuditableResource ann = c.getAnnotation(AuditableResource.class);
            if (ann != null) {
                String path = ann.path().trim();
                if (path.isEmpty()) {
                    throw new IllegalStateException("@AuditableResource.path must be non-blank on " + c.getName());
                }
                String explicitRt = ann.resourceType().trim();
                String rt = explicitRt.isEmpty() ? AuditResourceTypes.fromDataPath(path) : explicitRt;
                return new Resolved(path, rt);
            }
        }
        throw new IllegalStateException(
                "Missing @AuditableResource on " + start.getName() + " (or its superclasses)");
    }
}
