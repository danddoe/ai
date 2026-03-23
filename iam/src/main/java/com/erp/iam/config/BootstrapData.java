package com.erp.iam.config;

import com.erp.iam.domain.*;
import com.erp.iam.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds a tenant + superadmin user when the database has no tenants (local / first boot).
 * <p>
 * Activate: {@code --spring.profiles.active=default-bootstrap}
 * <p>
 * Login identity: tenant slug / email from {@link BootstrapSeedProperties}; password must be set via
 * {@code app.bootstrap.admin-password} or env {@code SEED_SUPERADMIN_PASSWORD} (no default in code).
 */
@Component
@Profile("default-bootstrap")
public class BootstrapData {

    @Bean
    CommandLineRunner bootstrap(
            BootstrapSeedProperties seed,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            TenantUserRepository tenantUserRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            RolePermissionRepository rolePermissionRepository,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (tenantRepository.count() > 0) {
                return;
            }
            if (seed.getAdminPassword() == null || seed.getAdminPassword().isBlank()) {
                throw new IllegalStateException(
                        "default-bootstrap requires app.bootstrap.admin-password or SEED_SUPERADMIN_PASSWORD when the database has no tenants");
            }

            Tenant tenant = new Tenant();
            tenant.setName(seed.getTenantName());
            tenant.setSlug(seed.getTenantSlug());
            tenant.setStatus("ACTIVE");
            tenant = tenantRepository.save(tenant);

            User user = new User();
            user.setEmail(seed.getAdminEmail());
            user.setPasswordHash(passwordEncoder.encode(seed.getAdminPassword()));
            user.setDisplayName(seed.getAdminDisplayName());
            user.setStatus("ACTIVE");
            user = userRepository.save(user);

            TenantUser tu = new TenantUser();
            tu.setTenantId(tenant.getId());
            tu.setUserId(user.getId());
            tu.setStatus("ACTIVE");
            tu.setJoinedAt(java.time.Instant.now());
            tenantUserRepository.save(tu);

            Role role = new Role();
            role.setTenantId(tenant.getId());
            role.setName(seed.getAdminRoleName());
            role.setDescription("Seeded superadmin (all permissions)");
            role.setSystem(true);
            role = roleRepository.save(role);

            UserRole ur = new UserRole();
            ur.setTenantId(tenant.getId());
            ur.setUserId(user.getId());
            ur.setRoleId(role.getId());
            userRoleRepository.save(ur);

            List<Permission> perms = permissionRepository.findAll();
            for (Permission p : perms) {
                RolePermission rp = new RolePermission();
                rp.setTenantId(tenant.getId());
                rp.setRoleId(role.getId());
                rp.setPermissionId(p.getId());
                rolePermissionRepository.save(rp);
            }
        };
    }
}
