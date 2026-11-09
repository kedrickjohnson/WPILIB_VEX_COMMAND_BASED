package com.robojacks.robot;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.robot.Constants.DriveConstants;
import com.robojacks.lib.drive.FeedforwardCharacterization;
import com.robojacks.lib.drive.TuningCommands;
import com.robojacks.lib.drive.TurnAutoTune;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.sim.SimHardware;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.telemetry.Telemetry;
import com.robojacks.robot.subsystems.DriveSubsystem;

/**
 * Runs the template robot code against SimHardware on your computer.
 *
 * <ul>
 *   <li>{@code ./gradlew :template:simulate} — match: 3 s disabled (IMU calibrates), 15 s auto,
 *       then teleop: D-pad LEFT (turn to 90), then both sticks forward (tank drive).</li>
 *   <li>{@code ./gradlew :template:simulate --args=tune} — runs the tuning tests and shows how the
 *       auto-tuned gains perform.</li>
 * </ul>
 */
public final class SimMain {
    private SimMain() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("tune")) {
            runTuning();
        } else {
            runMatch();
        }
    }

    // ---------------------------------------------------------------------------------

    private static SimHardware newHardware() {
        SimHardware hw = new SimHardware();
        HAL.set(hw);
        return hw;
    }

    // ---------------------------------------------------------------------------------

    private static void runMatch() {
        SimHardware hw = newHardware();
        Telemetry.setEnabled(false); // keep sim output readable; SIM lines show the pose

        Robot robot = new Robot();
        robot.robotInit();
        simulatePhysics(hw);

        double period = robot.getPeriod();
        double t = 0.0;
        double lastPrint = -1.0;
        while (t < 23.0) {
            hw.competition.enabled = t >= 3.0;
            hw.competition.autonomous = t < 18.0;
            hw.primary.buttons[GamepadIO.BUTTON_LEFT] = t >= 19.0 && t < 19.2;
            // Tank drive: both sticks forward for a second (robot now faces +Y).
            double stick = (t >= 21.0 && t < 22.0) ? 0.7 : 0.0;
            hw.primary.axes[GamepadIO.AXIS_LEFT_Y] = stick;
            hw.primary.axes[GamepadIO.AXIS_RIGHT_Y] = stick;

            robot.loopFunc();
            t += period;
            hw.clock().sleepUntil(t);

            if (t - lastPrint >= 1.0) {
                lastPrint = t;
                Pose2d p = robot.getContainer().getDriveSubsystem().getPose();
                String mode = !hw.competition.enabled ? "disabled" : hw.competition.autonomous ? "auto" : "teleop";
                System.out.println("SIM t=" + Math.round(t) + "s " + mode + " " + p);
            }
        }
    }

    // ---------------------------------------------------------------------------------

    private static void simulatePhysics(SimHardware hw) {
        int[] left = {DriveConstants.kLeftFrontMotorPort, DriveConstants.kLeftMiddleMotorPort, DriveConstants.kLeftRearMotorPort};
        int[] right = {DriveConstants.kRightFrontMotorPort, DriveConstants.kRightMiddleMotorPort, DriveConstants.kRightRearMotorPort};
        hw.simulateTankDrive(left, right,
                DriveConstants.kMetersPerMotorRotation, DriveConstants.kTrackWidthMeters);
    }

    private static void runTuning() {
        Telemetry.setEnabled(false);
        CommandScheduler.getInstance().setRobotEnabled(true);

        SimHardware hw = newHardware();
        DriveSubsystem drive = new DriveSubsystem();
        simulatePhysics(hw);
        runFor(hw, 2.5); // IMU calibration

        FeedforwardCharacterization ff = (FeedforwardCharacterization) TuningCommands.characterize(drive);
        runUntilDone(hw, ff, 10.0);
        TurnAutoTune tune = (TurnAutoTune) TuningCommands.autoTuneTurn(drive);
        runUntilDone(hw, tune, 15.0);

        // Try the suggested gains, like pasting them into DriveConstants.
        drive.setGains(drive.getGains().withTurn(tune.getKP(), tune.getKD()).withFeedforward(ff.getKS(), ff.getKV()).withKA(ff.getKA()));
        System.out.println("--- Checking tuned gains ---");
        runUntilDone(hw, TuningCommands.turnTest(drive), 20.0);
        runUntilDone(hw, TuningCommands.driveDistanceTest(drive, 1.0), 10.0);
    }

    private static void runFor(SimHardware hw, double seconds) {
        double end = hw.clock().nowSeconds() + seconds;
        while (hw.clock().nowSeconds() < end) {
            CommandScheduler.getInstance().run();
            hw.clock().sleepUntil(hw.clock().nowSeconds() + 0.010);
        }
    }

    private static void runUntilDone(SimHardware hw, Command command, double timeoutS) {
        command.schedule();
        double start = hw.clock().nowSeconds();
        do {
            CommandScheduler.getInstance().run();
            hw.clock().sleepUntil(hw.clock().nowSeconds() + 0.010);
        } while (command.isScheduled() && hw.clock().nowSeconds() - start < timeoutS);
        if (command.isScheduled()) {
            System.out.println("SIM: " + command.getName() + " timed out");
            command.cancel();
        }
        runFor(hw, 0.5); // let the robot settle between tests
    }
}
