package com.evo.commerce;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-key-for-application-context-smoke-test-only")
class EvoCommerceApplicationTests {

    @Test
    void contextLoads() {
    }

}
