package com.erp.iam.config;

import com.erp.iam.domain.*;
import com.erp.iam.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Creates a default tenant and admin user when the database is empty (e.g. for local dev).
 * Enable with profile: default-bootstrap
 */
@Component
@Profile("default-bootstrap")
public class BootstrapData {

    @Bean
    CommandLineRunner bootstrap(TenantRepository tenantRepository,
                               UserRepository userRepository,
                               TenantUserRepository tenantUserRepository,
                               RoleRepository roleRepository,
                               UserRoleRepository userRoleRepository,
                               RolePermissionRepository rolePermissionRepository,
                               PermissionRepository permissionRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            if (tenantRepository.count() > 0) return;

            Tenant tenant = new Tenant();
            tenant.setName("Default Tenant");
            tenant.setSlug("default");
            tenant.setStatus("ACTIVE");
            tenant = tenantRepository.save(tenant);

            User user = new User();
            user.setEmail("admin@example.com");
            user.setPasswordHash(passwordEncoder.encode("admin123"));
            user.setDisplayName("Admin");
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
            role.setName("ADMIN");
            role.setDescription("Administrator");
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
