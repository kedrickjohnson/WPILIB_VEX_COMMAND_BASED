package com.robojacks.docs;

import com.robojacks.lib.auto.AutoBuilder;
import com.robojacks.lib.auto.AutoSelector;
import com.robojacks.lib.auto.NamedCommands;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.RobotModeTriggers;
import com.robojacks.lib.command.Trigger;
import com.robojacks.lib.input.VexController;
import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.robot.RobotController;

/** A RobotContainer showing controllers, bindings, triggers and the auto selector. */
public class ExampleRobotContainer {
    private final ExampleTrackingDrive m_robotDrive = new ExampleTrackingDrive();
    private final ExampleIntake m_intake = new ExampleIntake();
    private final ExampleArm m_arm = new ExampleArm();
    private final ExampleClamp m_clamp = new ExampleClamp();

    // [controllers]
    private final VexController m_driverController = new VexController(0);   // primary
    private final VexController m_operatorController = new VexController(1); // partner
    // [/controllers]

    // [auto-selector-field]
    private final AutoSelector m_autoSelector = new AutoSelector();
    // [/auto-selector-field]

    public ExampleRobotContainer() {
        // [named-commands]
        NamedCommands.register("intake", m_intake::intakeCommand);
        NamedCommands.register("clamp", m_clamp::clampCommand);
        NamedCommands.register("armUp", () -> m_arm.goToAngleCommand(Units.degreesToRadians(80)));
        // [/named-commands]

        configureBindings();
        configureAutos();
    }

    private void configureBindings() {
        // [bindings]
        // Hold R1 to intake, tap R2 to eject
        m_driverController.r1().whileTrue(m_intake.intakeCommand());
        m_driverController.r2().onTrue(m_intake.ejectCommand());

        // Each press of A toggles the clamp
        m_driverController.a().onTrue(m_clamp.toggleCommand());

        // Operator: D-pad sets the arm height; the arm holds there until the next press
        m_operatorController.up().onTrue(m_arm.goToAngleCommand(Units.degreesToRadians(80)));
        m_operatorController.down().onTrue(m_arm.goToAngleCommand(Units.degreesToRadians(-30)));
        // [/bindings]

        // [triggers]
        // Rumble the driver's controller when a piece arrives
        m_intake.hasPiece.onTrue(m_driverController.rumbleCommand("."));

        // L1 + L2 together: a "two-key" action that's hard to press by accident
        m_driverController.l1().and(m_driverController.l2())
                .onTrue(m_clamp.releaseCommand());

        // Operator's right stick pushed up or down past 20%: move the arm by hand
        m_operatorController.axisMagnitudeGreaterThan(VexController.Axis.kRightY, 0.2)
                .whileTrue(Commands.run(() -> { /* manual arm control */ }, m_arm));

        // Long buzz when driver control starts, so the driver knows the robot is live
        RobotModeTriggers.teleop().onTrue(m_driverController.rumbleCommand("-"));

        // Clamp automatically when a goal hits the switch, but only during driver control
        new Trigger(m_clamp::isGoalPresent).and(RobotModeTriggers.teleop())
                .onTrue(m_clamp.clampCommand());
        // [/triggers]
    }

    private void configureAutos() {
        // [auto-selector]
        m_autoSelector.setDefaultOption("Do nothing", Commands.none());
        m_autoSelector.addOption("Left: grab goal", leftAuto());
        m_autoSelector.addOption("Right: two pieces", rightAuto());
        m_autoSelector.addOption("Skills", skillsAuto());
        // D-pad left/right also cycles the choice while disabled, shown on the controller screen
        m_autoSelector.addControllerBindings(m_driverController);
        // [/auto-selector]
    }

    // [auto]
    /** Drive to a goal, clamp it, and back up, using field coordinates (meters, from center). */
    private Command leftAuto() {
        return Commands.sequence(
                AutoBuilder.resetPose(m_robotDrive, -1.5, 0.6, 0),
                AutoBuilder.driveTo(m_robotDrive, -0.9, 0.6),
                m_clamp.clampCommand(),
                AutoBuilder.turnTo(m_robotDrive, 90),
                Commands.parallel(
                        AutoBuilder.driveTo(m_robotDrive, -0.9, 1.2),
                        m_intake.intakeCommand().withTimeout(2.0)),
                AutoBuilder.backTo(m_robotDrive, -0.9, 0.9)
        ).withName("Left");
    }
    // [/auto]

    private Command rightAuto() {
        return Commands.sequence(
                AutoBuilder.resetPose(m_robotDrive, 1.5, 0.6, 180),
                AutoBuilder.driveTo(m_robotDrive, 0.9, 0.6),
                m_intake.intakeCommand().withTimeout(1.5)
        ).withName("Right");
    }

    private Command skillsAuto() {
        return Commands.none().withName("Skills");
    }

    // [controller-screen]
    /** Call from Robot.robotPeriodic(): shows useful numbers on the driver's controller. */
    public void updateControllerScreen() {
        m_driverController.setText(1, 0, "Batt " + Math.round(RobotController.getBatteryPercent()) + "%");
        m_driverController.setText(2, 0, m_intake.hasPiece() ? "PIECE" : "     ");
    }
    // [/controller-screen]

    public Command getAutonomousCommand() {
        // [auto-selector-get]
        return m_autoSelector.getSelected();
        // [/auto-selector-get]
    }
}
