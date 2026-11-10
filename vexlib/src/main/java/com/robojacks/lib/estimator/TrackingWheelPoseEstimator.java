package com.robojacks.lib.estimator;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.kinematics.TrackingWheelOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;

/**
 * Tracking-wheel odometry ({@link TrackingWheelOdometry}) corrected by GPS readings.
 *
 * <p>Wheel locations are from the robot's center in WPILib's robot frame (+X forward, +Y left).
 *
 * <pre>{@code
 * m_poseEstimator = new TrackingWheelPoseEstimator(
 *         kForwardWheelLocation, kSidewaysWheelLocation,
 *         m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition(), startPose);
 *
 * // periodic():
 * m_poseEstimator.update(m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition());
 * }</pre>
 */
public class TrackingWheelPoseEstimator extends PoseEstimator {
    private final TrackingWheelOdometry odometry;

    public TrackingWheelPoseEstimator(double forwardWheelLeftOffset, double sidewaysWheelForwardOffset,
                                      Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance,
                                      Pose2d initialPose) {
        this(forwardWheelLeftOffset, sidewaysWheelForwardOffset, gyroAngle, forwardDistance, sidewaysDistance,
                initialPose, new double[] {0.01, 0.01, 0.01}, new double[] {0.1, 0.1, 0.1});
    }

    /** Tracking wheels drift less than drive wheels, so the default odometry trust is higher (0.01 m). */
    public TrackingWheelPoseEstimator(double forwardWheelLeftOffset, double sidewaysWheelForwardOffset,
                                      Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance,
                                      Pose2d initialPose, double[] stateStdDevs, double[] measurementStdDevs) {
        super(initialPose, stateStdDevs, measurementStdDevs);
        odometry = new TrackingWheelOdometry(forwardWheelLeftOffset, sidewaysWheelForwardOffset,
                gyroAngle, forwardDistance, sidewaysDistance, initialPose);
    }

    /** Wheel locations as robot-frame translations (see {@link TrackingWheelOdometry}). */
    public TrackingWheelPoseEstimator(Translation2d forwardWheelLocation, Translation2d sidewaysWheelLocation,
                                      Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance,
                                      Pose2d initialPose) {
        this(forwardWheelLocation.getY(), sidewaysWheelLocation.getX(), gyroAngle, forwardDistance, sidewaysDistance, initialPose);
    }

    public TrackingWheelPoseEstimator(Translation2d forwardWheelLocation, Translation2d sidewaysWheelLocation,
                                      Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance,
                                      Pose2d initialPose, double[] stateStdDevs, double[] measurementStdDevs) {
        this(forwardWheelLocation.getY(), sidewaysWheelLocation.getX(), gyroAngle, forwardDistance, sidewaysDistance,
                initialPose, stateStdDevs, measurementStdDevs);
    }

    public Pose2d update(Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance) {
        return addOdometry(HAL.now(), odometry.update(gyroAngle, forwardDistance, sidewaysDistance));
    }

    public void resetPosition(Rotation2d gyroAngle, double forwardDistance, double sidewaysDistance, Pose2d pose) {
        odometry.resetPosition(gyroAngle, forwardDistance, sidewaysDistance, pose);
        resetEstimate(pose, pose);
    }

    public Pose2d getOdometryPose() {
        return odometry.getPoseMeters();
    }
}
