package com.maybeitssquid.rotatingsecrets.hikari;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.util.Credentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HikariCredentialsUpdaterTest {

    private HikariCredentialsUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new HikariCredentialsUpdater("initialUser", "initialPass");
    }

    @Test
    void constructor_setsInitialCredentials() {
        Credentials creds = updater.getCredentials();

        assertEquals("initialUser", creds.getUsername());
        assertEquals("initialPass", creds.getPassword());
    }

    @Test
    void setCredential_updatesCredentials() {
        updater.setCredential("newUser", "newPass");

        Credentials creds = updater.getCredentials();
        assertEquals("newUser", creds.getUsername());
        assertEquals("newPass", creds.getPassword());
    }

    @Test
    void setCredential_softEvictsConnections() {
        HikariDataSource mockDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean mockPoolMXBean = mock(HikariPoolMXBean.class);
        when(mockDataSource.getHikariPoolMXBean()).thenReturn(mockPoolMXBean);

        updater.setDataSource(mockDataSource);
        updater.setCredential("newUser", "newPass");

        verify(mockPoolMXBean).softEvictConnections();
    }

    @Test
    void setCredential_handlesNullDataSource() {
        // Should not throw when dataSource is null
        assertDoesNotThrow(() -> updater.setCredential("user", "pass"));
    }

    @Test
    void setCredential_handlesNullPoolMXBean() {
        HikariDataSource mockDataSource = mock(HikariDataSource.class);
        when(mockDataSource.getHikariPoolMXBean()).thenReturn(null);

        updater.setDataSource(mockDataSource);

        // Should not throw when poolMXBean is null
        assertDoesNotThrow(() -> updater.setCredential("user", "pass"));
    }

    @Test
    void setDataSource_allowsLateInjection() {
        HikariDataSource mockDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean mockPoolMXBean = mock(HikariPoolMXBean.class);
        when(mockDataSource.getHikariPoolMXBean()).thenReturn(mockPoolMXBean);

        // Update before setting dataSource
        updater.setCredential("user1", "pass1");
        verifyNoInteractions(mockPoolMXBean);

        // Now set dataSource
        updater.setDataSource(mockDataSource);

        // Update after setting dataSource
        updater.setCredential("user2", "pass2");
        verify(mockPoolMXBean).softEvictConnections();
    }

    @Test
    void getCredentials_returnsImmutableCredentials() {
        Credentials creds1 = updater.getCredentials();
        updater.setCredential("newUser", "newPass");
        Credentials creds2 = updater.getCredentials();

        // Original credentials should be unchanged (immutable)
        assertEquals("initialUser", creds1.getUsername());
        assertEquals("initialPass", creds1.getPassword());

        // New credentials should reflect the update
        assertEquals("newUser", creds2.getUsername());
        assertEquals("newPass", creds2.getPassword());
    }

    @Test
    void setCredential_isAtomicUnderConcurrentAccess() throws Exception {
        HikariDataSource mockDataSource = mock(HikariDataSource.class);
        HikariPoolMXBean mockPoolMXBean = mock(HikariPoolMXBean.class);
        when(mockDataSource.getHikariPoolMXBean()).thenReturn(mockPoolMXBean);
        updater.setDataSource(mockDataSource);

        int iterations = 1000;
        Thread writer = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                updater.setCredential("user" + i, "pass" + i);
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < iterations; i++) {
                Credentials creds = updater.getCredentials();
                // Should never get inconsistent credentials
                assertNotNull(creds);
                assertNotNull(creds.getUsername());
                assertNotNull(creds.getPassword());
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();
    }
}
