package com.kamvity.samples.order_proxy.mira;

import java.sql.Timestamp;
import java.time.Instant;

public class MiraHealth {

    private static Timestamp failedTime;
    private static String status;
    public static final String RUNNING = "running";
    public static final String STOPPED = "stopped";

    private MiraHealth() {throw new IllegalStateException("Utility class");}

    public static String getStatus() {
        return status;
    }
    public static synchronized void setRunning() {
        status = RUNNING;
    }

    public static synchronized void setStopped() {
        status = STOPPED;
        failedTime = Timestamp.from(Instant.now());
    }

    public static Timestamp getFailedTime() {
        return failedTime;
    }

    protected static void setFailedTime(Timestamp timestamp) {
        failedTime = timestamp;
    }

}
