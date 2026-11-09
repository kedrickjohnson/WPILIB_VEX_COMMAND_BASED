package com.robojacks.sim;

import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.telemetry.Telemetry;
import com.robojacks.robot.Robot;
import com.robojacks.robot.Constants.DriveConstants;

/**
 * Runs the real robot code against SimHardware on your computer:
 * 3 s disabled (IMU calibrates) -> 15 s autonomous -> 5 s teleop pressing D-pad LEFT (turn to 90).
 * Run with: ./gradlew simulate
 */
public final class SimMain {
    private SimMain() {}

    public static void main(String[] args) {
        SimHardware hw = new SimHardware(DriveConstants.CARTRIDGE_RPM);
        HAL.set(hw);
        Telemetry.setEnabled(false); // keep sim output readable; SIM lines below show the pose

        Robot robot = new Robot();
        robot.robotInit();

        // Heading physics: wheel speed difference over track width.
        hw.getImu().setRateSupplier(() -> {
            double l = sideSpeed(hw, DriveConstants.LEFT_PORTS);
            double r = sideSpeed(hw, DriveConstants.RIGHT_PORTS);
            double ccwRadPerSec = (r - l) / DriveConstants.TRACK_WIDTH_M;
            return -Math.toDegrees(ccwRadPerSec); // IMU reports CW-positive
        });

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

    private static double sideSpeed(SimHardware hw, int[] ports) {
        double rpm = 0.0;
        for (int port : ports) {
            rpm += hw.getMotor(port).getVelocityRpm();
        }
        rpm /= ports.length;
        return rpm / 60.0 * DriveConstants.GEAR_RATIO * Math.PI * DriveConstants.WHEEL_DIAMETER_M;
    }
}
