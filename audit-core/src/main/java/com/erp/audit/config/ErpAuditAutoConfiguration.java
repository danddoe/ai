package com.erp.audit.config;

import com.erp.audit.AuditLogWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
public class ErpAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuditLogWriter auditLogWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new AuditLogWriter(jdbcTemplate, objectMapper);
    }
}
