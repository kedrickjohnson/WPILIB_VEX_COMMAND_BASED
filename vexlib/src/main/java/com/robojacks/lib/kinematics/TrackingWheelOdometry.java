package com.robojacks.lib.kinematics;

import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Twist2d;

/**
 * Odometry from unpowered tracking wheels (on Rotation Sensors or 3-wire encoders) plus the
 * Inertial Sensor for heading. Tracking wheels don't slip when the drive pushes or spins its
 * wheels, so they stay accurate where drive-encoder odometry drifts. This is the standard
 * setup for competitive VEX autonomous.
 *
 * <p>Typical layout: one wheel that rolls forward/back ({@code forward}) and one that rolls
 * sideways ({@code sideways}). Each wheel's offset from the robot's turning center corrects for
 * the motion it sees when the robot spins in place.
 *
 * <pre>{@code
 * // Forward wheel 1.5" left of center, sideways wheel 3" behind center
 * TrackingWheelOdometry m_odometry = new TrackingWheelOdometry(
 *         Units.inchesToMeters(1.5), Units.inchesToMeters(-3.0),
 *         m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition());
 *
 * // in periodic():
 * m_odometry.update(m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition());
 * }</pre>
 *
 * <p>No sideways wheel? Pass 0 for its distance every time. The robot is then assumed not to
 * slide sideways, which is true for most tank drives unless they're pushed.
 */
public class TrackingWheelOdometry {
    private final double forwardWheelLeftOffset;
    private final double sidewaysWheelForwardOffset;
    private Pose2d pose;
    private Rotation2d gyroOffset;
    private Rotation2d previousAngle;
    private double prevForward;
    private double prevSideways;

    /**
     * @param forwardWheelLeftOffset     meters the forward-rolling wheel is LEFT of the turning
     *                                   center (negative = right of center)
     * @param sidewaysWheelForwardOffset meters the sideways-rolling wheel is IN FRONT of the
     *                                   turning center (negative = behind)
     * @param gyroAngle                  current heading, counter-clockwise positive
     * @param forwardDistance            forward wheel's travel so far, meters (positive = robot moving forward)
     * @param sidewaysDistance           sideways wheel's travel so far, meters (positive = robot moving left)
     * @param initialPose                where the robot starts
     */
    public TrackingWheelOdometry(double forwardWheelLeftOffset, double sidewaysWheelForwardOffset,
                                 Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance,
                                 Pose2d initialPose) {
        this.forwardWheelLeftOffset = forwardWheelLeftOffset;
        this.sidewaysWheelForwardOffset = sidewaysWheelForwardOffset;
        resetPosition(gyroAngle, forwardDistance, sidewaysDistance, initialPose);
    }

    /** Starts at the field origin. */
    public TrackingWheelOdometry(double forwardWheelLeftOffset, double sidewaysWheelForwardOffset,
                                 Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance) {
        this(forwardWheelLeftOffset, sidewaysWheelForwardOffset, gyroAngle, forwardDistance, sidewaysDistance, Pose2d.kZero);
    }

    /** Teleports the robot to {@code pose} without resetting the sensors. */
    public void resetPosition(Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance, Pose2d pose) {
        this.pose = pose;
        previousAngle = pose.getRotation();
        gyroOffset = pose.getRotation().minus(gyroAngle);
        prevForward = forwardDistance;
        prevSideways = sidewaysDistance;
    }

    public Pose2d getPoseMeters() {
        return pose;
    }

    /** Call every loop with the latest readings. Returns the new pose. */
    public Pose2d update(Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance) {
        double dForward = forwardDistance - prevForward;
        double dSideways = sidewaysDistance - prevSideways;
        prevForward = forwardDistance;
        prevSideways = sidewaysDistance;

        Rotation2d angle = gyroAngle.plus(gyroOffset);
        double dTheta = angle.minus(previousAngle).getRadians();

        // Remove the part of each wheel's travel caused by the robot turning about its center:
        // a wheel left of center rolls backward when the robot turns counter-clockwise, and a
        // wheel in front of center rolls left.
        double dx = dForward + forwardWheelLeftOffset * dTheta;
        double dy = dSideways - sidewaysWheelForwardOffset * dTheta;

        Pose2d newPose = pose.exp(new Twist2d(dx, dy, dTheta));
        previousAngle = angle;
        pose = new Pose2d(newPose.getTranslation(), angle); // the IMU is the heading source
        return pose;
    }
}
