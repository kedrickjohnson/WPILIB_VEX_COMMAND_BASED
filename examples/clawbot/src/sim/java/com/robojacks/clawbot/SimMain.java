package com.robojacks.clawbot;

import com.robojacks.clawbot.Constants.ArmConstants;
import com.robojacks.clawbot.Constants.ClawConstants;
import com.robojacks.clawbot.Constants.DriveConstants;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.sim.SimHardware;
import com.robojacks.lib.telemetry.Telemetry;

/**
 * Runs the Clawbot code on your computer: 2 s disabled, 15 s autonomous, then a scripted
 * driver: open claw, grab, raise high, drive, place.
 * Run with: ./gradlew :examples:clawbot:simulate
 */
public final class SimMain {
    private SimMain() {}

    public static void main(String[] args) {
        SimHardware hw = new SimHardware();
        HAL.set(hw);
        Telemetry.setEnabled(false);

        Robot robot = new Robot();
        robot.robotInit();

        hw.simulateTankDrive(new int[] {DriveConstants.kLeftMotorPort}, new int[] {DriveConstants.kRightMotorPort},
                DriveConstants.kMetersPerMotorRotation, DriveConstants.kTrackWidthMeters);
        // Claw: fully open a quarter turn one way, closes onto an object a quarter turn the other.
        hw.getMotor(ClawConstants.kMotorPort).setHardStops(-0.25, 0.25);
        // Arm: rests on the frame at 0, can't go past ~100 degrees.
        hw.getMotor(ArmConstants.kMotorPort).setHardStops(0.0, 100.0 / 360.0 / ArmConstants.kGearRatio);

        double period = robot.getPeriod();
        double t = 0.0;
        double lastPrint = -1.0;
        while (t < 30.0) {
            hw.competition.enabled = t >= 2.0;
            hw.competition.autonomous = t < 17.0;

            // Scripted driver (teleop starts at 17 s).
            press(hw, GamepadIO.BUTTON_R1, t, 17.5);   // open claw
            press(hw, GamepadIO.BUTTON_A, t, 18.5);    // grab
            press(hw, GamepadIO.BUTTON_UP, t, 20.5);   // arm high
            hw.primary.axes[GamepadIO.AXIS_LEFT_Y] = (t >= 23.0 && t < 24.0) ? 0.8 : 0.0; // drive forward
            press(hw, GamepadIO.BUTTON_B, t, 25.0);    // place

            robot.loopFunc();
            t += period;
            hw.clock().sleepUntil(t);

            if (t - lastPrint >= 1.0) {
                lastPrint = t;
                RobotContainer c = robot.getRobotContainer();
                Pose2d p = c.getDriveSubsystem().getPose();
                String mode = !hw.competition.enabled ? "disabled" : hw.competition.autonomous ? "auto" : "teleop";
                System.out.println("SIM t=" + Math.round(t) + "s " + mode + " " + p
                        + " arm=" + Math.round(c.getArm().getAngleDegrees()) + "deg"
                        + " claw=" + (c.getClaw().isClosed() ? "closed" : "open"));
            }
        }
    }

    /** Holds a button for 0.2 s starting at {@code at}. */
    private static void press(SimHardware hw, int button, double t, double at) {
        if (t >= at && t < at + 0.2) {
            hw.primary.buttons[button] = true;
        } else if (t >= at + 0.2 && t < at + 0.25) {
            hw.primary.buttons[button] = false;
        }
    }
}
