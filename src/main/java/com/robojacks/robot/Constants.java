package com.robojacks.robot;

import com.robojacks.lib.drive.Cartridge;
import com.robojacks.lib.drive.DrivetrainConfig;

/**
 * THE file teams edit. Set up the drivetrain below and you have a driving robot with
 * odometry, turn-to-heading and drive-to-point.
 */
public final class Constants {
    private Constants() {}

    /**
     * true = controller buttons run tuning tests instead of match controls:
     * A = feedforward characterization, B = turn PID auto-tune,
     * X = drive 1 m test, Y = turn test (4 x 90 deg).
     * Results print over USB serial with a line to paste below. Set back to false for matches!
     */
    public static final boolean TUNING_MODE = false;

    public static final DrivetrainConfig DRIVE = DrivetrainConfig.builder()
            // ---- Hardware: REQUIRED ----
            .leftMotors(-1, -2, -3)      // smart ports; negative = reversed
            .rightMotors(4, 5, 6)
            .imuPort(10)
            .cartridge(Cartridge.BLUE)   // RED 100 / GREEN 200 / BLUE 600 rpm
            .gearRatio(36, 48)           // motor gear teeth, wheel gear teeth (or .directDrive())
            .wheelDiameterInches(3.25)
            .trackWidthInches(11.5)      // center of left wheels to center of right wheels

            // ---- Tuning: optional, paste results from the tuning tests ----
            .turnPID(9.0, 0.5)           // from TurnAutoTune (B)
            .drivePID(25.0, 1.0)
            .feedforward(0.5, 0.0)       // from characterization (A). kS matters: with 0, small
                                         // turn corrections can't beat friction and turns stall.
            .maxAutoVolts(9.0)
            .turnToleranceDegrees(2.0)
            .driveToleranceInches(1.0)
            .build();

    public static final class IntakeConstants {
        public static final int PORT = 7;
        public static final boolean REVERSED = false;
        public static final double INTAKE_VOLTS = 12.0;
        public static final double OUTTAKE_VOLTS = -10.0;
    }

    public static final class OIConstants {
        public static final double DEADBAND = 0.05;
    }
}
