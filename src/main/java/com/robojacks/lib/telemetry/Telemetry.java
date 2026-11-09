package com.robojacks.lib.telemetry;

import com.robojacks.lib.hal.HAL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stand-in for SmartDashboard: collects key/values and prints one line over USB serial
 * at a limited rate. Pipe the serial output into a log file or a small plotting script.
 */
public final class Telemetry {
    private static final Map<String, String> values = new LinkedHashMap<>();
    private static double periodSeconds = 0.25;
    private static double lastFlush = -1e9;
    private static boolean enabled = true;

    private Telemetry() {}

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    /** How often flush() actually prints. */
    public static void setPeriod(double seconds) {
        periodSeconds = seconds;
    }

    public static void put(String key, double value) {
        values.put(key, String.valueOf(Math.round(value * 1000.0) / 1000.0));
    }

    public static void put(String key, boolean value) {
        values.put(key, String.valueOf(value));
    }

    public static void put(String key, String value) {
        values.put(key, value);
    }

    /** Call once per loop (Robot.robotPeriodic does this). */
    public static void flush() {
        if (!enabled || values.isEmpty()) {
            return;
        }
        double now = HAL.now();
        if (now - lastFlush < periodSeconds) {
            return;
        }
        lastFlush = now;
        StringBuilder sb = new StringBuilder();
        sb.append("[t=").append(Math.round(now * 100.0) / 100.0).append("]");
        for (Map.Entry<String, String> e : values.entrySet()) {
            sb.append(' ').append(e.getKey()).append('=').append(e.getValue());
        }
        System.out.println(sb);
    }
}
