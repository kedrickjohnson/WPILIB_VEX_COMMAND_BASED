package com.robojacks.lib.auto;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.drive.DriveDistance;
import com.robojacks.lib.drive.DriveToPoint;
import com.robojacks.lib.drive.TankDriveSubsystem;
import com.robojacks.lib.drive.TurnToHeading;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.trajectory.FollowTrajectory;
import com.robojacks.lib.trajectory.Trajectory;

/**
 * Building blocks for autos, used by the path planner's exported code and handy by hand.
 * Positions are field meters (center origin), headings are degrees (0 = +X, counter-clockwise
 * positive). Works with any drive subsystem that implements {@link TankDriveSubsystem}.
 */
public final class AutoBuilder {
    private AutoBuilder() {}

    /** Tells odometry where the robot starts. Always the first step of an auto. */
    public static Command resetPose(TankDriveSubsystem drive, double x, double y, double headingDegrees) {
        Pose2d pose = new Pose2d(x, y, Rotation2d.fromDegrees(headingDegrees));
        return Commands.runOnce(() -> drive.resetOdometry(pose), drive).withName("ResetPose");
    }

    /** Sets odometry to where a trajectory starts. Use before following the first path of an auto. */
    public static Command resetPose(TankDriveSubsystem drive, Trajectory path) {
        Pose2d pose = path.getInitialPose();
        return Commands.runOnce(() -> drive.resetOdometry(pose), drive).withName("ResetPose");
    }

    /**
     * Follows a path. Marker commands (made with {@link #marker}) run alongside it and are
     * stopped when the path ends. Markers must not need the drivetrain or each other's subsystems.
     */
    public static Command followPath(TankDriveSubsystem drive, Trajectory path, Command... markers) {
        Command follow = new FollowTrajectory(drive, path);
        return markers.length == 0 ? follow : Commands.deadline(follow, markers);
    }

    /** Starts a named command {@code atSeconds} into a path; it runs until it finishes or the path ends. */
    public static Command marker(double atSeconds, String namedCommand) {
        return Commands.sequence(Commands.waitSeconds(atSeconds), NamedCommands.get(namedCommand))
                .withName("Marker:" + namedCommand);
    }

    /** Starts a named command {@code atSeconds} into a path and stops it after {@code forSeconds}. */
    public static Command marker(double atSeconds, double forSeconds, String namedCommand) {
        return Commands.sequence(Commands.waitSeconds(atSeconds),
                NamedCommands.get(namedCommand).withTimeout(forSeconds)).withName("Marker:" + namedCommand);
    }

    /** Turns in place to a field heading. */
    public static Command turnTo(TankDriveSubsystem drive, double headingDegrees) {
        return new TurnToHeading(drive, Rotation2d.fromDegrees(headingDegrees));
    }

    /**
     * Turns in place to face a point (or to put the robot's back toward it when
     * {@code reversed}). The angle is worked out when the command starts, from where the robot
     * really is, so small earlier errors don't add up.
     */
    public static Command turnToFace(TankDriveSubsystem drive, double x, double y, boolean reversed) {
        Translation2d target = new Translation2d(x, y);
        return Commands.defer(() -> {
            Rotation2d angle = target.minus(drive.getPose().getTranslation()).getAngle();
            if (reversed) {
                angle = angle.plus(Rotation2d.k180deg);
            }
            return new TurnToHeading(drive, angle);
        }, drive).withName("TurnToFace" + target);
    }

    /** Drives forward to a field point, steering as it goes. */
    public static Command driveTo(TankDriveSubsystem drive, double x, double y) {
        return new DriveToPoint(drive, new Translation2d(x, y), false);
    }

    /** Backs up to a field point. */
    public static Command backTo(TankDriveSubsystem drive, double x, double y) {
        return new DriveToPoint(drive, new Translation2d(x, y), true);
    }

    /** Straight line from wherever the robot is, holding heading (negative = backward). */
    public static Command driveDistance(TankDriveSubsystem drive, double meters) {
        return new DriveDistance(drive, meters);
    }

    /** Runs {@code drive} and the named commands together, finishing when the drive finishes. */
    public static Command withMarkers(Command drive, String... namedCommands) {
        if (namedCommands.length == 0) {
            return drive;
        }
        Command[] others = new Command[namedCommands.length];
        for (int i = 0; i < namedCommands.length; i++) {
            others[i] = NamedCommands.get(namedCommands[i]);
        }
        return Commands.deadline(drive, others);
    }
}
