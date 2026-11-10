package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.DistanceSensorIO;
import com.robojacks.lib.hal.HAL;

/**
 * V5 Distance Sensor: a laser time-of-flight sensor that reads up to about 2 meters.
 *
 * <pre>{@code
 * VexDistanceSensor m_front = new VexDistanceSensor(kFrontDistancePort);
 *
 * // Is something within 15 cm?
 * Trigger objectClose = new Trigger(() -> m_front.isWithin(0.15));
 * }</pre>
 */
public class VexDistanceSensor {
    /** Below this confidence (0-63) readings beyond about 0.2 m are unreliable. */
    public static final int kMinConfidence = 30;

    private final DistanceSensorIO io;

    /** @param port smart port 1-21 */
    public VexDistanceSensor(int port) {
        io = HAL.get().distanceSensor(port);
    }

    /** Distance in meters, or {@code Double.NaN} when nothing is in range. */
    public double getDistanceMeters() {
        return io.getDistanceMeters();
    }

    /** True when the sensor sees an object at all. */
    public boolean hasTarget() {
        return !Double.isNaN(io.getDistanceMeters());
    }

    /** True when an object is closer than {@code meters}. False when nothing is in range. */
    public boolean isWithin(double meters) {
        double d = io.getDistanceMeters();
        return !Double.isNaN(d) && d <= meters;
    }

    /** 0 to 63. Close readings (under 0.2 m) always report 63. */
    public int getConfidence() {
        return io.getConfidence();
    }

    /** Rough object size, 0 to 400. */
    public int getObjectSize() {
        return io.getObjectSize();
    }

    /** m/s, positive moving away. */
    public double getObjectVelocity() {
        return io.getObjectVelocityMps();
    }

    public boolean isConnected() {
        return io.isConnected();
    }
}
