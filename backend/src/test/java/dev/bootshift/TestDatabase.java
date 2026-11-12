package dev.bootshift;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres for integration tests. Uses Testcontainers unless BOOTSHIFT_TEST_DB_URL points at an
 * existing database, in which case the datasource settings in application-test.yaml apply.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {

    public static final String IMAGE = "postgres:18.6";

    @Bean
    @ServiceConnection
    @ConditionalOnExpression("'${BOOTSHIFT_TEST_DB_URL:}' == ''")
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGE));
    }
}
