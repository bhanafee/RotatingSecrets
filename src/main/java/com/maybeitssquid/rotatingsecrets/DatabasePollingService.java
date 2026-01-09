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

@Service
public class DatabasePollingService {

    private static final Logger log = LoggerFactory.getLogger(DatabasePollingService.class);
    private static final String QUERY = "SELECT TO_CHAR(SYSDATE, 'YYYY-MM-DD HH24:MI:SS') FROM DUAL";

    private final DataSource dataSource;

    public DatabasePollingService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Scheduled(fixedRate = 5000)
    public void pollEveryFiveSeconds() {
        executeQuery("5-second-poller");
    }

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

            QueryResult result = new QueryResult(
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
