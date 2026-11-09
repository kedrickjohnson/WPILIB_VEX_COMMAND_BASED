package com.robojacks.robot.autos;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.robot.FieldConstants.StartPoses;
import com.robojacks.robot.commands.DriveToPoint;
import com.robojacks.robot.commands.TurnToHeading;
import com.robojacks.robot.subsystems.Drivetrain;
import com.robojacks.robot.subsystems.Intake;

/** Autonomous routines, built from commands. All coordinates are field-centered meters. */
public final class Autos {
    private Autos() {}

    /** Example: drive out, run the intake while driving to a point, turn around, come back. */
    public static Command exampleAuto(Drivetrain drive, Intake intake) {
        return Commands.sequence(
                drive.resetPoseCommand(StartPoses.LEFT_START),
                new DriveToPoint(drive, new Translation2d(-0.9, 0.6)),
                new DriveToPoint(drive, new Translation2d(-0.6, 0.0))
                        .deadlineFor(intake.intakeCommand()),
                new TurnToHeading(drive, Rotation2d.fromDegrees(180)),
                new DriveToPoint(drive, new Translation2d(-1.2, 0.0)),
                Commands.print("Auto done")
        ).withName("ExampleAuto");
    }

    public static Command doNothing() {
        return Commands.none().withName("DoNothing");
    }
}
