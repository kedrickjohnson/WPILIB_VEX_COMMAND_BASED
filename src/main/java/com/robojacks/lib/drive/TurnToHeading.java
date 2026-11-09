package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.controller.PIDController;
import com.robojacks.lib.math.Rotation2d;

/** Turns in place to a FIELD heading (CCW-positive, 0 = +X). Gains come from DrivetrainConfig. */
public class TurnToHeading extends Command {
    private final TankDrivetrain drive;
    private final Rotation2d target;
    private final PIDController pid;

    public TurnToHeading(TankDrivetrain drive, Rotation2d target) {
        this.drive = drive;
        this.target = target;
        DrivetrainConfig c = drive.getConfig();
        pid = new PIDController(c.turnKP, 0.0, c.turnKD);
        pid.enableContinuousInput(-Math.PI, Math.PI);
        pid.setTolerance(c.turnToleranceRad, Math.toRadians(20.0)); // also nearly stopped
        addRequirements(drive);
        setName("TurnTo(" + Math.round(target.getDegrees()) + ")");
    }

    @Override
    public void initialize() {
        pid.reset();
        pid.setSetpoint(target.getRadians());
    }

    @Override
    public void execute() {
        double turnVolts = pid.calculate(drive.getHeading().getRadians());
        if (Math.abs(pid.getError()) < drive.getConfig().turnToleranceRad) {
            turnVolts = 0.0; // don't let kS buzz the motors once we're there
        }
        drive.autoDriveVolts(0.0, turnVolts);
    }

    @Override
    public boolean isFinished() {
        return pid.atSetpoint();
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
    }
}
