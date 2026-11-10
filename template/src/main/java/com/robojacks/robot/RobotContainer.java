package com.robojacks.robot;

import com.robojacks.lib.auto.AutoSelector;
import com.robojacks.lib.auto.NamedCommands;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.command.RobotModeTriggers;
import com.robojacks.lib.drive.TuningCommands;
import com.robojacks.lib.input.VexController;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.robot.autos.Autos;
import com.robojacks.robot.autos.PlannerAutos;
import com.robojacks.robot.commands.TankDrive;
import com.robojacks.robot.subsystems.DriveSubsystem;
import com.robojacks.robot.subsystems.Intake;
import com.robojacks.robot.subsystems.Vision;

import static com.robojacks.robot.Constants.OIConstants.kDriveDeadband;

/**
 * Where the robot is set up: subsystems, controller bindings and the auto.
 *
 * <pre>
 * Left stick  up/down   left side
 * Right stick up/down   right side
 * L2 (hold)             half speed
 * R1 / R2 (hold)        intake / outtake
 * D-pad                 turn to field heading (up 0, left 90, down 180, right -90)
 * D-pad left/right      (while disabled) choose the auto; also tap it on the brain screen
 * </pre>
 */
public class RobotContainer {
    // The robot's subsystems
    private final DriveSubsystem m_robotDrive = new DriveSubsystem();
    private final Intake m_intake = new Intake();
    // Vision: the GPS sends pose estimates to the drive, like a PhotonVision camera. Only if there is one.
    private final Vision m_vision = Constants.VisionConstants.kGpsPort > 0
            ? new Vision(m_robotDrive::addVisionMeasurement) : null;

    // The driver's controller
    private final VexController m_driverController = new VexController(0);

    // Auto chooser on the brain's screen (VEX's SendableChooser)
    private final AutoSelector m_autoSelector = new AutoSelector();

    public RobotContainer() {
        // Commands autos can use by name (path planner markers). Register before building autos.
        NamedCommands.register("intake", m_intake::intakeCommand);
        NamedCommands.register("outtake", m_intake::outtakeCommand);

        // Default command: tank drive. VEX sticks are already positive when pushed up.
        m_robotDrive.setDefaultCommand(new TankDrive(
                m_robotDrive,
                () -> MathUtil.applyDeadband(m_driverController.getLeftY(), kDriveDeadband),
                () -> MathUtil.applyDeadband(m_driverController.getRightY(), kDriveDeadband)));

        // Autos to choose from. Build them after registering named commands.
        m_autoSelector.setDefaultOption("Example auto", Autos.exampleAuto(m_robotDrive, m_intake));
        m_autoSelector.addOption("Planner auto", PlannerAutos.exampleAuto(m_robotDrive));
        m_autoSelector.addOption("Do nothing", Autos.doNothing());
        m_autoSelector.addControllerBindings(m_driverController);

        if (Constants.kTuningMode) {
            configureTuningBindings();
        } else {
            configureButtonBindings();
        }
    }

    private void configureButtonBindings() {
        // Drive at half speed while L2 is held
        m_driverController.l2().whileTrue(Commands.startEnd(
                () -> m_robotDrive.setMaxOutput(0.5), () -> m_robotDrive.setMaxOutput(1.0)));

        m_driverController.r1().whileTrue(m_intake.intakeCommand());
        m_driverController.r2().whileTrue(m_intake.outtakeCommand());

        // Snap to field headings (handy for testing odometry and the turn PID)
        m_driverController.up().onTrue(m_robotDrive.turnToAngleCommand(0));
        m_driverController.left().onTrue(m_robotDrive.turnToAngleCommand(90));
        m_driverController.down().onTrue(m_robotDrive.turnToAngleCommand(180));
        m_driverController.right().onTrue(m_robotDrive.turnToAngleCommand(-90));

        // Buzz the driver when driver control starts
        RobotModeTriggers.teleop().onTrue(m_driverController.rumbleCommand("-"));
    }

    /** Press a button to start a test. L1 aborts it and hands control back to the sticks. */
    private void configureTuningBindings() {
        m_driverController.a().onTrue(TuningCommands.characterize(m_robotDrive));
        m_driverController.b().onTrue(TuningCommands.autoTuneTurn(m_robotDrive));
        m_driverController.x().onTrue(TuningCommands.driveDistanceTest(m_robotDrive, 1.0));
        m_driverController.y().onTrue(TuningCommands.turnTest(m_robotDrive));
        m_driverController.l1().onTrue(m_robotDrive.stopCommand());
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
}
