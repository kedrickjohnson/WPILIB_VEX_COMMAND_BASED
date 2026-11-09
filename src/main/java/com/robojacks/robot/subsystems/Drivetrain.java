package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.drive.DriveDistance;
import com.robojacks.lib.drive.DriveToPoint;
import com.robojacks.lib.drive.DrivetrainConfig;
import com.robojacks.lib.drive.TankDrivetrain;
import com.robojacks.lib.drive.TurnToHeading;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.robot.Constants;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * The robot's tank drive, written like an FRC drive subsystem.
 *
 * <p>Motors, IMU and odometry are set up by {@link TankDrivetrain} from {@code Constants.DRIVE},
 * so this file is free for your commands. Methods you get from the base class:
 * <ul>
 *   <li>{@code tankDriveVolts(l, r)}, {@code arcadeDrive(fwd, turn)}, {@code stop()}</li>
 *   <li>{@code getPose()}, {@code resetPose(pose)}, {@code getHeading()}</li>
 *   <li>{@code getLeftDistanceMeters()}, {@code getRightDistanceMeters()}, velocities</li>
 *   <li>{@code autoDriveVolts(fwd, turn)}: capped, friction-compensated output for auto commands</li>
 * </ul>
 */
public class Drivetrain extends TankDrivetrain {

    public Drivetrain() {
        this(Constants.DRIVE);
    }

    /** Lets the simulator or a test build a drivetrain with a different config. */
    public Drivetrain(DrivetrainConfig config) {
        super(config);
    }

    @Override
    public void periodic() {
        super.periodic(); // updates odometry; keep this line
        // Add your own per-loop code or telemetry here.
    }

    // =====================================================================
    // Teleop commands
    // =====================================================================

    /**
     * Arcade drive: one stick forward/back, the other turns. Used as the default command.
     *
     * @param forward -1..1, positive = forward
     * @param turn    -1..1, positive = counter-clockwise (left)
     */
    public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier turn) {
        return run(() -> arcadeDrive(forward.getAsDouble(), turn.getAsDouble()))
                .withName("ArcadeDrive");
    }

    /** Tank drive: left stick drives the left side, right stick the right side. -1..1 each. */
    public Command tankDriveCommand(DoubleSupplier left, DoubleSupplier right) {
        return run(() -> tankDriveVolts(12.0 * left.getAsDouble(), 12.0 * right.getAsDouble()))
                .withName("TankDrive");
    }

    public Command stopCommand() {
        return runOnce(this::stop).withName("StopDrive");
    }

    // =====================================================================
    // Autonomous commands (all positions are field-centered meters)
    // =====================================================================

    /** Tells odometry where the robot is, e.g. its starting spot at the beginning of auto. */
    public Command resetPoseCommand(Pose2d pose) {
        return runOnce(() -> resetPose(pose)).withName("ResetPose");
    }

    /** Drives straight (negative = backward) while holding the current heading. */
    public Command driveDistanceCommand(double meters) {
        return new DriveDistance(this, meters);
    }

    /** Turns in place to a field heading: 0 = +X, 90 = +Y (counter-clockwise positive). */
    public Command turnToAngleCommand(double fieldDegrees) {
        return new TurnToHeading(this, Rotation2d.fromDegrees(fieldDegrees));
    }

    /** Drives forward to a point on the field, steering as it goes. */
    public Command driveToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), false);
    }

    /** Backs up into a point on the field. */
    public Command backToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), true);
    }

    /** Turns to face a point, then drives to it. Cleaner than driveToPoint for big angle changes. */
    public Command turnAndDriveToPointCommand(double xMeters, double yMeters) {
        Translation2d target = new Translation2d(xMeters, yMeters);
        return defer(() -> {
            double angle = target.minus(getPose().getTranslation()).getAngle().getDegrees();
            return turnToAngleCommand(angle).andThen(driveToPointCommand(xMeters, yMeters));
        }).withName("TurnAndDriveTo" + target);
    }

    /** Builds the command when it starts, so it can use the robot's pose at that moment. */
    private Command defer(Supplier<Command> supplier) {
        return Commands.defer(supplier, this);
    }
}
