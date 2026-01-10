package com.maybeitssquid.rotatingsecrets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabasePollingServiceTest {

    private final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    private Connection h2Connection;
    private DataSource dataSource;
    private DatabasePollingService service;

    @BeforeEach
    void setUp() throws SQLException {
        System.setOut(new PrintStream(outputCapture));

        // Create H2 database with Oracle compatibility for DUAL table
        h2Connection = DriverManager.getConnection(
                "jdbc:h2:mem:testdb;MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");

        // Create DUAL table for Oracle compatibility
        try (Statement stmt = h2Connection.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS DUAL (DUMMY VARCHAR(1))");
            stmt.execute("DELETE FROM DUAL");
            stmt.execute("INSERT INTO DUAL VALUES ('X')");
        }

        dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(h2Connection);

        service = new DatabasePollingService(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        System.setOut(originalOut);
        if (h2Connection != null && !h2Connection.isClosed()) {
            h2Connection.close();
        }
    }

    @Test
    void pollEveryFiveSeconds_executesQuery() throws SQLException {
        service.pollEveryFiveSeconds();

        verify(dataSource).getConnection();
        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\": \"5-second-poller\""));
    }

    @Test
    void pollEveryThreeSeconds_executesQuery() throws SQLException {
        service.pollEveryThreeSeconds();

        verify(dataSource).getConnection();
        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\": \"3-second-poller\""));
    }

    @Test
    void poll_outputsJsonFormat() throws SQLException {
        service.pollEveryFiveSeconds();

        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\":"));
        assertTrue(output.contains("\"timestamp\":"));
        assertTrue(output.contains("\"dbTime\":"));
    }

    @Test
    void poll_handlesSqlException() throws SQLException {
        DataSource failingDataSource = mock(DataSource.class);
        when(failingDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        DatabasePollingService failingService = new DatabasePollingService(failingDataSource);

        assertDoesNotThrow(failingService::pollEveryFiveSeconds);
    }

    @Test
    void poll_returnsConnectionToPool() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("Query failed"));

        DatabasePollingService mockService = new DatabasePollingService(dataSource);
        mockService.pollEveryFiveSeconds();

        verify(mockConnection).close();
    }
}
