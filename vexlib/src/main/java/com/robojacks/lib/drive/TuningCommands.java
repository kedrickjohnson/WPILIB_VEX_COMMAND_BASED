package com.robojacks.lib.drive;

import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.math.Rotation2d;

/**
 * Tests that dial in the drivetrain constants without writing code. Each prints results over
 * serial with a line to paste into DriveConstants. Bound to buttons when
 * Constants.kTuningMode is true.
 *
 * <p>Suggested order: characterize -> autoTuneTurn -> driveDistanceTest -> turnTest.
 */
public final class TuningCommands {
    private TuningCommands() {}

    /** Ramp test for kS / kV. */
    public static Command characterize(TankDriveSubsystem drive) {
        return new FeedforwardCharacterization(drive);
    }

    /** Relay auto-tune for the turn PID. */
    public static Command autoTuneTurn(TankDriveSubsystem drive) {
        return new TurnAutoTune(drive);
    }

    /**
     * Drives a set distance using the encoders, then prints what it measured.
     * Measure the real distance with a tape measure and correct the wheel diameter:
     * kWheelDiameterInches *= actualDistance / reportedDistance.
     */
    public static Command driveDistanceTest(TankDriveSubsystem drive, double meters) {
        DriveDistance d = new DriveDistance(drive, meters);
        return d.andThen(Commands.runOnce(() -> {
            double reportedIn = Units.metersToInches(d.traveledMeters());
            System.out.println("[DriveTest] robot thinks it drove " + round(reportedIn) + " in");
            System.out.println("[DriveTest] measure the real distance A (in), then multiply "
                    + "kWheelDiameterInches by A / " + round(reportedIn));
        }));
    }

    /**
     * Turns 90 degrees four times and reports the error at each stop.
     * Good for checking the turn PID after auto-tuning.
     */
    public static Command turnTest(TankDriveSubsystem drive) {
        Command[] steps = new Command[8];
        for (int i = 0; i < 4; i++) {
            double target = 90.0 * (i + 1);
            steps[2 * i] = new TurnToHeading(drive, Rotation2d.fromDegrees(target));
            steps[2 * i + 1] = Commands.runOnce(() -> {
                double err = Rotation2d.fromDegrees(target).minus(drive.getPose().getRotation()).getDegrees();
                System.out.println("[TurnTest] target " + Math.round(target) + " deg, error " + round(err) + " deg");
            });
        }
        return Commands.sequence(steps).withName("TurnTest");
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
