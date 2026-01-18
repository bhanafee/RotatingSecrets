package com.maybeitssquid.rotatingsecrets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

/**
 * Demonstrates database connectivity by polling the database on scheduled intervals.
 *
 * <p>Runs two polling threads that check out connections from the pool, execute
 * a simple query, and print results to stdout. This exercises the connection pool
 * and credential rotation behavior.</p>
 */
@Service
public class DemoDatabasePollingService {

    private static final Logger log = LoggerFactory.getLogger(DemoDatabasePollingService.class);
    private static final String QUERY = "SELECT CURRENT_TIMESTAMP";

    private final DataSource dataSource;

    /**
     * Creates the polling service with the given DataSource.
     *
     * @param dataSource the connection pool to use for queries
     */
    public DemoDatabasePollingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Polls the database every 5 seconds.
     */
    @Scheduled(fixedRate = 5000)
    public void pollEveryFiveSeconds() {
        executeQuery("5-second-poller");
    }

    /**
     * Polls the database every 3 seconds.
     */
    @Scheduled(fixedRate = 3000)
    public void pollEveryThreeSeconds() {
        executeQuery("3-second-poller");
    }

    private void executeQuery(String threadName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(QUERY)) {

            String dbTime = null;
            if (rs.next()) {
                dbTime = rs.getString(1);
            }

            DemoQueryResult result = new DemoQueryResult(
                    threadName,
                    Instant.now(),
                    dbTime
            );

            System.out.println(result);

        } catch (SQLException e) {
            log.error("Error executing query in {}: {}", threadName, e.getMessage());
        }
    }
}
