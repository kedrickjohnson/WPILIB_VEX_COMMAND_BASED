package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.controller.PIDController;
import com.robojacks.lib.math.Rotation2d;

/** Drives straight a set distance (negative = backward) while holding the starting heading. */
public class DriveDistance extends Command {
    private final TankDriveSubsystem drive;
    private final double meters;
    private final PIDController distancePid;
    private final PIDController headingPid;
    private double startDistance;
    private Rotation2d holdHeading;

    public DriveDistance(TankDriveSubsystem drive, double meters) {
        this.drive = drive;
        this.meters = meters;
        DriveGains g = drive.getGains();
        distancePid = new PIDController(g.driveP, 0.0, g.driveD);
        distancePid.setTolerance(g.driveToleranceMeters, 0.05); // within tolerance and nearly stopped
        headingPid = new PIDController(g.turnP, 0.0, g.turnD);
        headingPid.enableContinuousInput(-Math.PI, Math.PI);
        addRequirements(drive);
        setName("DriveDistance(" + meters + ")");
    }

    /** Distance traveled since this command started. */
    public double traveledMeters() {
        return drive.getAverageEncoderDistance() - startDistance;
    }

    @Override
    public void initialize() {
        distancePid.reset();
        headingPid.reset();
        startDistance = drive.getAverageEncoderDistance();
        holdHeading = drive.getPose().getRotation();
        distancePid.setSetpoint(meters);
        headingPid.setSetpoint(holdHeading.getRadians());
    }

    @Override
    public void execute() {
        double forward = distancePid.calculate(traveledMeters());
        double turn = headingPid.calculate(drive.getPose().getRotation().getRadians());
        if (distancePid.atSetpoint()) {
            forward = 0.0;
            turn = 0.0;
        }
        drive.autoDriveVolts(forward, turn);
    }

    @Override
    public boolean isFinished() {
        return distancePid.atSetpoint();
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
    }
}
