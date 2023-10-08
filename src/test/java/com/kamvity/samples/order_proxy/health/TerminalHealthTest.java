package com.kamvity.samples.order_proxy.health;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class TerminalHealthTest {

    @Test
    public void testSetStatus() {
        TerminalHealth.status = TerminalHealth.RUNNING;
        assertEquals(TerminalHealth.RUNNING,TerminalHealth.status);
    }

    @Test
    public void testSetFailedTime() {
        TerminalHealth.failedTime = Timestamp.from(Instant.now());
        assert(TerminalHealth.failedTime.before(Timestamp.from(Instant.now())) || TerminalHealth.failedTime.equals(Timestamp.from(Instant.now())));
        assert(TerminalHealth.failedTime.after(Timestamp.from(Instant.now().minusMillis(TimeUnit.MINUTES.toMillis(5)))));
    }
}
