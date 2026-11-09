package com.robojacks.lib.drive;

import com.robojacks.lib.command.Subsystem;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;

/**
 * What the library's drive commands (TurnToHeading, DriveToPoint, DriveDistance and the
 * tuning tests) need from your drivetrain. Implement it on your Drivetrain subsystem.
 */
public interface TankDriveSubsystem extends Subsystem {
    /** Raw side voltages, -12..12. */
    void tankDriveVolts(double leftVolts, double rightVolts);

    /** Field pose from odometry (meters, CCW-positive heading). */
    Pose2d getPose();

    /** Tells odometry where the robot is (e.g. its starting pose in auto). */
    void resetOdometry(Pose2d pose);

    /** Average of left and right wheel travel, meters. */
    double getAverageEncoderDistance();

    /** Left wheel speed, m/s. */
    double getLeftEncoderRate();

    /** Right wheel speed, m/s. */
    double getRightEncoderRate();

    DriveGains getGains();

    /** Distance between the left and right wheels, meters (used by path following). */
    double getTrackWidthMeters();

    default void stop() {
        tankDriveVolts(0.0, 0.0);
    }

    /**
     * Output for auto commands: forward and CCW turn in volts, each capped at maxAutoVolts,
     * with kS added so small corrections still overcome friction.
     */
    default void autoDriveVolts(double forwardVolts, double turnVolts) {
        DriveGains g = getGains();
        forwardVolts = MathUtil.clamp(forwardVolts, -g.maxAutoVolts, g.maxAutoVolts);
        turnVolts = MathUtil.clamp(turnVolts, -g.maxAutoVolts, g.maxAutoVolts);
        tankDriveVolts(g.addStatic(forwardVolts - turnVolts), g.addStatic(forwardVolts + turnVolts));
    }
}
