package com.kamvity.samples.order_proxy.mira;

import java.sql.Timestamp;
import java.time.Instant;

public class MiraHealth {

    public static Timestamp failedTime;
    public static String status;
    public static final String RUNNING = "running";
    public static final String STOPPED = "stopped";

    public static synchronized void setRunning() {
        status = RUNNING;
    }

    public static synchronized void setStopped() {
        status = STOPPED;
        failedTime = Timestamp.from(Instant.now());
    }
}
