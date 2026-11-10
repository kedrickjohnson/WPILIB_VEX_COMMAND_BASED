package com.robojacks.lib.hal;

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
     * Where the sensor sits on the robot, relative to the robot's center, in meters
     * (x to the robot's right, y to its front, as VEX defines it).
     */
    void setOffset(double xMeters, double yMeters);

    /** Seeds the sensor's estimate before it sees the field (x, y in meters, compass heading). */
    void setInitialPose(double xMeters, double yMeters, double headingDegreesCW);

    boolean isConnected();
}
