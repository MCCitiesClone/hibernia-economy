package io.paradaux.treasuryrestapi;

import io.paradaux.treasuryrestapi.testsupport.EmbeddedMariaDb;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class TreasuryRestApiApplicationTests {

    // Point the context at the embedded MariaDB so the suite boots without an
    // external database (the market integration tests use the same instance).
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        EmbeddedMariaDb.ensureStarted();
        registry.add("spring.datasource.url", EmbeddedMariaDb::jdbcUrl);
        registry.add("spring.datasource.username", EmbeddedMariaDb::username);
        registry.add("spring.datasource.password", EmbeddedMariaDb::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
    }

    @Test
    void contextLoads() {
    }

}
