package com.maybeitssquid.rotatingsecrets;

import java.time.Instant;

/**
 * Result of a database poll operation.
 *
 * @param threadName   name identifying which polling thread executed the query
 * @param timestamp    instant when the query was executed (client-side)
 * @param databaseTime current time as reported by the database server
 */
public record DemoQueryResult(
        String threadName,
        Instant timestamp,
        String databaseTime
) {
    @Override
    public String toString() {
        return String.format(
                "{\"thread\": \"%s\", \"timestamp\": \"%s\", \"dbTime\": %s}",
                threadName,
                timestamp,
                databaseTime != null ? "\"" + databaseTime + "\"" : "null"
        );
    }
}
