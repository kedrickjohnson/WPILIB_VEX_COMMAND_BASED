package com.robojacks.clawbot.autos;

import com.robojacks.clawbot.subsystems.Arm;
import com.robojacks.clawbot.subsystems.Claw;
import com.robojacks.clawbot.subsystems.DriveSubsystem;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.math.Pose2d;

/**
 * Clawbot autonomous routines. Odometry starts at (0, 0) facing 0 degrees, so positions are
 * "meters from where the robot started" (+X forward, +Y left).
 */
public final class Autos {
    private Autos() {}

    /**
     * Start with an object in front of the open claw. Grab it, carry it forward and to the
     * left, set it down, and back away.
     */
    public static Command pickUpAndMove(DriveSubsystem drive, Arm arm, Claw claw) {
        return Commands.sequence(
                drive.resetOdometryCommand(Pose2d.kZero),
                claw.closeCommand(),
                arm.carryCommand(),
                drive.driveDistanceCommand(0.6),
                drive.turnToAngleCommand(90),
                drive.driveDistanceCommand(0.3),
                arm.downCommand(),
                claw.openCommand(),
                drive.driveDistanceCommand(-0.25),
                Commands.print("Auto done")
        ).withName("PickUpAndMove");
    }

    /** Drive a 0.5 m square using odometry. Good for checking turns and tracking. */
    public static Command square(DriveSubsystem drive) {
        return Commands.sequence(
                drive.resetOdometryCommand(Pose2d.kZero),
                drive.driveToPointCommand(0.5, 0.0),
                drive.turnToAngleCommand(90),
                drive.driveToPointCommand(0.5, 0.5),
                drive.turnToAngleCommand(180),
                drive.driveToPointCommand(0.0, 0.5),
                drive.turnToAngleCommand(-90),
                drive.driveToPointCommand(0.0, 0.0),
                drive.turnToAngleCommand(0)
        ).withName("Square");
    }
}
