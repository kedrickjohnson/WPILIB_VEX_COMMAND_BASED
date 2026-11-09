package com.robojacks.sim;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.drive.DrivetrainConfig;
import com.robojacks.lib.drive.FeedforwardCharacterization;
import com.robojacks.lib.drive.TankDrivetrain;
import com.robojacks.lib.drive.TuningCommands;
import com.robojacks.lib.drive.TurnAutoTune;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.telemetry.Telemetry;
import com.robojacks.robot.Constants;
import com.robojacks.robot.Robot;
import com.robojacks.robot.subsystems.Drivetrain;

/**
 * Runs robot code against SimHardware on your computer.
 *
 * <ul>
 *   <li>{@code ./gradlew simulate} — match: 3 s disabled (IMU calibrates), 15 s auto,
 *       then teleop pressing D-pad LEFT (turn to 90).</li>
 *   <li>{@code ./gradlew simulate --args=tune} — runs the tuning tests and shows how the
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

    private static SimHardware newHardware(DrivetrainConfig config) {
        SimHardware hw = new SimHardware(config.cartridge.freeRpm);
        HAL.set(hw);
        return hw;
    }

    /** Heading physics: wheel speed difference over track width. Call after the drivetrain exists. */
    private static void wireImu(SimHardware hw, DrivetrainConfig config) {
        hw.getImu().setRateSupplier(() -> {
            double l = sideSpeed(hw, config, config.leftPorts);
            double r = sideSpeed(hw, config, config.rightPorts);
            double ccwRadPerSec = (r - l) / config.trackWidthM;
            return -Math.toDegrees(ccwRadPerSec); // IMU reports CW-positive
        });
    }

    private static double sideSpeed(SimHardware hw, DrivetrainConfig config, int[] ports) {
        double rpm = 0.0;
        for (int port : ports) {
            rpm += hw.getMotor(port).getVelocityRpm();
        }
        return rpm / ports.length / 60.0 * config.metersPerMotorRotation();
    }

    // ---------------------------------------------------------------------------------

    private static void runMatch() {
        DrivetrainConfig config = Constants.DRIVE;
        SimHardware hw = newHardware(config);
        Telemetry.setEnabled(false); // keep sim output readable; SIM lines show the pose

        Robot robot = new Robot();
        robot.robotInit();
        wireImu(hw, config);

        double period = robot.getPeriod();
        double t = 0.0;
        double lastPrint = -1.0;
        while (t < 23.0) {
            hw.competition.enabled = t >= 3.0;
            hw.competition.autonomous = t < 18.0;
            hw.primary.buttons[GamepadIO.BUTTON_LEFT] = t >= 19.0 && t < 19.2;

            robot.loopFunc();
            t += period;
            hw.clock().sleepUntil(t);

            if (t - lastPrint >= 1.0) {
                lastPrint = t;
                Pose2d p = robot.getContainer().getDrivetrain().getPose();
                String mode = !hw.competition.enabled ? "disabled" : hw.competition.autonomous ? "auto" : "teleop";
                System.out.println("SIM t=" + Math.round(t) + "s " + mode + " " + p);
            }
        }
    }

    // ---------------------------------------------------------------------------------

    private static void runTuning() {
        Telemetry.setEnabled(false);
        CommandScheduler.getInstance().setRobotEnabled(true);

        // 1) Characterize + auto-tune on the CURRENT config.
        DrivetrainConfig start = Constants.DRIVE;
        SimHardware hw = newHardware(start);
        TankDrivetrain drive = new Drivetrain(start);
        wireImu(hw, start);
        runFor(hw, 2.5); // IMU calibration

        FeedforwardCharacterization ff = (FeedforwardCharacterization) TuningCommands.characterize(drive);
        runUntilDone(hw, ff, 10.0);
        TurnAutoTune tune = (TurnAutoTune) TuningCommands.autoTuneTurn(drive);
        runUntilDone(hw, tune, 15.0);

        // 2) Fresh robot with the suggested gains, then check them.
        CommandScheduler.getInstance().unregisterSubsystem(drive);
        DrivetrainConfig tuned = start.toBuilder()
                .turnPID(tune.getKP(), tune.getKD())
                .feedforward(ff.getKS(), ff.getKV())
                .build();
        SimHardware hw2 = newHardware(tuned);
        TankDrivetrain drive2 = new Drivetrain(tuned);
        wireImu(hw2, tuned);
        runFor(hw2, 2.5);

        System.out.println("--- Checking tuned gains ---");
        runUntilDone(hw2, TuningCommands.turnTest(drive2), 20.0);
        runUntilDone(hw2, TuningCommands.driveDistanceTest(drive2, 1.0), 10.0);
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
