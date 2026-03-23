package com.erp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.cloud.vault.enabled=false")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
