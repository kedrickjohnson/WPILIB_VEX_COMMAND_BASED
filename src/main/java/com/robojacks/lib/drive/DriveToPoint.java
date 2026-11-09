package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.controller.PIDController;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;

/**
 * Drives to a FIELD point (meters, center origin) using odometry.
 * Steers toward the point while driving; slows forward speed when pointed the wrong way.
 * Set {@code reversed} to back into the point.
 */
public class DriveToPoint extends Command {
    private final TankDrivetrain drive;
    private final Translation2d target;
    private final boolean reversed;
    private final PIDController distancePid;
    private final PIDController headingPid;
    private double distance = Double.POSITIVE_INFINITY;

    public DriveToPoint(TankDrivetrain drive, Translation2d target, boolean reversed) {
        this.drive = drive;
        this.target = target;
        this.reversed = reversed;
        DrivetrainConfig c = drive.getConfig();
        distancePid = new PIDController(c.driveKP, 0.0, c.driveKD);
        headingPid = new PIDController(c.turnKP, 0.0, c.turnKD);
        headingPid.enableContinuousInput(-Math.PI, Math.PI);
        addRequirements(drive);
        setName((reversed ? "BackTo" : "DriveTo") + target);
    }

    @Override
    public void initialize() {
        distancePid.reset();
        headingPid.reset();
        distance = Double.POSITIVE_INFINITY;
    }

    @Override
    public void execute() {
        Pose2d pose = drive.getPose();
        Translation2d toTarget = target.minus(pose.getTranslation());
        distance = toTarget.getNorm();

        Rotation2d facing = reversed ? pose.getRotation().plus(Rotation2d.k180deg) : pose.getRotation();
        double angleError = toTarget.getAngle().minus(facing).getRadians();

        // Forward effort: drive distance to zero, scaled by how well we're aimed.
        double forward = -distancePid.calculate(distance, 0.0) * Math.max(0.0, Math.cos(angleError));
        if (reversed) {
            forward = -forward;
        }

        // Stop steering in the last few cm, otherwise the robot spins near the point.
        double turn = distance > 0.08 ? headingPid.calculate(-angleError, 0.0) : 0.0;

        if (distance < drive.getConfig().driveToleranceM) {
            forward = 0.0;
            turn = 0.0;
        }
        drive.autoDriveVolts(forward, turn);
    }

    @Override
    public boolean isFinished() {
        return distance < drive.getConfig().driveToleranceM;
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
    }
}
