package com.robojacks.lib.hal;

/** V5 Inertial Sensor. */
public interface ImuIO {
    /** True for the ~2 s after power-up/calibrate(). Don't trust heading while true. */
    boolean isCalibrating();

    void calibrate();

    /**
     * Continuous heading in degrees, CLOCKWISE positive, NOT wrapped to 0..360
     * (VEX calls this "rotation"). Drivetrain converts to CCW-positive.
     */
    double getRotationDegreesCW();

    void resetRotation();
}
