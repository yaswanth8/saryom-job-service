package com.saryom.jobservice.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A real Postgres set up the way production is: the service's own role owns the
 * {@code jobs} schema and is not a superuser.
 *
 * <p>That last part is the point. A superuser bypasses row level security
 * entirely, so a test that connects as one (which is what a Testcontainers
 * database hands out by default) would pass with every policy missing.
 *
 * <p>Uses Testcontainers unless {@code JOBS_TEST_PG_URL} names a server that is
 * already running, for machines where Docker is not available. The variables
 * {@code JOBS_TEST_PG_USER} and {@code JOBS_TEST_PG_PASSWORD} are that server's
 * superuser, needed once to create the role.
 */
public final class TestPostgres {

    public static final String ROLE = "saryom_jobs";
    public static final String ROLE_PASSWORD = "jobs_test";

    private static final String URL;

    static {
        String external = System.getenv("JOBS_TEST_PG_URL");
        String adminUser;
        String adminPassword;
        if (external != null && !external.isBlank()) {
            URL = external;
            adminUser = System.getenv().getOrDefault("JOBS_TEST_PG_USER", "postgres");
            adminPassword = System.getenv().getOrDefault("JOBS_TEST_PG_PASSWORD", "postgres");
        } else {
            @SuppressWarnings("resource") // lives for the JVM; Ryuk removes the container
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            URL = container.getJdbcUrl();
            adminUser = container.getUsername();
            adminPassword = container.getPassword();
        }
        try (Connection admin = DriverManager.getConnection(URL, adminUser, adminPassword);
             Statement sql = admin.createStatement()) {
            // Start clean, so a reused server behaves like a fresh container.
            sql.execute("DROP SCHEMA IF EXISTS jobs CASCADE");
            sql.execute("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '" + ROLE + "') THEN "
                    + "CREATE ROLE " + ROLE + " LOGIN PASSWORD '" + ROLE_PASSWORD + "' NOSUPERUSER NOBYPASSRLS; "
                    + "END IF; END $$");
            sql.execute("CREATE SCHEMA jobs AUTHORIZATION " + ROLE);
            sql.execute("ALTER ROLE " + ROLE + " SET search_path = jobs");
        } catch (SQLException e) {
            throw new IllegalStateException("could not prepare the test database", e);
        }
    }

    private TestPostgres() {
    }

    /** Points the application at this database, connecting as the unprivileged role. */
    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> ROLE);
        registry.add("spring.datasource.password", () -> ROLE_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.schemas", () -> "jobs");
        registry.add("spring.flyway.default-schema", () -> "jobs");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "jobs");
        registry.add("saryom.jobs.rls.enabled", () -> "true");
    }

    /** A connection as the service's role, outside the application, for asking the database directly. */
    public static Connection connectAsService() throws SQLException {
        return DriverManager.getConnection(URL, ROLE, ROLE_PASSWORD);
    }
}
