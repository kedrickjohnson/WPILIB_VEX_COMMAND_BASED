package com.robojacks.robot.commands;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.controller.PIDController;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.robot.subsystems.Drivetrain;

import static com.robojacks.robot.Constants.DriveConstants.*;

/** Turns in place to a FIELD heading (CCW-positive, 0 = +X). */
public class TurnToHeading extends Command {
    private final Drivetrain drive;
    private final Rotation2d target;
    private final PIDController pid = new PIDController(TURN_KP, 0.0, TURN_KD);

    public TurnToHeading(Drivetrain drive, Rotation2d target) {
        this.drive = drive;
        this.target = target;
        pid.enableContinuousInput(-Math.PI, Math.PI);
        pid.setTolerance(TURN_TOLERANCE_RAD, Math.toRadians(20.0)); // also nearly stopped
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
        double volts = pid.calculate(drive.getHeading().getRadians());
        volts = MathUtil.clamp(volts, -AUTO_MAX_VOLTS, AUTO_MAX_VOLTS);
        drive.tankDriveVolts(-volts, volts); // positive = CCW
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
