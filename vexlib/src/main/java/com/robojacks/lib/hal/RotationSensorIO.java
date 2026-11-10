package com.robojacks.lib.hal;

/** V5 Rotation Sensor (an absolute encoder). Raw access; robot code uses {@code VexRotationSensor}. */
public interface RotationSensorIO {
    /** Continuous position in rotations (counts past one turn), counter-clockwise looking at the sensor face unless reversed. */
    double getPositionRotations();

    /** Velocity in rotations per second. */
    double getVelocityRps();

    /** Absolute angle within one turn, 0 to 1 rotation. Kept through power cycles. */
    double getAbsoluteRotations();

    /** Sets the continuous position (the absolute angle is not affected). */
    void setPositionRotations(double rotations);

    void setReversed(boolean reversed);

    boolean isConnected();
}
