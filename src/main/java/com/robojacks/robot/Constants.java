package com.robojacks.robot;

/** All the numbers you tune live here, like an FRC Constants.java. Units: meters, seconds, volts. */
public final class Constants {
    private Constants() {}

    public static final double INCH = 0.0254;

    public static final class DriveConstants {
        // Smart ports. Flip a "reversed" flag if that side drives backwards.
        public static final int[] LEFT_PORTS = {1, 2, 3};
        public static final boolean[] LEFT_REVERSED = {true, true, true};
        public static final int[] RIGHT_PORTS = {4, 5, 6};
        public static final boolean[] RIGHT_REVERSED = {false, false, false};

        public static final int IMU_PORT = 10;

        /**
         * Wheel rotations per motor-output rotation. VEX reports position AFTER the cartridge,
         * so only external gearing goes here. Example: 36T on motor driving 48T on wheel = 36/48.
         */
        public static final double GEAR_RATIO = 36.0 / 48.0;

        /** Cartridge free speed: red 100, green 200, blue 600 rpm. Used for max speed / sim. */
        public static final double CARTRIDGE_RPM = 600.0;

        public static final double WHEEL_DIAMETER_M = 3.25 * INCH;

        /** Distance between left and right wheel contact patches. Measure it, then tune it. */
        public static final double TRACK_WIDTH_M = 11.5 * INCH;

        public static final double MAX_SPEED_MPS =
                CARTRIDGE_RPM / 60.0 * GEAR_RATIO * Math.PI * WHEEL_DIAMETER_M;

        // Heading PID: output volts per radian of error.
        public static final double TURN_KP = 9.0;
        public static final double TURN_KD = 0.5;
        public static final double TURN_TOLERANCE_RAD = Math.toRadians(2.0);

        // Distance PID: output volts per meter of error.
        public static final double DRIVE_KP = 25.0;
        public static final double DRIVE_KD = 1.0;
        public static final double DRIVE_TOLERANCE_M = 0.03;

        /** Cap autonomous drive voltage so the robot doesn't wheelie or slip. */
        public static final double AUTO_MAX_VOLTS = 9.0;
    }

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
