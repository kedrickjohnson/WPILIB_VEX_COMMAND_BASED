package com.robojacks.lib.telemetry;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.Pose2d;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stand-in for SmartDashboard: collects key/values and prints one line over USB serial at a
 * limited rate. The dashboard page on the docs site reads those lines live (Chrome or Edge, over
 * USB) or from a saved log, graphs numbers and draws poses on the field.
 *
 * <pre>{@code
 * // in a subsystem's periodic():
 * Telemetry.put("arm/degrees", getAngleDegrees());
 * Telemetry.put("intake/hasPiece", hasPiece());
 * Telemetry.putPose("drive/pose", getPose());
 * }</pre>
 *
 * <p>Line format: {@code [t=12.34] arm/degrees=45.0 intake/hasPiece=true drive/pose=-1.2,0.6,90.0}.
 * Keys can't contain spaces or '='. Poses are x,y in meters and heading in degrees.
 */
public final class Telemetry {
    private static final Map<String, String> values = new LinkedHashMap<>();
    private static double periodSeconds = 0.25;
    private static double lastFlush = -1e9;
    private static boolean enabled = true;
    private static Consumer<String> sink = System.out::println;

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
        values.put(key, value.replace(' ', '_'));
    }

    /** A field pose: the dashboard draws it on the field. */
    public static void putPose(String key, Pose2d pose) {
        values.put(key, round(pose.getX()) + "," + round(pose.getY()) + "," + round(pose.getRotation().getDegrees()));
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    /** Where lines go. Default is System.out (USB serial on the brain). The simulator can send them to a file. */
    public static void setSink(Consumer<String> output) {
        sink = output;
    }

    /** Forgets all values (they're otherwise printed until overwritten). */
    public static void clear() {
        values.clear();
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
        sink.accept(sb.toString());
    }
}
