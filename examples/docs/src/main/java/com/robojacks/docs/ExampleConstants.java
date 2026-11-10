package com.robojacks.docs;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.math.util.Units;

/** Constants for the documentation examples. */
public final class ExampleConstants {
    private ExampleConstants() {}

    // [constants]
    public static final class IntakeConstants {
        public static final int kMotorPort = 7;
        public static final Cartridge kCartridge = Cartridge.BLUE;
        public static final double kIntakeVolts = 10.0;
        public static final int kOpticalPort = 8;
        public static final int kDistancePort = 9;
        /** A piece is "in" when the distance sensor reads closer than this. */
        public static final double kPieceDistanceMeters = Units.inchesToMeters(2.0);
    }

    public static final class ArmConstants {
        public static final int kMotorPort = 11;
        public static final Cartridge kCartridge = Cartridge.RED;
        public static final int kRotationSensorPort = 12;
        /** Motor rotations per arm rotation: 84-tooth gear driven by a 12-tooth gear. */
        public static final double kGearRatio = 84.0 / 12.0;
        public static final double kP = 6.0;
        public static final double kD = 0.1;
        public static final double kS = 0.3;   // volts to overcome friction
        public static final double kG = 0.9;   // volts to hold the arm level
        public static final double kV = 1.4;   // volts per rad/s
        public static final double kMaxVelocity = Math.PI;          // rad/s
        public static final double kMaxAcceleration = 2 * Math.PI;  // rad/s^2
    }
    // [/constants]
}
