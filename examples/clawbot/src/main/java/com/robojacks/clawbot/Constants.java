package com.robojacks.clawbot;

import com.robojacks.lib.hal.Cartridge;

/**
 * Constants for the V5 Clawbot (the standard kit build). Ports match the usual Clawbot
 * wiring; if yours differs, change them here.
 */
public final class Constants {
    private Constants() {}

    /** true = A/B/X/Y run drivetrain tuning tests instead of match controls (results over serial). */
    public static final boolean kTuningMode = false;

    public static final class DriveConstants {
        // ---- Hardware ----
        public static final int kLeftMotorPort = 1;
        public static final boolean kLeftMotorReversed = false;
        public static final int kRightMotorPort = 10;
        public static final boolean kRightMotorReversed = true;
        public static final Cartridge kCartridge = Cartridge.GREEN;
        public static final double kCurrentLimitAmps = 2.5;

        /**
         * The kit Clawbot has no Inertial Sensor, so heading comes from the wheel encoders.
         * If you add one, set kHasImu = true and its port below: turns get much more accurate.
         */
        public static final boolean kHasImu = false;
        public static final int kImuPort = 4;

        // ---- Geometry ----
        public static final double kGearRatio = 1.0; // direct drive
        public static final double kWheelDiameterInches = 4.0;
        /** Center of left wheel to center of right wheel. MEASURE YOURS: it sets turn accuracy without an IMU. */
        public static final double kTrackWidthInches = 11.0;

        public static final double kWheelDiameterMeters = kWheelDiameterInches * 0.0254;
        public static final double kTrackWidthMeters = kTrackWidthInches * 0.0254;
        public static final double kMetersPerMotorRotation = kGearRatio * Math.PI * kWheelDiameterMeters;

        // ---- Gains: paste tuning results here ----
        public static final double kTurnP = 8.0;     // volts per radian
        public static final double kTurnD = 0.4;
        public static final double kDriveP = 30.0;   // volts per meter
        public static final double kDriveD = 1.0;
        public static final double kS = 0.6;         // volts to overcome friction
        public static final double kMaxAutoVolts = 8.0;
        public static final double kTurnToleranceDegrees = 2.0;
        public static final double kDriveToleranceMeters = 0.025;
    }

    public static final class ArmConstants {
        public static final int kMotorPort = 8;
        public static final boolean kReversed = false;
        public static final Cartridge kCartridge = Cartridge.GREEN;
        public static final double kCurrentLimitAmps = 2.5;

        /** Arm rotations per motor rotation. Count your gears: motor gear teeth / arm gear teeth. */
        public static final double kGearRatio = 12.0 / 84.0;
        /** Arm degrees per motor rotation: makes the arm encoder read in degrees. */
        public static final double kDegreesPerMotorRotation = 360.0 * kGearRatio;

        // The arm must be all the way DOWN when the program starts: that's 0 degrees.
        public static final double kMinDegrees = 0.0;
        public static final double kMaxDegrees = 95.0;

        // Presets (degrees above the starting position).
        public static final double kDownDegrees = 0.0;
        public static final double kCarryDegrees = 25.0;
        public static final double kHighDegrees = 85.0;

        public static final double kManualVolts = 8.0;
        public static final double kPresetMaxRpm = 120.0;  // motor rpm limit for preset moves
        public static final double kToleranceDegrees = 3.0;
        public static final double kMoveTimeoutSeconds = 2.5;
    }

    public static final class ClawConstants {
        public static final int kMotorPort = 3;
        /** If R1 closes the claw instead of opening it, flip this. */
        public static final boolean kReversed = false;
        public static final Cartridge kCartridge = Cartridge.GREEN;
        /** Low on purpose: the claw stalls against objects, and this keeps it cool. */
        public static final double kCurrentLimitAmps = 1.2;

        public static final double kOpenVolts = 6.0;
        public static final double kCloseVolts = -6.0;
        /** Gentle squeeze kept on after closing so the object doesn't slip out. */
        public static final double kSqueezeVolts = -2.0;

        /** The claw is considered stopped (fully open, or gripping) below this speed. */
        public static final double kStallRpm = 5.0;
        /** Ignore stall detection right after starting, while the motor spins up. */
        public static final double kSpinUpSeconds = 0.2;
        public static final double kMoveTimeoutSeconds = 1.0;
    }

    public static final class OIConstants {
        public static final double kDriveDeadband = 0.05;
    }
}
