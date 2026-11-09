package com.robojacks.robot;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.drive.TuningCommands;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.input.VexController;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.robot.autos.Autos;
import com.robojacks.robot.subsystems.Drivetrain;
import com.robojacks.robot.subsystems.Intake;

import static com.robojacks.robot.Constants.OIConstants.kDriveDeadband;

/** Creates subsystems, binds controls, picks the auto. Same role as in FRC command-based. */
public class RobotContainer {
    private final Drivetrain drivetrain = new Drivetrain();
    private final Intake intake = new Intake();

    private final VexController driver = new VexController(HAL.get().primaryController());

    public RobotContainer() {
        // Arcade drive: left stick forward/back, right stick turn.
        // VEX stick up is already positive. Right stick right is +, but CCW (left) is +, so negate.
        drivetrain.setDefaultCommand(drivetrain.arcadeDriveCommand(
                () -> MathUtil.applyDeadband(driver.getLeftY(), kDriveDeadband),
                () -> -MathUtil.applyDeadband(driver.getRightX(), kDriveDeadband)));

        if (Constants.kTuningMode) {
            configureTuningBindings();
        } else {
            configureBindings();
        }
    }

    private void configureBindings() {
        driver.r1().whileTrue(intake.intakeCommand());
        driver.r2().whileTrue(intake.outtakeCommand());
        driver.l2().whileTrue(drivetrain.slowModeCommand());

        // Snap to field headings (handy for testing odometry + turn PID).
        driver.up().onTrue(drivetrain.turnToAngleCommand(0));
        driver.left().onTrue(drivetrain.turnToAngleCommand(90));
        driver.down().onTrue(drivetrain.turnToAngleCommand(180));
        driver.right().onTrue(drivetrain.turnToAngleCommand(-90));
    }

    /** Press a button to start a test. L1 aborts it and hands control back to the sticks. */
    private void configureTuningBindings() {
        driver.a().onTrue(TuningCommands.characterize(drivetrain));
        driver.b().onTrue(TuningCommands.autoTuneTurn(drivetrain));
        driver.x().onTrue(TuningCommands.driveDistanceTest(drivetrain, 1.0));
        driver.y().onTrue(TuningCommands.turnTest(drivetrain));
        driver.l1().onTrue(drivetrain.stopCommand());
    }

    public Command getAutonomousCommand() {
        return Autos.exampleAuto(drivetrain, intake);
    }

    public Drivetrain getDrivetrain() {
        return drivetrain;
    }
}
