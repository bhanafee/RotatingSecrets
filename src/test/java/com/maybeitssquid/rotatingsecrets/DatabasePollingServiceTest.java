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
    private DemoDatabasePollingService service;

    @BeforeEach
    void setUp() throws SQLException {
        System.setOut(new PrintStream(outputCapture));

        // Create H2 in-memory database
        h2Connection = DriverManager.getConnection(
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");

        dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenReturn(h2Connection);

        service = new DemoDatabasePollingService(dataSource);
    }

    @AfterEach
    void tearDown() throws SQLException {
        System.setOut(originalOut);
        if (h2Connection != null && !h2Connection.isClosed()) {
            h2Connection.close();
        }
    }

    @Test
    void pollSlow_executesQuery() throws SQLException {
        service.pollSlow();

        verify(dataSource).getConnection();
        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\": \"slow-poller\""));
    }

    @Test
    void pollFast_executesQuery() throws SQLException {
        service.pollFast();

        verify(dataSource).getConnection();
        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\": \"fast-poller\""));
    }

    @Test
    void poll_outputsJsonFormat() throws SQLException {
        service.pollSlow();

        String output = outputCapture.toString();
        assertTrue(output.contains("\"thread\":"));
        assertTrue(output.contains("\"timestamp\":"));
        assertTrue(output.contains("\"dbTime\":"));
    }

    @Test
    void poll_handlesSqlException() throws SQLException {
        DataSource failingDataSource = mock(DataSource.class);
        when(failingDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        DemoDatabasePollingService failingService = new DemoDatabasePollingService(failingDataSource);

        assertDoesNotThrow(failingService::pollSlow);
    }

    @Test
    void poll_returnsConnectionToPool() throws SQLException {
        Connection mockConnection = mock(Connection.class);
        Statement mockStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockStatement.executeQuery(anyString())).thenThrow(new SQLException("Query failed"));

        DemoDatabasePollingService mockService = new DemoDatabasePollingService(dataSource);
        mockService.pollSlow();

        verify(mockConnection).close();
    }
}
