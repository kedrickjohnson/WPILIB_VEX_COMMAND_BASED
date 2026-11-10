package com.robojacks.lib.estimator;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;

/**
 * Tank-drive odometry (drive encoders + Inertial Sensor) corrected by GPS readings. Drop-in
 * replacement for {@link DifferentialDriveOdometry}, like WPILib's class of the same name.
 *
 * <pre>{@code
 * private final DifferentialDrivePoseEstimator m_poseEstimator = new DifferentialDrivePoseEstimator(
 *         m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance(), new Pose2d());
 * private final VexGps m_gps = new VexGps(kGpsPort, kRobotToGps); // mounting from robot center
 *
 * public void periodic() {
 *     m_poseEstimator.update(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance());
 *     if (m_gps.isReliable()) {
 *         double std = Math.max(m_gps.getError(), 0.02);
 *         m_poseEstimator.addVisionMeasurement(m_gps.getPose(), Timer.getFPGATimestamp(), std, std, 1000);
 *     }
 * }
 * }</pre>
 */
public class DifferentialDrivePoseEstimator extends PoseEstimator {
    private final DifferentialDriveOdometry odometry;

    /** WPILib's default trust values: odometry (0.02 m, 0.02 m, 0.01 rad), measurements (0.1, 0.1, 0.1). */
    public DifferentialDrivePoseEstimator(Rotation2d gyroAngle, double leftDistanceMeters,
                                          double rightDistanceMeters, Pose2d initialPose) {
        this(gyroAngle, leftDistanceMeters, rightDistanceMeters, initialPose,
                new double[] {0.02, 0.02, 0.01}, new double[] {0.1, 0.1, 0.1});
    }

    public DifferentialDrivePoseEstimator(Rotation2d gyroAngle, double leftDistanceMeters,
                                          double rightDistanceMeters, Pose2d initialPose,
                                          double[] stateStdDevs, double[] measurementStdDevs) {
        super(initialPose, stateStdDevs, measurementStdDevs);
        odometry = new DifferentialDriveOdometry(gyroAngle, leftDistanceMeters, rightDistanceMeters, initialPose);
    }

    /** Call every loop. Returns the new estimate. */
    public Pose2d update(Rotation2d gyroAngle, double leftDistanceMeters, double rightDistanceMeters) {
        return addOdometry(HAL.now(), odometry.update(gyroAngle, leftDistanceMeters, rightDistanceMeters));
    }

    /** Teleports the robot (start of an auto). */
    public void resetPosition(Rotation2d gyroAngle, double leftDistanceMeters, double rightDistanceMeters, Pose2d pose) {
        odometry.resetPosition(gyroAngle, leftDistanceMeters, rightDistanceMeters, pose);
        resetEstimate(pose, pose);
    }

    /** The odometry-only pose, without measurements (handy for comparing). */
    public Pose2d getOdometryPose() {
        return odometry.getPoseMeters();
    }
}
