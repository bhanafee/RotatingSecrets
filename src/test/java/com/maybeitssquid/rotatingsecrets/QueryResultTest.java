package com.maybeitssquid.rotatingsecrets;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class QueryResultTest {

    @Test
    void recordComponents_areAccessible() {
        Instant now = Instant.now();
        QueryResult result = new QueryResult("test-thread", now, "2025-01-09 12:00:00");

        assertEquals("test-thread", result.threadName());
        assertEquals(now, result.timestamp());
        assertEquals("2025-01-09 12:00:00", result.databaseTime());
    }

    @Test
    void toString_returnsJsonFormat() {
        Instant timestamp = Instant.parse("2025-01-09T12:00:00Z");
        QueryResult result = new QueryResult("poller", timestamp, "2025-01-09 12:00:00");

        String json = result.toString();

        assertTrue(json.contains("\"thread\": \"poller\""));
        assertTrue(json.contains("\"timestamp\": \"2025-01-09T12:00:00Z\""));
        assertTrue(json.contains("\"dbTime\": \"2025-01-09 12:00:00\""));
    }

    @Test
    void toString_handlesNullDatabaseTime() {
        QueryResult result = new QueryResult("poller", Instant.now(), null);

        String json = result.toString();

        assertTrue(json.contains("\"dbTime\": \"null\""));
    }

    @Test
    void equals_comparesAllFields() {
        Instant now = Instant.now();
        QueryResult a = new QueryResult("thread", now, "time");
        QueryResult b = new QueryResult("thread", now, "time");
        QueryResult c = new QueryResult("other", now, "time");

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void hashCode_isConsistent() {
        Instant now = Instant.now();
        QueryResult a = new QueryResult("thread", now, "time");
        QueryResult b = new QueryResult("thread", now, "time");

        assertEquals(a.hashCode(), b.hashCode());
    }
}
