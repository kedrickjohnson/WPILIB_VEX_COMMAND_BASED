package com.robojacks.robot.commands;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.controller.PIDController;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.robot.subsystems.Drivetrain;

import static com.robojacks.robot.Constants.DriveConstants.*;

/**
 * Drives to a FIELD point (meters, center origin) using odometry.
 * Steers toward the point while driving; slows forward speed when pointed the wrong way.
 * Set {@code reversed} to back into the point.
 */
public class DriveToPoint extends Command {
    private final Drivetrain drive;
    private final Translation2d target;
    private final boolean reversed;
    private final PIDController distancePid = new PIDController(DRIVE_KP, 0.0, DRIVE_KD);
    private final PIDController headingPid = new PIDController(TURN_KP, 0.0, TURN_KD);
    private double distance;

    public DriveToPoint(Drivetrain drive, Translation2d target, boolean reversed) {
        this.drive = drive;
        this.target = target;
        this.reversed = reversed;
        headingPid.enableContinuousInput(-Math.PI, Math.PI);
        addRequirements(drive);
        setName("DriveTo" + target);
    }

    public DriveToPoint(Drivetrain drive, Translation2d target) {
        this(drive, target, false);
    }

    @Override
    public void initialize() {
        distancePid.reset();
        headingPid.reset();
    }

    @Override
    public void execute() {
        Pose2d pose = drive.getPose();
        Translation2d toTarget = target.minus(pose.getTranslation());
        distance = toTarget.getNorm();

        Rotation2d facing = reversed ? pose.getRotation().plus(Rotation2d.k180deg) : pose.getRotation();
        double angleError = toTarget.getAngle().minus(facing).getRadians();

        // Forward effort: drive the distance to zero, scaled by how well we're aimed.
        double forward = -distancePid.calculate(distance, 0.0) * Math.max(0.0, Math.cos(angleError));
        if (reversed) {
            forward = -forward;
        }

        // Stop steering in the last few cm, otherwise the robot spins near the point.
        double turn = distance > 0.08 ? headingPid.calculate(-angleError, 0.0) : 0.0;

        forward = MathUtil.clamp(forward, -AUTO_MAX_VOLTS, AUTO_MAX_VOLTS);
        turn = MathUtil.clamp(turn, -AUTO_MAX_VOLTS, AUTO_MAX_VOLTS);
        drive.tankDriveVolts(forward - turn, forward + turn);
    }

    @Override
    public boolean isFinished() {
        return distance < DRIVE_TOLERANCE_M;
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
    }
}
