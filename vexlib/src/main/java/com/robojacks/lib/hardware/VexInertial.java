package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.math.Rotation2d;

/**
 * V5 Inertial Sensor, used like a gyro in FRC (Pigeon2, NavX, ADXRS450):
 * {@link #getAngle()} is clockwise-positive degrees like WPILib's Gyro interface, and
 * {@link #getRotation2d()} is counter-clockwise-positive for odometry.
 *
 * <p>Calibration starts automatically and takes about 2 seconds. Keep the robot still.
 */
public class VexInertial {
    private final ImuIO io;

    public VexInertial(int port) {
        io = HAL.get().imu(port);
        io.calibrate();
    }

    public void calibrate() {
        io.calibrate();
    }

    public boolean isCalibrating() {
        return io.isCalibrating();
    }

    /** Unwrapped angle in degrees, CLOCKWISE positive (WPILib Gyro convention). */
    public double getAngle() {
        return io.getRotationDegreesCW();
    }

    /** Heading for odometry: COUNTER-CLOCKWISE positive, unwrapped. */
    public Rotation2d getRotation2d() {
        return Rotation2d.fromDegrees(-io.getRotationDegreesCW());
    }

    public void reset() {
        io.resetRotation();
    }
}
