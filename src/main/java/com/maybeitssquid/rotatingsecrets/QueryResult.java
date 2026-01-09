package com.maybeitssquid.rotatingsecrets;

import java.time.Instant;

public record QueryResult(
        String threadName,
        Instant timestamp,
        String databaseTime
) {
    @Override
    public String toString() {
        return String.format(
                "{\"thread\": \"%s\", \"timestamp\": \"%s\", \"dbTime\": \"%s\"}",
                threadName, timestamp, databaseTime
        );
    }
}
