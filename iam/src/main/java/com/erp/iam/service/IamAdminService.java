package com.erp.iam.service;

import com.erp.iam.domain.*;
import com.erp.iam.repository.*;
import com.erp.iam.web.ApiException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class IamAdminService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    public IamAdminService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- Tenants ---

    public Page<Tenant> listTenants(Optional<String> status, Optional<String> q, Pageable pageable) {
        return tenantRepository.findAll(tenantSpec(status, q), pageable);
    }

    @Transactional
    public Tenant createTenant(String name, String slug, String status) {
        if (tenantRepository.existsBySlug(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Tenant slug already exists", Map.of("slug", slug));
        }
        Tenant t = new Tenant();
        t.setName(name);
        t.setSlug(slug);
        if (status != null && !status.isBlank()) t.setStatus(status);
        return tenantRepository.save(t);
    }

    public Tenant getTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant not found"));
    }

    @Transactional
    public Tenant updateTenant(UUID tenantId, Optional<String> name, Optional<String> slug, Optional<String> status) {
        Tenant t = getTenant(tenantId);
        name.ifPresent(v -> { if (!v.isBlank()) t.setName(v); });
        slug.ifPresent(v -> {
            if (v != null && !v.isBlank() && !v.equals(t.getSlug())) {
                if (tenantRepository.existsBySlug(v)) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Tenant slug already exists", Map.of("slug", v));
                }
                t.setSlug(v);
            }
        });
        status.ifPresent(v -> { if (v != null && !v.isBlank()) t.setStatus(v); });
        return tenantRepository.save(t);
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant not found");
        }
        tenantRepository.deleteById(tenantId);
    }

    // --- Users ---

    public Page<User> listUsers(Optional<String> email, Optional<String> status, Optional<String> q, Pageable pageable) {
        return userRepository.findAll(userSpec(email, status, q), pageable);
    }

    @Transactional
    public User createUser(String email, String displayName, String password, String status) {
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Email already exists", Map.of("email", email));
        }
        User u = new User();
        u.setEmail(email);
        u.setDisplayName(displayName);
        u.setPasswordHash(passwordEncoder.encode(password));
        if (status != null && !status.isBlank()) u.setStatus(status);
        return userRepository.save(u);
    }

    public User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found"));
    }

    public User getUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Email is required");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found"));
    }

    @Transactional
    public User updateUser(UUID userId, Optional<String> email, Optional<String> displayName, Optional<String> status, Optional<String> password) {
        User u = getUser(userId);
        email.ifPresent(v -> {
            if (v != null && !v.isBlank() && !v.equalsIgnoreCase(u.getEmail())) {
                if (userRepository.existsByEmail(v)) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Email already exists", Map.of("email", v));
                }
                u.setEmail(v);
            }
        });
        displayName.ifPresent(u::setDisplayName);
        status.ifPresent(v -> { if (v != null && !v.isBlank()) u.setStatus(v); });
        password.ifPresent(v -> {
            if (v != null && !v.isBlank()) {
                u.setPasswordHash(passwordEncoder.encode(v));
            }
        });
        return userRepository.save(u);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found");
        }
        userRepository.deleteById(userId);
    }

    // --- Tenant membership ---

    public Page<TenantUser> listMembers(UUID tenantId, Optional<String> status, Pageable pageable) {
        ensureTenantExists(tenantId);
        return tenantUserRepository.findAll(memberSpec(tenantId, status), pageable);
    }

    @Transactional
    public TenantUser addMember(UUID tenantId, UUID userId, String displayName, String status) {
        ensureTenantExists(tenantId);
        ensureUserExists(userId);
        if (tenantUserRepository.existsByTenantIdAndUserId(tenantId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "User is already a member of tenant");
        }
        TenantUser tu = new TenantUser();
        tu.setTenantId(tenantId);
        tu.setUserId(userId);
        tu.setDisplayName(displayName);
        if (status != null && !status.isBlank()) tu.setStatus(status);
        tu.setInvitedAt(Instant.now());
        return tenantUserRepository.save(tu);
    }

    public TenantUser getMember(UUID tenantId, UUID userId) {
        ensureTenantExists(tenantId);
        return tenantUserRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant membership not found"));
    }

    @Transactional
    public TenantUser updateMember(UUID tenantId, UUID userId, Optional<String> displayName, Optional<String> status) {
        TenantUser tu = getMember(tenantId, userId);
        displayName.ifPresent(tu::setDisplayName);
        status.ifPresent(v -> { if (v != null && !v.isBlank()) tu.setStatus(v); });
        return tenantUserRepository.save(tu);
    }

    @Transactional
    public void deleteMember(UUID tenantId, UUID userId) {
        TenantUser tu = getMember(tenantId, userId);
        // also remove role assignments for that tenant/user
        List<UserRole> urs = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);
        userRoleRepository.deleteAll(urs);
        tenantUserRepository.delete(tu);
    }

    // --- Roles ---

    public Page<Role> listRoles(UUID tenantId, Optional<String> q, Pageable pageable) {
        ensureTenantExists(tenantId);
        return roleRepository.findAll(roleSpec(tenantId, q), pageable);
    }

    @Transactional
    public Role createRole(UUID tenantId, String name, String description, boolean system) {
        ensureTenantExists(tenantId);
        if (roleRepository.existsByTenantIdAndName(tenantId, name)) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Role name already exists", Map.of("name", name));
        }
        Role r = new Role();
        r.setTenantId(tenantId);
        r.setName(name);
        r.setDescription(description);
        r.setSystem(system);
        return roleRepository.save(r);
    }

    public Role getRole(UUID tenantId, UUID roleId) {
        ensureTenantExists(tenantId);
        Role r = roleRepository.findById(roleId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Role not found"));
        if (!tenantId.equals(r.getTenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Role not found");
        }
        return r;
    }

    @Transactional
    public Role updateRole(UUID tenantId, UUID roleId, Optional<String> name, Optional<String> description) {
        Role r = getRole(tenantId, roleId);
        name.ifPresent(v -> {
            if (v != null && !v.isBlank() && !v.equals(r.getName())) {
                if (roleRepository.existsByTenantIdAndName(tenantId, v)) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Role name already exists", Map.of("name", v));
                }
                r.setName(v);
            }
        });
        description.ifPresent(r::setDescription);
        return roleRepository.save(r);
    }

    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId, boolean allowSystemRoleDelete) {
        Role r = getRole(tenantId, roleId);
        if (r.isSystem() && !allowSystemRoleDelete) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "System roles cannot be deleted");
        }
        roleRepository.delete(r);
    }

    // --- Permissions ---

    public Page<Permission> listPermissions(Optional<String> q, Pageable pageable) {
        return permissionRepository.findAll(permissionSpec(q), pageable);
    }

    @Transactional
    public Permission createPermission(String code, String description) {
        if (permissionRepository.findByCode(code).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict", "Permission code already exists", Map.of("code", code));
        }
        Permission p = new Permission();
        p.setCode(code);
        p.setDescription(description);
        return permissionRepository.save(p);
    }

    public Permission getPermission(UUID permissionId) {
        return permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Permission not found"));
    }

    @Transactional
    public Permission updatePermission(UUID permissionId, Optional<String> code, Optional<String> description) {
        Permission p = getPermission(permissionId);
        code.ifPresent(v -> {
            if (v != null && !v.isBlank() && !v.equals(p.getCode())) {
                if (permissionRepository.findByCode(v).isPresent()) {
                    throw new ApiException(HttpStatus.CONFLICT, "conflict", "Permission code already exists", Map.of("code", v));
                }
                p.setCode(v);
            }
        });
        description.ifPresent(p::setDescription);
        return permissionRepository.save(p);
    }

    @Transactional
    public void deletePermission(UUID permissionId) {
        if (!permissionRepository.existsById(permissionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Permission not found");
        }
        permissionRepository.deleteById(permissionId);
    }

    // --- Assignments ---

    public List<Permission> listRolePermissions(UUID tenantId, UUID roleId) {
        getRole(tenantId, roleId);
        List<UUID> ids = rolePermissionRepository.findPermissionIdsByTenantIdAndRoleId(tenantId, roleId);
        if (ids.isEmpty()) return List.of();
        return permissionRepository.findAllById(ids);
    }

    @Transactional
    public void replaceRolePermissions(UUID tenantId, UUID roleId, List<UUID> permissionIds) {
        getRole(tenantId, roleId);
        if (permissionIds == null) permissionIds = List.of();
        List<UUID> unique = permissionIds.stream().filter(Objects::nonNull).distinct().toList();
        // validate permission ids exist
        if (!unique.isEmpty()) {
            Set<UUID> found = new HashSet<>(permissionRepository.findAllById(unique).stream().map(Permission::getId).toList());
            List<UUID> missing = unique.stream().filter(id -> !found.contains(id)).toList();
            if (!missing.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Some permissions do not exist", Map.of("missingPermissionIds", missing));
            }
        }
        rolePermissionRepository.deleteByTenantIdAndRoleId(tenantId, roleId);
        for (UUID pid : unique) {
            RolePermission rp = new RolePermission();
            rp.setTenantId(tenantId);
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionRepository.save(rp);
        }
    }

    public List<Role> listUserRoles(UUID tenantId, UUID userId) {
        ensureTenantExists(tenantId);
        ensureUserExists(userId);
        // ensure membership exists to avoid cross-tenant leakage
        if (tenantUserRepository.findByTenantIdAndUserId(tenantId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant membership not found");
        }
        List<UUID> roleIds = userRoleRepository.findRoleIdsByTenantIdAndUserId(tenantId, userId);
        if (roleIds.isEmpty()) return List.of();
        return roleRepository.findAllById(roleIds);
    }

    @Transactional
    public void replaceUserRoles(UUID tenantId, UUID userId, List<UUID> roleIds) {
        ensureTenantExists(tenantId);
        ensureUserExists(userId);
        if (tenantUserRepository.findByTenantIdAndUserId(tenantId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant membership not found");
        }
        if (roleIds == null) roleIds = List.of();
        List<UUID> unique = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!unique.isEmpty()) {
            List<Role> roles = roleRepository.findAllById(unique);
            Map<UUID, Role> byId = new HashMap<>();
            for (Role r : roles) byId.put(r.getId(), r);
            List<UUID> missing = unique.stream().filter(id -> !byId.containsKey(id)).toList();
            if (!missing.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Some roles do not exist", Map.of("missingRoleIds", missing));
            }
            List<UUID> wrongTenant = roles.stream().filter(r -> !tenantId.equals(r.getTenantId())).map(Role::getId).toList();
            if (!wrongTenant.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "bad_request", "Some roles do not belong to tenant", Map.of("wrongTenantRoleIds", wrongTenant));
            }
        }

        List<UserRole> existing = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);
        userRoleRepository.deleteAll(existing);
        for (UUID rid : unique) {
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setUserId(userId);
            ur.setRoleId(rid);
            userRoleRepository.save(ur);
        }
    }

    // --- Specifications ---

    private Specification<Tenant> tenantSpec(Optional<String> status, Optional<String> q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            status.filter(s -> !s.isBlank()).ifPresent(s -> preds.add(cb.equal(root.get("status"), s)));
            q.filter(s -> !s.isBlank()).ifPresent(s -> {
                String like = "%" + s.toLowerCase(Locale.ROOT) + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("slug")), like)
                ));
            });
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private Specification<User> userSpec(Optional<String> email, Optional<String> status, Optional<String> q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            email.filter(s -> !s.isBlank()).ifPresent(s -> preds.add(cb.equal(cb.lower(root.get("email")), s.toLowerCase(Locale.ROOT))));
            status.filter(s -> !s.isBlank()).ifPresent(s -> preds.add(cb.equal(root.get("status"), s)));
            q.filter(s -> !s.isBlank()).ifPresent(s -> {
                String like = "%" + s.toLowerCase(Locale.ROOT) + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("displayName")), like)
                ));
            });
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private Specification<TenantUser> memberSpec(UUID tenantId, Optional<String> status) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("tenantId"), tenantId));
            status.filter(s -> !s.isBlank()).ifPresent(s -> preds.add(cb.equal(root.get("status"), s)));
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private Specification<Role> roleSpec(UUID tenantId, Optional<String> q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("tenantId"), tenantId));
            q.filter(s -> !s.isBlank()).ifPresent(s -> {
                String like = "%" + s.toLowerCase(Locale.ROOT) + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            });
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private Specification<Permission> permissionSpec(Optional<String> q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            q.filter(s -> !s.isBlank()).ifPresent(s -> {
                String like = "%" + s.toLowerCase(Locale.ROOT) + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("description")), like)
                ));
            });
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private void ensureTenantExists(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Tenant not found");
        }
    }

    private void ensureUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found");
        }
    }
}

