package com.robojacks.clawbot;

import com.robojacks.clawbot.autos.Autos;
import com.robojacks.clawbot.commands.ArcadeDrive;
import com.robojacks.clawbot.subsystems.Arm;
import com.robojacks.clawbot.subsystems.Claw;
import com.robojacks.clawbot.subsystems.DriveSubsystem;
import com.robojacks.lib.auto.AutoSelector;
import com.robojacks.lib.auto.NamedCommands;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.drive.TuningCommands;
import com.robojacks.lib.input.VexController;
import com.robojacks.lib.math.MathUtil;

import static com.robojacks.clawbot.Constants.OIConstants.kDriveDeadband;

/**
 * Subsystems, controls and autos.
 *
 * <pre>
 * Left stick  up/down     drive forward/back
 * Right stick left/right  turn
 * L1 / L2 (hold)          arm up / down
 * R1 / R2                 open / close claw
 * D-pad up / right / down arm high / carry / down
 * A                       grab: close claw, lift to carry
 * B                       place: lower arm, open claw
 * Y (hold)                slow drive
 * </pre>
 *
 * <p>Choose the auto on the brain's screen before the match.
 */
public class RobotContainer {
    // The robot's subsystems
    private final DriveSubsystem m_robotDrive = new DriveSubsystem();
    private final Arm m_arm = new Arm();
    private final Claw m_claw = new Claw();

    // The driver's controller
    private final VexController m_driverController = new VexController(0);

    // Auto chooser on the brain's screen
    private final AutoSelector m_autoSelector = new AutoSelector("Clawbot auto");

    public RobotContainer() {
        // Commands autos can use by name (path planner markers). Register before building autos.
        NamedCommands.register("openClaw", m_claw::openCommand);
        NamedCommands.register("closeClaw", m_claw::closeCommand);
        NamedCommands.register("armDown", m_arm::downCommand);
        NamedCommands.register("armCarry", m_arm::carryCommand);
        NamedCommands.register("armHigh", m_arm::highCommand);
        NamedCommands.register("grab", this::grabCommand);
        NamedCommands.register("place", this::placeCommand);

        // VEX stick up is already positive. Right stick right is +, but CCW (left) turn is +, so negate.
        m_robotDrive.setDefaultCommand(new ArcadeDrive(
                m_robotDrive,
                () -> MathUtil.applyDeadband(m_driverController.getLeftY(), kDriveDeadband),
                () -> -MathUtil.applyDeadband(m_driverController.getRightX(), kDriveDeadband)));

        m_autoSelector.setDefaultOption("Pick up and move", Autos.pickUpAndMove(m_robotDrive, m_arm, m_claw));
        m_autoSelector.addOption("Drive a square", Autos.square(m_robotDrive));
        m_autoSelector.addOption("Do nothing", Commands.none());

        if (Constants.kTuningMode) {
            configureTuningBindings();
        } else {
            configureButtonBindings();
        }
    }

    private void configureButtonBindings() {
        m_driverController.l1().whileTrue(m_arm.raiseCommand());
        m_driverController.l2().whileTrue(m_arm.lowerCommand());

        m_driverController.r1().onTrue(m_claw.openCommand());
        m_driverController.r2().onTrue(m_claw.closeCommand());

        m_driverController.up().onTrue(m_arm.highCommand());
        m_driverController.right().onTrue(m_arm.carryCommand());
        m_driverController.down().onTrue(m_arm.downCommand());

        m_driverController.a().onTrue(grabCommand());
        m_driverController.b().onTrue(placeCommand());

        // Drive at half speed while Y is held
        m_driverController.y().whileTrue(Commands.startEnd(
                () -> m_robotDrive.setMaxOutput(0.5), () -> m_robotDrive.setMaxOutput(1.0)));
    }

    private void configureTuningBindings() {
        m_driverController.a().onTrue(TuningCommands.characterize(m_robotDrive));
        m_driverController.b().onTrue(TuningCommands.autoTuneTurn(m_robotDrive));
        m_driverController.x().onTrue(TuningCommands.driveDistanceTest(m_robotDrive, 1.0));
        m_driverController.y().onTrue(TuningCommands.turnTest(m_robotDrive));
        m_driverController.l1().onTrue(m_robotDrive.stopCommand());
    }

    /** Close the claw on an object, then lift it to carry height. */
    public Command grabCommand() {
        return Commands.sequence(m_claw.closeCommand(), m_arm.carryCommand()).withName("Grab");
    }

    /** Lower the arm, then let go. */
    public Command placeCommand() {
        return Commands.sequence(m_arm.downCommand(), m_claw.openCommand()).withName("Place");
    }

    public Command getAutonomousCommand() {
        return m_autoSelector.getSelected();
    }

    public AutoSelector getAutoSelector() {
        return m_autoSelector;
    }

    public DriveSubsystem getDriveSubsystem() {
        return m_robotDrive;
    }

    public Arm getArm() {
        return m_arm;
    }

    public Claw getClaw() {
        return m_claw;
    }
}
