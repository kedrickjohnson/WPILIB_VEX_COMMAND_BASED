package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.GpsIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Transform2d;

/**
 * V5 GPS Sensor: reads the field-perimeter code strip with a camera and reports where it is.
 * Used like a PhotonVision camera: tell it where it's mounted with a robot-to-sensor transform,
 * and {@link #getPose()} returns the <em>robot's</em> pose on the field.
 *
 * <h2>Mounting: robot-to-GPS transform</h2>
 * Same convention as WPILib and PhotonVision's {@code robotToCamera}: measured from the robot's
 * center, in the robot's coordinate system
 * <ul>
 *   <li>+X forward (toward the robot's front),</li>
 *   <li>+Y left,</li>
 *   <li>rotation counter-clockwise positive, viewed from above: the direction the GPS camera faces
 *       relative to the robot's front (0 = facing forward, 180 = facing backward, 90 = facing
 *       the robot's left side, -90 = its right side).</li>
 * </ul>
 *
 * <pre>{@code
 * // GPS 10 cm behind center and 5 cm right of it, camera facing out the back
 * Transform2d kRobotToGps = new Transform2d(
 *         new Translation2d(-0.10, -0.05), Rotation2d.fromDegrees(180));
 * VexGps m_gps = new VexGps(kGpsPort, kRobotToGps);
 *
 * Pose2d robotPose = m_gps.getPose();        // the robot's center, field frame
 * Pose2d sensorPose = m_gps.getSensorPose(); // where the sensor itself is
 * }</pre>
 *
 * <p>Field frame: meters from the field center, heading counter-clockwise from +X (see the
 * Units &amp; field coordinates guide). Feed poses to a {@code PoseEstimator} through a
 * {@code GpsPoseEstimator} to correct odometry.
 *
 * <p>The GPS can't see the strip when it's blocked or too close to the wall; {@link #getError()}
 * goes up when that happens.
 */
public class VexGps {
    /** Readings with a larger reported error than this (meters) are ignored by {@link #isReliable()}. */
    public static final double kDefaultMaxError = 0.05;

    private final GpsIO io;
    private Transform2d robotToGps = new Transform2d(0.0, 0.0, Rotation2d.kZero);
    private Transform2d gpsToRobot = robotToGps.inverse();
    private double fieldHeadingOffsetDegrees = 90.0;
    private double maxError = kDefaultMaxError;

    /** A GPS mounted at the robot's center, facing forward. */
    public VexGps(int port) {
        io = HAL.get().gps(port);
        // All mounting math is done here, in WPILib's robot frame, so the sensor's own offset is cleared.
        io.setOffset(0.0, 0.0);
        io.declareMounting(robotToGps);
    }

    /**
     * @param port       smart port 1-21
     * @param robotToGps where the GPS is on the robot and which way its camera faces, from the
     *                   robot's center (+X forward, +Y left, CCW rotation)
     */
    public VexGps(int port, Transform2d robotToGps) {
        this(port);
        setRobotToGps(robotToGps);
    }

    /** Changes where the GPS is mounted (robot-to-GPS transform, WPILib robot frame). */
    public void setRobotToGps(Transform2d robotToGps) {
        this.robotToGps = robotToGps;
        gpsToRobot = robotToGps.inverse();
        io.declareMounting(robotToGps);
    }

    public Transform2d getRobotToGps() {
        return robotToGps;
    }

    /** Where the sensor itself is on the field, facing the way its camera faces. */
    public Pose2d getSensorPose() {
        double deg = MathUtil.inputModulus(fieldHeadingOffsetDegrees - io.getHeadingDegreesCW(), -180.0, 180.0);
        return new Pose2d(io.getXMeters(), io.getYMeters(), Rotation2d.fromDegrees(deg));
    }

    /** The robot's center on the field: the sensor's pose moved back through the mounting transform. */
    public Pose2d getPose() {
        return getSensorPose().plus(gpsToRobot);
    }

    /** The robot's heading (counter-clockwise from +X, -180 to 180 degrees), corrected for mounting. */
    public Rotation2d getRotation2d() {
        return getPose().getRotation();
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
     * Converts the GPS's compass heading into the field frame: field degrees = offset - GPS
     * degrees. This is about which field wall is +X, not about mounting (that's
     * {@link #setRobotToGps}). The default, 90, is right when the GPS's 0 points at the field's +Y
     * wall. Check it once on a real field: with a forward-facing GPS, point the robot along +X and
     * adjust until {@link #getRotation2d()} reads 0.
     */
    public void setFieldHeadingOffset(double degrees) {
        fieldHeadingOffsetDegrees = degrees;
    }

    /** Tells the sensor where the robot starts, so its first estimate is good before it sees the strip. */
    public void setStartingPose(Pose2d robotPose) {
        Pose2d sensor = robotPose.plus(robotToGps);
        io.setInitialPose(sensor.getX(), sensor.getY(),
                MathUtil.inputModulus(fieldHeadingOffsetDegrees - sensor.getRotation().getDegrees(), 0.0, 360.0));
    }

    public boolean isConnected() {
        return io.isConnected();
    }
}
