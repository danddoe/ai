package com.erp.iam.service;

import com.erp.iam.domain.PortalNavigationItem;
import com.erp.iam.repository.PortalNavigationItemRepository;
import com.erp.iam.security.TenantPrincipal;
import com.erp.iam.web.ApiException;
import com.erp.iam.web.v1.dto.NavigationDtos;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PortalNavigationAdminService {

    /** Matches path only (no {@code ?}), before query validation. */
    private static final Pattern ENTITY_RECORDS_PATH =
            Pattern.compile("^/entities/[0-9a-fA-F-]{36}/records(/new)?$");

    /** Per-entity audit timeline (portal SPA). */
    private static final Pattern ENTITY_AUDIT_PATH =
            Pattern.compile("^/entities/[0-9a-fA-F-]{36}/audit$");

    private static final int MAX_ROUTE_PATH_CHARS = 2048;

    private static final Set<String> ALLOWED_RECORD_QUERY_KEYS =
            Set.of("cols", "inline", "actions", "page", "pageSize", "q", "view", "showRecordId", "showUuid");

    private static final Pattern SLUG_TOKEN = Pattern.compile("^[a-zA-Z0-9_]{1,100}$");

    private static final Pattern UUID_TOKEN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Set<String> ALLOWED_NAV_TYPES = Set.of("internal", "external", "section", "divider");

    private final PortalNavigationItemRepository repository;

    public PortalNavigationAdminService(PortalNavigationItemRepository repository) {
        this.repository = repository;
    }

    private static final Comparator<PortalNavigationItem> ADMIN_LIST_ORDER =
            Comparator.comparing(PortalNavigationItem::getParentId, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparingInt(PortalNavigationItem::getSortOrder)
                    .thenComparing(PortalNavigationItem::getId);

    @Transactional(readOnly = true)
    public NavigationDtos.NavigationAdminListResponse listForPrincipal(
            Authentication authentication,
            TenantPrincipal principal
    ) {
        Set<String> auths = authorities(authentication);
        List<PortalNavigationItem> rows;
        if (canWriteGlobalNav(auths)) {
            rows = new ArrayList<>(repository.findAll());
        } else if (canWriteTenantNav(auths) && principal.getTenantId() != null) {
            rows = new ArrayList<>(repository.findAllByTenantId(principal.getTenantId()));
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Requires portal navigation write access");
        }
        rows.sort(ADMIN_LIST_ORDER);
        List<NavigationDtos.NavigationAdminItemDto> dtos = rows.stream().map(this::toAdminDto).toList();
        return new NavigationDtos.NavigationAdminListResponse(dtos);
    }

    private NavigationDtos.NavigationAdminItemDto toAdminDto(PortalNavigationItem item) {
        return new NavigationDtos.NavigationAdminItemDto(
                item.getId(),
                item.getParentId(),
                item.getTenantId(),
                item.getSortOrder(),
                item.getRoutePath(),
                item.getLabel(),
                item.getDescription(),
                item.getType(),
                item.getIcon(),
                item.getCategoryKey(),
                List.copyOf(item.getSearchKeywords()),
                List.copyOf(item.getRequiredPermissions()),
                List.copyOf(item.getRequiredRoles()),
                item.isActive(),
                item.getDesignStatus(),
                item.getLinkedListViewId(),
                item.getLinkedFormLayoutId()
        );
    }

    /**
     * Fixed portal SPA roots (no query). Use with {@link #isAllowedEntityRecordRoute} for full internal link validation.
     */
    public static boolean isAllowedSimplePortalRoute(String routePath) {
        if (routePath == null) {
            return false;
        }
        String trimmed = routePath.trim();
        if (trimmed.length() > MAX_ROUTE_PATH_CHARS) {
            return false;
        }
        int q = trimmed.indexOf('?');
        if (q >= 0) {
            String query = trimmed.substring(q + 1);
            if (query != null && !query.isBlank()) {
                return false;
            }
            trimmed = trimmed.substring(0, q).trim();
        }
        if (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return "/home".equals(trimmed) || "/entities".equals(trimmed) || "/audit".equals(trimmed);
    }

    /** Internal nav link: entity records URL or a small allow-list of portal routes (e.g. {@code /home}). */
    public static boolean isAllowedInternalSpaRoute(String routePath) {
        return isAllowedEntityRecordRoute(routePath)
                || isAllowedEntityAuditRoute(routePath)
                || isAllowedSimplePortalRoute(routePath);
    }

    /**
     * Allows {@code /entities/{uuid}/audit} with no query string.
     */
    public static boolean isAllowedEntityAuditRoute(String routePath) {
        if (routePath == null) {
            return false;
        }
        String trimmed = routePath.trim();
        if (trimmed.length() > MAX_ROUTE_PATH_CHARS) {
            return false;
        }
        int q = trimmed.indexOf('?');
        String pathOnly = q < 0 ? trimmed : trimmed.substring(0, q);
        String query = q < 0 ? null : trimmed.substring(q + 1);
        if (pathOnly.endsWith("/") && pathOnly.length() > 1) {
            pathOnly = pathOnly.substring(0, pathOnly.length() - 1);
        }
        if (!ENTITY_AUDIT_PATH.matcher(pathOnly).matches()) {
            return false;
        }
        return query == null || query.isBlank();
    }

    /**
     * Allows {@code /entities/{uuid}/records} and {@code .../records/new} with optional whitelisted query string.
     * Query is not allowed on {@code .../records/new}.
     */
    public static boolean isAllowedEntityRecordRoute(String routePath) {
        if (routePath == null) {
            return false;
        }
        String trimmed = routePath.trim();
        if (trimmed.length() > MAX_ROUTE_PATH_CHARS) {
            return false;
        }
        int q = trimmed.indexOf('?');
        String pathOnly = q < 0 ? trimmed : trimmed.substring(0, q);
        String query = q < 0 ? null : trimmed.substring(q + 1);

        if (!ENTITY_RECORDS_PATH.matcher(pathOnly).matches()) {
            return false;
        }
        if (pathOnly.endsWith("/new")) {
            return query == null || query.isBlank();
        }
        if (query == null || query.isBlank()) {
            return true;
        }
        return validateEntityRecordsQueryString(query);
    }

    static boolean validateEntityRecordsQueryString(String rawQuery) {
        Map<String, String> params;
        try {
            params = parseQueryParams(rawQuery);
        } catch (IllegalArgumentException e) {
            return false;
        }
        for (String key : params.keySet()) {
            if (!ALLOWED_RECORD_QUERY_KEYS.contains(key)) {
                return false;
            }
        }
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!validateRecordQueryParam(e.getKey(), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> parseQueryParams(String rawQuery) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String part : rawQuery.split("&")) {
            if (part.isEmpty()) {
                continue;
            }
            int eq = part.indexOf('=');
            String encKey = eq < 0 ? part : part.substring(0, eq);
            String encVal = eq < 0 ? "" : part.substring(eq + 1);
            String key = urlDecode(encKey);
            String val = urlDecode(encVal);
            if (out.containsKey(key)) {
                throw new IllegalArgumentException("duplicate query key");
            }
            out.put(key, val);
        }
        return out;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean validateRecordQueryParam(String key, String value) {
        return switch (key) {
            case "cols", "inline" -> validateSlugCommaList(value);
            case "actions" -> "0".equals(value) || "1".equals(value);
            case "showRecordId", "showUuid" -> validateBooleanQueryToken(value);
            case "page" -> validatePositiveInt(value, 1, 10_000_000);
            case "pageSize" -> validatePositiveInt(value, 1, 200);
            case "q" -> validateSearchQ(value);
            case "view" -> value != null && UUID_TOKEN.matcher(value.trim()).matches();
            default -> false;
        };
    }

    /** Matches portal Records URL parsing ({@code 0|1|true|false}). */
    private static boolean validateBooleanQueryToken(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("0") || v.equals("1") || v.equals("true") || v.equals("false");
    }

    private static boolean validateSlugCommaList(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        for (String part : value.split(",")) {
            String s = part.trim();
            if (s.isEmpty() || !SLUG_TOKEN.matcher(s).matches()) {
                return false;
            }
        }
        return true;
    }

    private static boolean validatePositiveInt(String value, int min, int max) {
        try {
            int n = Integer.parseInt(value.trim());
            return n >= min && n <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean validateSearchQ(String value) {
        if (value == null) {
            return true;
        }
        if (value.length() > 200) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t') {
                return false;
            }
        }
        return true;
    }

    @Transactional
    public NavigationDtos.NavigationItemCreatedDto create(
            Authentication authentication,
            TenantPrincipal principal,
            NavigationDtos.NavigationItemCreateRequest req
    ) {
        Set<String> auths = authorities(authentication);
        boolean global = "GLOBAL".equalsIgnoreCase(req.scope());
        if (global) {
            if (!canWriteGlobalNav(auths)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Global navigation requires iam:security:admin or iam:superadmin");
            }
        } else {
            if (!canWriteTenantNav(auths)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Requires portal:navigation:write or entity_builder:schema:write");
            }
        }
        UUID tenantId = global ? null : principal.getTenantId();
        if (!global && tenantId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Tenant id missing from token for tenant-scoped nav item");
        }

        PortalNavigationItem parent = resolveParent(req.parentId());
        validateParentChildScope(parent, tenantId, global);

        String navType = normalizeNavType(req.type());
        String routeRaw = req.routePath() == null ? "" : req.routePath().trim();

        PortalNavigationItem item = new PortalNavigationItem();
        item.setParentId(req.parentId());
        item.setTenantId(tenantId);
        item.setSortOrder(req.sortOrder());
        item.setLabel(req.label().trim());
        item.setDescription(req.description() != null ? req.description().trim() : null);
        item.setType(navType);
        applyRouteForCreate(navType, routeRaw, item);
        item.setIcon(req.icon());
        item.setCategoryKey(req.categoryKey());
        item.setSearchKeywords(req.searchKeywords() != null ? new ArrayList<>(req.searchKeywords()) : new ArrayList<>());
        item.setRequiredPermissions(req.requiredPermissions() != null ? new ArrayList<>(req.requiredPermissions()) : new ArrayList<>());
        item.setRequiredRoles(req.requiredRoles() != null ? new ArrayList<>(req.requiredRoles()) : new ArrayList<>());
        item.setActive(true);
        item.setDesignStatus(normalizeDesignStatus(req.designStatus()));
        if (req.linkedListViewId() != null) {
            item.setLinkedListViewId(req.linkedListViewId());
        }
        if (req.linkedFormLayoutId() != null) {
            item.setLinkedFormLayoutId(req.linkedFormLayoutId());
        }

        assertRouteMatchesType(item);
        repository.save(item);
        return new NavigationDtos.NavigationItemCreatedDto(item.getId());
    }

    private static String normalizeDesignStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PUBLISHED";
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if ("WIP".equals(s)) {
            return "WIP";
        }
        if ("PUBLISHED".equals(s)) {
            return "PUBLISHED";
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "designStatus must be PUBLISHED or WIP");
    }

    private static String normalizeNavType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "internal";
        }
        String t = raw.trim();
        if (!ALLOWED_NAV_TYPES.contains(t)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "type must be internal, external, section, or divider");
        }
        return t;
    }

    private static void applyRouteForCreate(String navType, String routeRaw, PortalNavigationItem item) {
        switch (navType) {
            case "section", "divider" -> item.setRoutePath(routeRaw.isEmpty() ? null : routeRaw);
            case "internal" -> {
                if (!isAllowedInternalSpaRoute(routeRaw)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath must be /home, /entities, or /entities/{uuid}/records[/new]");
                }
                item.setRoutePath(routeRaw);
            }
            case "external" -> {
                if (routeRaw.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath is required for external type");
                }
                if (routeRaw.length() > MAX_ROUTE_PATH_CHARS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath too long");
                }
                item.setRoutePath(routeRaw);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid type");
        }
    }

    private static void applyRoutePatchForType(String rawType, String rawPath, PortalNavigationItem item) {
        String t = rawType == null ? "internal" : rawType;
        String r = rawPath == null ? "" : rawPath.trim();
        switch (t) {
            case "internal" -> {
                if (!isAllowedInternalSpaRoute(r)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid routePath for internal type");
                }
                item.setRoutePath(r);
            }
            case "external" -> {
                if (r.isEmpty()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath cannot be empty for external type");
                }
                if (r.length() > MAX_ROUTE_PATH_CHARS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath too long");
                }
                item.setRoutePath(r);
            }
            case "section", "divider" -> item.setRoutePath(r.isEmpty() ? null : r);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Invalid type");
        }
    }

    /**
     * Enforces DB check constraint: route required unless section/divider; internal routes must match entity records pattern.
     */
    private static void assertRouteMatchesType(PortalNavigationItem item) {
        String t = item.getType() == null ? "internal" : item.getType();
        String rp = item.getRoutePath();
        boolean hasRoute = rp != null && !rp.trim().isEmpty();
        if (!hasRoute && !"section".equals(t) && !"divider".equals(t)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath is required unless type is section or divider");
        }
        if (hasRoute && "internal".equals(t) && !isAllowedInternalSpaRoute(rp.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "internal type requires an allowed SPA route");
        }
        if (hasRoute && rp.trim().length() > MAX_ROUTE_PATH_CHARS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "routePath too long");
        }
    }

    @Transactional
    public void update(
            Authentication authentication,
            TenantPrincipal principal,
            UUID id,
            NavigationDtos.NavigationItemPatchRequest req
    ) {
        PortalNavigationItem item = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Navigation item not found"));

        assertCanModify(authentication, principal, item);

        if (Boolean.TRUE.equals(req.promoteToGlobal())) {
            if (!canWriteGlobalNav(authorities(authentication))) {
                throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Promote to global requires iam:security:admin or iam:superadmin");
            }
            item.setTenantId(null);
        }

        if (req.parentId() != null) {
            PortalNavigationItem parent = resolveParent(req.parentId());
            validateParentChildScope(parent, item.getTenantId(), item.getTenantId() == null);
            item.setParentId(req.parentId());
        }
        if (req.sortOrder() != null) {
            item.setSortOrder(req.sortOrder());
        }
        if (req.type() != null) {
            item.setType(normalizeNavType(req.type()));
        }
        if (req.routePath() != null) {
            applyRoutePatchForType(item.getType(), req.routePath(), item);
        }
        if (req.label() != null) {
            item.setLabel(req.label().trim());
        }
        if (req.description() != null) {
            item.setDescription(req.description().trim());
        }
        if (req.icon() != null) {
            item.setIcon(req.icon());
        }
        if (req.categoryKey() != null) {
            item.setCategoryKey(req.categoryKey());
        }
        if (req.searchKeywords() != null) {
            item.setSearchKeywords(new ArrayList<>(req.searchKeywords()));
        }
        if (req.requiredPermissions() != null) {
            item.setRequiredPermissions(new ArrayList<>(req.requiredPermissions()));
        }
        if (req.requiredRoles() != null) {
            item.setRequiredRoles(new ArrayList<>(req.requiredRoles()));
        }
        if (req.active() != null) {
            item.setActive(req.active());
        }
        if (req.designStatus() != null) {
            item.setDesignStatus(normalizeDesignStatus(req.designStatus()));
        }
        if (req.linkedListViewId() != null) {
            item.setLinkedListViewId(req.linkedListViewId());
        }
        if (req.linkedFormLayoutId() != null) {
            item.setLinkedFormLayoutId(req.linkedFormLayoutId());
        }

        assertRouteMatchesType(item);
        repository.save(item);
    }

    @Transactional
    public void deactivate(Authentication authentication, TenantPrincipal principal, UUID id) {
        PortalNavigationItem item = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Navigation item not found"));
        assertCanModify(authentication, principal, item);
        item.setActive(false);
        repository.save(item);
    }

    private static void assertCanModify(Authentication authentication, TenantPrincipal principal, PortalNavigationItem item) {
        Set<String> auths = authorities(authentication);
        if (canWriteGlobalNav(auths)) {
            return;
        }
        UUID rowTenant = item.getTenantId();
        if (rowTenant == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Cannot modify global navigation item");
        }
        if (principal.getTenantId() == null || !rowTenant.equals(principal.getTenantId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Wrong tenant");
        }
        if (!canWriteTenantNav(auths)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "Requires portal:navigation:write or entity_builder:schema:write");
        }
    }

    private PortalNavigationItem resolveParent(UUID parentId) {
        if (parentId == null) {
            return null;
        }
        return repository.findById(parentId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "parentId not found"));
    }

    /**
     * Tenant-scoped child may attach under global parent or same-tenant parent.
     * Global child must attach only under global parent.
     */
    private static void validateParentChildScope(PortalNavigationItem parent, UUID childTenantId, boolean globalChild) {
        if (parent == null) {
            return;
        }
        UUID pTenant = parent.getTenantId();
        if (globalChild) {
            if (pTenant != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Global nav item cannot use a tenant-scoped parent");
            }
            return;
        }
        if (childTenantId == null) {
            return;
        }
        if (pTenant != null && !pTenant.equals(childTenantId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Parent belongs to a different tenant");
        }
    }

    private static Set<String> authorities(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private static boolean canWriteTenantNav(Set<String> auths) {
        return auths.contains("portal:navigation:write") || auths.contains("entity_builder:schema:write");
    }

    private static boolean canWriteGlobalNav(Set<String> auths) {
        return auths.contains("iam:security:admin") || auths.contains("iam:superadmin");
    }
}
