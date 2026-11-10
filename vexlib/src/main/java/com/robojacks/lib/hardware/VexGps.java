package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.GpsIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;

/**
 * V5 GPS Sensor: reads the field-perimeter code strip with a camera and reports where the
 * robot is. Readings are converted to the library's field frame: meters from the field center,
 * heading counter-clockwise from +X. Feed them to a {@code PoseEstimator} to correct odometry.
 *
 * <pre>{@code
 * VexGps m_gps = new VexGps(kGpsPort, 0.0, -0.10); // sensor 10 cm behind the robot's center
 * if (m_gps.isReliable()) {
 *     m_poseEstimator.addVisionMeasurement(m_gps.getPose(), Timer.getFPGATimestamp());
 * }
 * }</pre>
 *
 * <p>The GPS can't see the strip when it's blocked or too close to the wall; {@link #getError()}
 * goes up when that happens.
 */
public class VexGps {
    /** Readings with a larger reported error than this (meters) are ignored by {@link #isReliable()}. */
    public static final double kDefaultMaxError = 0.05;

    private final GpsIO io;
    private double headingOffsetDegrees = 90.0;
    private double maxError = kDefaultMaxError;

    /** @param port smart port 1-21. The sensor is assumed to sit at the robot's center. */
    public VexGps(int port) {
        io = HAL.get().gps(port);
    }

    /**
     * @param forwardOffsetMeters how far in front of the robot's center the sensor is (negative = behind)
     * @param leftOffsetMeters    how far left of center (negative = right)
     */
    public VexGps(int port, double leftOffsetMeters, double forwardOffsetMeters) {
        this(port);
        // VEX's offset: x to the robot's right, y to its front.
        io.setOffset(-leftOffsetMeters, forwardOffsetMeters);
    }

    /** The robot's pose on the field, in the library's frame. */
    public Pose2d getPose() {
        return new Pose2d(io.getXMeters(), io.getYMeters(), getRotation2d());
    }

    /** Heading, counter-clockwise from +X, -180 to 180 degrees. */
    public Rotation2d getRotation2d() {
        return Rotation2d.fromDegrees(MathUtil.inputModulus(headingOffsetDegrees - io.getHeadingDegreesCW(), -180.0, 180.0));
    }

    /** The sensor's own error estimate, meters. */
    public double getError() {
        return io.getErrorMeters();
    }

    /** True when the reported error is small enough to trust. */
    public boolean isReliable() {
        return io.isConnected() && io.getErrorMeters() <= maxError;
    }

    public void setMaxError(double meters) {
        maxError = meters;
    }

    /**
     * Converts the GPS's compass heading: library degrees = offset - GPS degrees. The default, 90,
     * is right when the GPS's 0 points at the field's +Y wall. Check it once on a real field:
     * point the robot along +X and adjust until {@link #getRotation2d()} reads 0.
     */
    public void setHeadingOffset(double degrees) {
        headingOffsetDegrees = degrees;
    }

    /** Tells the sensor where the robot starts, so its first estimate is good before it sees the strip. */
    public void setStartingPose(Pose2d pose) {
        io.setInitialPose(pose.getX(), pose.getY(), headingOffsetDegrees - pose.getRotation().getDegrees());
    }

    public boolean isConnected() {
        return io.isConnected();
    }
}
