package com.robojacks.robot;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.input.VexController;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.robot.autos.Autos;
import com.robojacks.robot.commands.TurnToHeading;
import com.robojacks.robot.subsystems.Drivetrain;
import com.robojacks.robot.subsystems.Intake;

import static com.robojacks.robot.Constants.OIConstants.DEADBAND;

/** Creates subsystems, binds controls, picks the auto. Same role as in FRC command-based. */
public class RobotContainer {
    private final Drivetrain drivetrain = new Drivetrain();
    private final Intake intake = new Intake();

    private final VexController driver = new VexController(HAL.get().primaryController());

    public RobotContainer() {
        configureBindings();
    }

    private void configureBindings() {
        // Arcade drive: left stick forward/back, right stick turn.
        // VEX stick up is already positive. Right stick right is +, but CCW (left) is +, so negate.
        drivetrain.setDefaultCommand(drivetrain.arcadeDriveCommand(
                () -> MathUtil.applyDeadband(driver.getLeftY(), DEADBAND),
                () -> -MathUtil.applyDeadband(driver.getRightX(), DEADBAND)));

        driver.r1().whileTrue(intake.intakeCommand());
        driver.r2().whileTrue(intake.outtakeCommand());

        // Snap to field headings (handy for testing odometry + turn PID).
        driver.up().onTrue(new TurnToHeading(drivetrain, Rotation2d.fromDegrees(0)));
        driver.left().onTrue(new TurnToHeading(drivetrain, Rotation2d.fromDegrees(90)));
        driver.down().onTrue(new TurnToHeading(drivetrain, Rotation2d.fromDegrees(180)));
        driver.right().onTrue(new TurnToHeading(drivetrain, Rotation2d.fromDegrees(-90)));
    }

    public Command getAutonomousCommand() {
        return Autos.exampleAuto(drivetrain, intake);
    }

    public Drivetrain getDrivetrain() {
        return drivetrain;
    }
}
