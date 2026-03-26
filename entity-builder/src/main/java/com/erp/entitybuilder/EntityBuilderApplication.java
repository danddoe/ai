package com.erp.entitybuilder;

import com.erp.entitybuilder.config.PlatformTenantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PlatformTenantProperties.class)
public class EntityBuilderApplication {
    public static void main(String[] args) {
        SpringApplication.run(EntityBuilderApplication.class, args);
    }
}

