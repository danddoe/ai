package com.erp.iam.service;

import com.erp.iam.domain.PortalNavigationItem;
import com.erp.iam.repository.PortalNavigationItemRepository;
import com.erp.iam.security.TenantPrincipal;
import com.erp.iam.web.v1.dto.NavigationDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PortalNavigationService {

    private final PortalNavigationItemRepository repository;

    public PortalNavigationService(PortalNavigationItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NavigationDtos.NavigationResponse navigationFor(TenantPrincipal principal) {
        List<PortalNavigationItem> all = repository.findAllByActiveTrueOrderByParentIdAscSortOrderAsc();
        Map<UUID, PortalNavigationItem> byId = all.stream().collect(Collectors.toMap(PortalNavigationItem::getId, Function.identity()));
        Set<UUID> visible = computeVisibleIds(principal, all, byId);

        Map<UUID, List<PortalNavigationItem>> childrenByParent = all.stream()
                .filter(i -> visible.contains(i.getId()))
                .filter(i -> i.getParentId() != null)
                .collect(Collectors.groupingBy(PortalNavigationItem::getParentId));

        List<NavigationDtos.NavigationItemDto> roots = all.stream()
                .filter(i -> visible.contains(i.getId()))
                .filter(i -> i.getParentId() == null)
                .sorted(Comparator.comparingInt(PortalNavigationItem::getSortOrder))
                .map(r -> toDto(r, childrenByParent, null))
                .filter(Objects::nonNull)
                .toList();

        return new NavigationDtos.NavigationResponse(roots);
    }

    /**
     * Flat search over navigation entries visible to the principal.
     * Matches label, description, and {@code search_keywords} (case-insensitive substring).
     */
    @Transactional(readOnly = true)
    public NavigationDtos.NavigationSearchResponse searchNavigation(TenantPrincipal principal, String query, int limit) {
        if (query == null || query.isBlank() || query.trim().length() < 2) {
            return new NavigationDtos.NavigationSearchResponse(List.of());
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        int lim = limit <= 0 ? 12 : Math.min(limit, 50);

        List<PortalNavigationItem> all = repository.findAllByActiveTrueOrderByParentIdAscSortOrderAsc();
        Map<UUID, PortalNavigationItem> byId = all.stream().collect(Collectors.toMap(PortalNavigationItem::getId, Function.identity()));
        Set<UUID> visible = computeVisibleIds(principal, all, byId);

        List<NavigationDtos.NavigationSearchHitDto> hits = new ArrayList<>();
        for (PortalNavigationItem n : all) {
            if (!visible.contains(n.getId())) {
                continue;
            }
            if (!"internal".equals(n.getType()) && !"external".equals(n.getType())) {
                continue;
            }
            if (n.getRoutePath() == null || n.getRoutePath().isBlank()) {
                continue;
            }
            if (!navigationItemMatchesQuery(n, q)) {
                continue;
            }
            hits.add(new NavigationDtos.NavigationSearchHitDto(
                    n.getId(),
                    n.getLabel(),
                    n.getDescription(),
                    n.getRoutePath(),
                    n.getType(),
                    n.getIcon(),
                    inheritedCategoryKey(n, byId)
            ));
            if (hits.size() >= lim) {
                break;
            }
        }
        return new NavigationDtos.NavigationSearchResponse(hits);
    }

    private static Set<UUID> computeVisibleIds(
            TenantPrincipal principal,
            List<PortalNavigationItem> all,
            Map<UUID, PortalNavigationItem> byId
    ) {
        Set<UUID> visible = new HashSet<>();
        for (PortalNavigationItem item : all) {
            if (visibleForPrincipal(principal, item)) {
                visible.add(item.getId());
            }
        }
        pruneOrphans(visible, byId);
        return visible;
    }

    private static String inheritedCategoryKey(PortalNavigationItem n, Map<UUID, PortalNavigationItem> byId) {
        PortalNavigationItem cur = n;
        Set<UUID> guard = new HashSet<>();
        while (cur != null) {
            if (cur.getCategoryKey() != null && !cur.getCategoryKey().isBlank()) {
                return cur.getCategoryKey();
            }
            UUID p = cur.getParentId();
            if (p == null) {
                return null;
            }
            if (!guard.add(p)) {
                return null;
            }
            cur = byId.get(p);
        }
        return null;
    }

    private static boolean navigationItemMatchesQuery(PortalNavigationItem n, String qLower) {
        if (n.getLabel() != null && n.getLabel().toLowerCase(Locale.ROOT).contains(qLower)) {
            return true;
        }
        if (n.getDescription() != null && n.getDescription().toLowerCase(Locale.ROOT).contains(qLower)) {
            return true;
        }
        List<String> kw = n.getSearchKeywords();
        if (kw != null) {
            for (String k : kw) {
                if (k != null && k.toLowerCase(Locale.ROOT).contains(qLower)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove nodes whose parent is not visible (transitively until fixpoint).
     */
    private static void pruneOrphans(Set<UUID> visible, Map<UUID, PortalNavigationItem> byId) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Iterator<UUID> it = visible.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                PortalNavigationItem item = byId.get(id);
                if (item == null) {
                    it.remove();
                    changed = true;
                    continue;
                }
                UUID p = item.getParentId();
                if (p != null && !visible.contains(p)) {
                    it.remove();
                    changed = true;
                }
            }
        }
    }

    /**
     * Empty required_* lists: no gate. Non-empty: user must match at least one entry (OR).
     * Between permissions and roles: both must pass (AND) when both lists are non-empty.
     */
    static boolean visibleForPrincipal(TenantPrincipal principal, PortalNavigationItem item) {
        UUID itemTenant = item.getTenantId();
        if (itemTenant != null) {
            UUID userTenant = principal.getTenantId();
            if (userTenant == null || !itemTenant.equals(userTenant)) {
                return false;
            }
        }
        List<String> reqPerm = item.getRequiredPermissions();
        if (reqPerm != null && !reqPerm.isEmpty()) {
            boolean any = reqPerm.stream().anyMatch(p -> principal.getPermissions().contains(p));
            if (!any) {
                return false;
            }
        }
        List<String> reqRoles = item.getRequiredRoles();
        if (reqRoles != null && !reqRoles.isEmpty()) {
            boolean any = reqRoles.stream().anyMatch(r -> principal.getRoles().contains(r));
            if (!any) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param inheritedCategory applied when this row's category_key is blank (walks down the tree)
     */
    private static NavigationDtos.NavigationItemDto toDto(
            PortalNavigationItem n,
            Map<UUID, List<PortalNavigationItem>> childrenByParent,
            String inheritedCategory
    ) {
        String rawCategory = n.getCategoryKey();
        final String effectiveCategory =
                (rawCategory != null && !rawCategory.isBlank()) ? rawCategory : inheritedCategory;

        List<PortalNavigationItem> rawKids = childrenByParent.getOrDefault(n.getId(), List.of());
        List<NavigationDtos.NavigationItemDto> kids = rawKids.stream()
                .sorted(Comparator.comparingInt(PortalNavigationItem::getSortOrder))
                .map(c -> toDto(c, childrenByParent, effectiveCategory))
                .filter(Objects::nonNull)
                .toList();

        if ("section".equals(n.getType()) && kids.isEmpty()) {
            return null;
        }

        return new NavigationDtos.NavigationItemDto(
                n.getId(),
                n.getParentId(),
                n.getSortOrder(),
                n.getRoutePath(),
                n.getLabel(),
                n.getDescription(),
                n.getType(),
                n.getIcon(),
                effectiveCategory,
                List.copyOf(n.getSearchKeywords()),
                n.getDesignStatus(),
                n.getLinkedListViewId(),
                n.getLinkedFormLayoutId(),
                kids
        );
    }
}
