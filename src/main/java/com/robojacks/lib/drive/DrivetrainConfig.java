package com.robojacks.lib.drive;

import java.util.HashSet;
import java.util.Set;

/**
 * Everything that makes one team's tank drive different from another's.
 * Build it once in Constants.java:
 *
 * <pre>{@code
 * DrivetrainConfig.builder()
 *     .leftMotors(-1, -2, -3)        // negative port = reversed (PROS / LemLib convention)
 *     .rightMotors(4, 5, 6)
 *     .imuPort(10)
 *     .cartridge(Cartridge.BLUE)
 *     .gearRatio(36, 48)             // motor gear teeth, wheel gear teeth
 *     .wheelDiameterInches(3.25)
 *     .trackWidthInches(11.5)
 *     .build();
 * }</pre>
 *
 * Every tuning value has a sensible default, so only the hardware lines are required.
 */
public final class DrivetrainConfig {
    private static final double INCH = 0.0254;

    // Hardware
    public final int[] leftPorts;
    public final boolean[] leftReversed;
    public final int[] rightPorts;
    public final boolean[] rightReversed;
    public final int imuPort;
    public final Cartridge cartridge;
    /** Wheel rotations per motor-output rotation (VEX reports position after the cartridge). */
    public final double gearRatio;
    public final double wheelDiameterM;
    public final double trackWidthM;
    public final boolean brakeMode;

    // Tuning (output is volts)
    public final double turnKP;
    public final double turnKD;
    public final double turnToleranceRad;
    public final double driveKP;
    public final double driveKD;
    public final double driveToleranceM;
    public final double maxAutoVolts;
    public final double kS;
    public final double kV;

    private DrivetrainConfig(Builder b) {
        leftPorts = new int[b.left.length];
        leftReversed = new boolean[b.left.length];
        for (int i = 0; i < b.left.length; i++) {
            leftPorts[i] = Math.abs(b.left[i]);
            leftReversed[i] = b.left[i] < 0;
        }
        rightPorts = new int[b.right.length];
        rightReversed = new boolean[b.right.length];
        for (int i = 0; i < b.right.length; i++) {
            rightPorts[i] = Math.abs(b.right[i]);
            rightReversed[i] = b.right[i] < 0;
        }
        imuPort = b.imuPort;
        cartridge = b.cartridge;
        gearRatio = b.gearRatio;
        wheelDiameterM = b.wheelDiameterM;
        trackWidthM = b.trackWidthM;
        brakeMode = b.brakeMode;
        turnKP = b.turnKP;
        turnKD = b.turnKD;
        turnToleranceRad = b.turnToleranceRad;
        driveKP = b.driveKP;
        driveKD = b.driveKD;
        driveToleranceM = b.driveToleranceM;
        maxAutoVolts = b.maxAutoVolts;
        kS = b.kS;
        kV = b.kV;
    }

    /** Wheel circumference times gear ratio: meters traveled per motor-output rotation. */
    public double metersPerMotorRotation() {
        return gearRatio * Math.PI * wheelDiameterM;
    }

    /** Theoretical top speed at 12 V, meters per second. */
    public double maxSpeedMps() {
        return cartridge.freeRpm / 60.0 * metersPerMotorRotation();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder pre-filled with this config, e.g. to try new gains: DRIVE.toBuilder().turnPID(8, 0.6).build(). */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.left = signedPorts(leftPorts, leftReversed);
        b.right = signedPorts(rightPorts, rightReversed);
        b.imuPort = imuPort;
        b.cartridge = cartridge;
        b.gearRatio = gearRatio;
        b.wheelDiameterM = wheelDiameterM;
        b.trackWidthM = trackWidthM;
        b.brakeMode = brakeMode;
        b.turnKP = turnKP;
        b.turnKD = turnKD;
        b.turnToleranceRad = turnToleranceRad;
        b.driveKP = driveKP;
        b.driveKD = driveKD;
        b.driveToleranceM = driveToleranceM;
        b.maxAutoVolts = maxAutoVolts;
        b.kS = kS;
        b.kV = kV;
        return b;
    }

    private static int[] signedPorts(int[] ports, boolean[] reversed) {
        int[] out = new int[ports.length];
        for (int i = 0; i < ports.length; i++) {
            out[i] = reversed[i] ? -ports[i] : ports[i];
        }
        return out;
    }

    public static final class Builder {
        private int[] left;
        private int[] right;
        private int imuPort = -1;
        private Cartridge cartridge = Cartridge.BLUE;
        private double gearRatio = 1.0;
        private double wheelDiameterM = 3.25 * INCH;
        private double trackWidthM = -1;
        private boolean brakeMode = true;

        private double turnKP = 9.0;
        private double turnKD = 0.5;
        private double turnToleranceRad = Math.toRadians(2.0);
        private double driveKP = 25.0;
        private double driveKD = 1.0;
        private double driveToleranceM = 0.03;
        private double maxAutoVolts = 9.0;
        private double kS = 0.0;
        private double kV = 0.0;

        private Builder() {}

        /** Smart ports for the left side. Negative = reversed. */
        public Builder leftMotors(int... ports) {
            left = ports.clone();
            return this;
        }

        /** Smart ports for the right side. Negative = reversed. */
        public Builder rightMotors(int... ports) {
            right = ports.clone();
            return this;
        }

        public Builder imuPort(int port) {
            imuPort = port;
            return this;
        }

        public Builder cartridge(Cartridge c) {
            cartridge = c;
            return this;
        }

        /** E.g. a 36-tooth gear on the motor driving a 48-tooth gear on the wheel: gearRatio(36, 48). */
        public Builder gearRatio(double motorTeeth, double wheelTeeth) {
            gearRatio = motorTeeth / wheelTeeth;
            return this;
        }

        /** Direct drive. */
        public Builder directDrive() {
            gearRatio = 1.0;
            return this;
        }

        public Builder wheelDiameterInches(double in) {
            wheelDiameterM = in * INCH;
            return this;
        }

        public Builder trackWidthInches(double in) {
            trackWidthM = in * INCH;
            return this;
        }

        public Builder brakeMode(boolean brake) {
            brakeMode = brake;
            return this;
        }

        /** Turn PID, volts per radian. Use TuningCommands.autoTuneTurn to find these. */
        public Builder turnPID(double kP, double kD) {
            turnKP = kP;
            turnKD = kD;
            return this;
        }

        public Builder turnToleranceDegrees(double deg) {
            turnToleranceRad = Math.toRadians(deg);
            return this;
        }

        /** Distance PID, volts per meter. */
        public Builder drivePID(double kP, double kD) {
            driveKP = kP;
            driveKD = kD;
            return this;
        }

        public Builder driveToleranceInches(double in) {
            driveToleranceM = in * INCH;
            return this;
        }

        /** Voltage cap for autonomous moves (stops wheelies and wheel slip). */
        public Builder maxAutoVolts(double volts) {
            maxAutoVolts = volts;
            return this;
        }

        /** Feedforward from TuningCommands.characterize: volts = kS + kV * (m/s). */
        public Builder feedforward(double kS, double kV) {
            this.kS = kS;
            this.kV = kV;
            return this;
        }

        public DrivetrainConfig build() {
            if (left == null || left.length == 0 || right == null || right.length == 0) {
                throw new IllegalStateException("DrivetrainConfig: set leftMotors(...) and rightMotors(...)");
            }
            Set<Integer> used = new HashSet<>();
            checkPorts(left, used);
            checkPorts(right, used);
            if (imuPort < 1 || imuPort > 21) {
                throw new IllegalStateException("DrivetrainConfig: set imuPort(1-21)");
            }
            if (!used.add(imuPort)) {
                throw new IllegalStateException("DrivetrainConfig: IMU port " + imuPort + " is also a motor port");
            }
            if (trackWidthM <= 0) {
                throw new IllegalStateException("DrivetrainConfig: set trackWidthInches(...)");
            }
            if (wheelDiameterM <= 0 || gearRatio <= 0) {
                throw new IllegalStateException("DrivetrainConfig: wheel diameter and gear ratio must be > 0");
            }
            if (maxAutoVolts <= 0 || maxAutoVolts > 12) {
                throw new IllegalStateException("DrivetrainConfig: maxAutoVolts must be in (0, 12]");
            }
            return new DrivetrainConfig(this);
        }

        private static void checkPorts(int[] ports, Set<Integer> used) {
            for (int p : ports) {
                int port = Math.abs(p);
                if (port < 1 || port > 21) {
                    throw new IllegalStateException("DrivetrainConfig: motor port " + p + " is not 1-21");
                }
                if (!used.add(port)) {
                    throw new IllegalStateException("DrivetrainConfig: port " + port + " is used twice");
                }
            }
        }
    }
}
