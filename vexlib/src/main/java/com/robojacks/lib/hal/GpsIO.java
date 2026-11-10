package com.robojacks.lib.hal;

import com.robojacks.lib.math.Transform2d;

/**
 * V5 GPS Sensor, in the sensor's own units: meters from the field center and a compass
 * heading (degrees, clockwise, 0 = toward the field's +Y wall). Robot code uses {@code VexGps},
 * which converts to the library's convention.
 */
public interface GpsIO {
    double getXMeters();

    double getYMeters();

    /** Compass heading, degrees clockwise, 0 to 360. */
    double getHeadingDegreesCW();

    /** The sensor's own estimate of its position error, meters. Large when it can't see the field strip. */
    double getErrorMeters();

    /**
     * The sensor's own built-in offset (VEX's convention: x to the robot's right, y to its front).
     * {@code VexGps} sets it to 0 and does the mounting math itself in WPILib's robot frame.
     */
    void setOffset(double xMeters, double yMeters);

    /**
     * Tells the hardware layer how the robot code says the sensor is mounted. The real brain
     * ignores it; the simulator uses it as the sensor's physical mounting (unless the sim script
     * sets a different one), so simulated readings match the robot code.
     */
    default void declareMounting(Transform2d robotToGps) {}

    /** Seeds the sensor's estimate before it sees the field (x, y in meters, compass heading). */
    void setInitialPose(double xMeters, double yMeters, double headingDegreesCW);

    boolean isConnected();
}
