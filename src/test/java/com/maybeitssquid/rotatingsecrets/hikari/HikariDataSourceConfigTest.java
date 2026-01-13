package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class HikariDataSourceConfigTest {

    private static final String JDBC_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";

    @TempDir
    Path tempDir;

    private DynamicHikariCredentialsProvider
            credentialsProvider;
    private HikariDataSourceConfig config;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("username"), "sa");
        Files.writeString(tempDir.resolve("password"), "");

        credentialsProvider = new DynamicHikariCredentialsProvider(tempDir.toString());
        config = new HikariDataSourceConfig();
    }

    @Test
    void dataSource_createsHikariDataSource() {
        DataSource dataSource = config.dataSource(credentialsProvider, JDBC_URL, 1, 3);

        assertInstanceOf(HikariDataSource.class, dataSource);

        ((HikariDataSource) dataSource).close();
    }

    @Test
    void dataSource_configuresPoolSize() {
        HikariDataSource dataSource = (HikariDataSource) config.dataSource(
                credentialsProvider, JDBC_URL, 2, 5);

        assertEquals(2, dataSource.getMinimumIdle());
        assertEquals(5, dataSource.getMaximumPoolSize());

        dataSource.close();
    }

    @Test
    void dataSource_setsPoolName() {
        HikariDataSource dataSource = (HikariDataSource) config.dataSource(
                credentialsProvider, JDBC_URL, 1, 3);

        assertEquals("RotatingSecretsPool", dataSource.getPoolName());

        dataSource.close();
    }

    @Test
    void dataSource_canExecuteQueries() throws SQLException {
        DataSource dataSource = config.dataSource(credentialsProvider, JDBC_URL, 1, 3);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        ((HikariDataSource) dataSource).close();
    }

    @Test
    void dataSource_poolsConnections() throws SQLException {
        HikariDataSource dataSource = (HikariDataSource) config.dataSource(
                credentialsProvider, JDBC_URL, 1, 3);

        try (Connection conn1 = dataSource.getConnection()) {
            assertNotNull(conn1);
        }

        try (Connection conn2 = dataSource.getConnection()) {
            assertNotNull(conn2);
        }

        assertTrue(dataSource.getHikariPoolMXBean().getTotalConnections() >= 1);

        dataSource.close();
    }
}
