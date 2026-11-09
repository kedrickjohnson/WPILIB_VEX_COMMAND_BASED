package com.robojacks.lib.kinematics;

import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Twist2d;

/**
 * Tank-drive odometry, same API as WPILib's DifferentialDriveOdometry.
 *
 * <p>Distances are total wheel travel in meters; the gyro angle is CCW-positive.
 * Heading comes from the gyro, position from integrating wheel travel along arcs.
 */
public class DifferentialDriveOdometry {
    private Pose2d pose;
    private Rotation2d gyroOffset;
    private Rotation2d previousAngle;
    private double prevLeftMeters;
    private double prevRightMeters;

    public DifferentialDriveOdometry(Rotation2d gyroAngle, double leftMeters, double rightMeters,
                                     Pose2d initialPose) {
        resetPosition(gyroAngle, leftMeters, rightMeters, initialPose);
    }

    public DifferentialDriveOdometry(Rotation2d gyroAngle, double leftMeters, double rightMeters) {
        this(gyroAngle, leftMeters, rightMeters, Pose2d.kZero);
    }

    /** Teleports the robot to {@code pose} without resetting encoders or the gyro. */
    public void resetPosition(Rotation2d gyroAngle, double leftMeters, double rightMeters, Pose2d pose) {
        this.pose = pose;
        this.previousAngle = pose.getRotation();
        this.gyroOffset = pose.getRotation().minus(gyroAngle);
        this.prevLeftMeters = leftMeters;
        this.prevRightMeters = rightMeters;
    }

    public Pose2d getPoseMeters() {
        return pose;
    }

    public Pose2d update(Rotation2d gyroAngle, double leftMeters, double rightMeters) {
        double dLeft = leftMeters - prevLeftMeters;
        double dRight = rightMeters - prevRightMeters;
        prevLeftMeters = leftMeters;
        prevRightMeters = rightMeters;

        double distance = (dLeft + dRight) / 2.0;
        Rotation2d angle = gyroAngle.plus(gyroOffset);
        double dTheta = angle.minus(previousAngle).getRadians();

        Pose2d newPose = pose.exp(new Twist2d(distance, 0.0, dTheta));
        previousAngle = angle;
        pose = new Pose2d(newPose.getTranslation(), angle); // trust the gyro for heading
        return pose;
    }
}
