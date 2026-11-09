package com.robojacks.robot;

import com.robojacks.lib.hal.Cartridge;

/**
 * Robot-wide constants, organized like an FRC Constants.java.
 * Units: meters, seconds, volts unless the name says otherwise.
 */
public final class Constants {
    private Constants() {}

    /**
     * true = controller buttons run tuning tests instead of match controls:
     * A = feedforward characterization, B = turn PID auto-tune,
     * X = drive 1 m test, Y = turn test (4 x 90 deg), L1 = abort.
     * Results print over USB serial. Set back to false for matches!
     */
    public static final boolean kTuningMode = false;

    public static final class DriveConstants {
        // ---- Motor ports (smart ports 1-21) ----
        public static final int kLeftFrontMotorPort = 1;
        public static final int kLeftMiddleMotorPort = 2;
        public static final int kLeftRearMotorPort = 3;
        public static final int kRightFrontMotorPort = 4;
        public static final int kRightMiddleMotorPort = 5;
        public static final int kRightRearMotorPort = 6;

        // Each motor's own direction. Flip one if that motor fights the others.
        public static final boolean kLeftFrontMotorReversed = true;
        public static final boolean kLeftMiddleMotorReversed = true;
        public static final boolean kLeftRearMotorReversed = true;
        public static final boolean kRightFrontMotorReversed = false;
        public static final boolean kRightMiddleMotorReversed = false;
        public static final boolean kRightRearMotorReversed = false;

        public static final Cartridge kCartridge = Cartridge.BLUE;
        /** Amps per motor. 2.5 is the V5 11 W motor's maximum. */
        public static final double kCurrentLimitAmps = 2.5;
        public static final int kImuPort = 10;

        // ---- Geometry ----
        /** Wheel rotations per motor rotation: motor gear teeth / wheel gear teeth (1.0 = direct). */
        public static final double kGearRatio = 36.0 / 48.0;
        public static final double kWheelDiameterInches = 3.25;
        /** Center of left wheels to center of right wheels. */
        public static final double kTrackWidthInches = 11.5;

        public static final double kWheelDiameterMeters = kWheelDiameterInches * 0.0254;
        public static final double kTrackWidthMeters = kTrackWidthInches * 0.0254;
        /** Distance traveled per motor-output rotation (VEX reports position after the cartridge). */
        public static final double kMetersPerMotorRotation = kGearRatio * Math.PI * kWheelDiameterMeters;

        // ---- Gains: paste tuning results here ----
        public static final double kTurnP = 9.0;     // volts per radian (tuning test B)
        public static final double kTurnD = 0.5;
        public static final double kDriveP = 25.0;   // volts per meter
        public static final double kDriveD = 1.0;
        /** Volts to overcome friction (tuning test A). With 0, small turn corrections stall. */
        public static final double kS = 0.5;
        public static final double kV = 0.0;
        public static final double kMaxAutoVolts = 9.0;
        public static final double kTurnToleranceDegrees = 2.0;
        public static final double kDriveToleranceMeters = 0.025;
    }

    public static final class IntakeConstants {
        public static final int kMotorPort = 7;
        public static final boolean kReversed = false;
        public static final Cartridge kCartridge = Cartridge.BLUE;
        public static final double kCurrentLimitAmps = 2.5;
        public static final double kIntakeVolts = 12.0;
        public static final double kOuttakeVolts = -10.0;
    }

    public static final class OIConstants {
        public static final double kDriveDeadband = 0.05;
    }
}
