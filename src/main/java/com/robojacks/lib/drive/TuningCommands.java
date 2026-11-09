package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import com.robojacks.lib.math.Rotation2d;

/**
 * Tests that dial in a DrivetrainConfig without writing code. Each prints results over
 * serial with a line to paste into Constants.java. Bound to buttons when
 * Constants.TUNING_MODE is true.
 *
 * <p>Suggested order: characterize -> autoTuneTurn -> driveDistanceTest -> turnTest.
 */
public final class TuningCommands {
    private TuningCommands() {}

    /** Ramp test for kS / kV. */
    public static Command characterize(TankDrivetrain drive) {
        return new FeedforwardCharacterization(drive);
    }

    /** Relay auto-tune for the turn PID. */
    public static Command autoTuneTurn(TankDrivetrain drive) {
        return new TurnAutoTune(drive);
    }

    /**
     * Drives a set distance using the encoders, then prints what it measured.
     * Measure the real distance with a tape measure and correct the wheel diameter:
     * newDiameter = currentDiameter * actualDistance / reportedDistance.
     */
    public static Command driveDistanceTest(TankDrivetrain drive, double meters) {
        DriveDistance d = new DriveDistance(drive, meters);
        double diameterIn = drive.getConfig().wheelDiameterM / 0.0254;
        return d.andThen(Commands.runOnce(() -> {
            double reported = d.traveledMeters();
            System.out.println("[DriveTest] robot thinks it drove " + round(reported) + " m ("
                    + round(reported / 0.0254) + " in)");
            System.out.println("[DriveTest] measure the real distance (inches) A, then set "
                    + ".wheelDiameterInches(" + round(diameterIn) + " * A / " + round(reported / 0.0254) + ")");
        }));
    }

    /**
     * Turns 90 degrees four times and reports the error at each stop.
     * Good for checking the turn PID after auto-tuning.
     */
    public static Command turnTest(TankDrivetrain drive) {
        Command[] steps = new Command[8];
        for (int i = 0; i < 4; i++) {
            double target = 90.0 * (i + 1);
            steps[2 * i] = new TurnToHeading(drive, Rotation2d.fromDegrees(target));
            steps[2 * i + 1] = Commands.runOnce(() -> {
                double err = Rotation2d.fromDegrees(target).minus(drive.getHeading()).getDegrees();
                System.out.println("[TurnTest] target " + Math.round(target) + " deg, error " + round(err) + " deg");
            });
        }
        return Commands.sequence(steps).withName("TurnTest");
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
