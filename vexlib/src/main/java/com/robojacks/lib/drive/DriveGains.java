package com.robojacks.lib.drive;

/**
 * Tuning numbers the library's drive commands use. Build it from your DriveConstants:
 * <pre>{@code
 * new DriveGains(kTurnP, kTurnD, kDriveP, kDriveD, kS, kV, kMaxAutoVolts,
 *                kTurnToleranceDegrees, kDriveToleranceMeters)
 * }</pre>
 * Outputs are volts: turn gains are volts per radian, drive gains volts per meter,
 * kV volts per (meter per second).
 */
public final class DriveGains {
    public final double turnP;
    public final double turnD;
    public final double driveP;
    public final double driveD;
    /** Volts added to every nonzero output to overcome friction. */
    public final double kS;
    /** Volts per (m/s) of wheel speed. Path following needs this (tuning test A measures it). */
    public final double kV;
    public final double maxAutoVolts;
    public final double turnToleranceRad;
    public final double driveToleranceMeters;
    /** Path following: extra volts per (m/s) of wheel-speed error. */
    public final double pathKP;
    /** Volts per (m/s²): helps the robot keep up while speeding up and slowing down (tuning test A). */
    public final double kA;

    public DriveGains(double turnP, double turnD, double driveP, double driveD, double kS, double kV,
                      double maxAutoVolts, double turnToleranceDegrees, double driveToleranceMeters) {
        this(turnP, turnD, driveP, driveD, kS, kV, maxAutoVolts,
                Math.toRadians(turnToleranceDegrees), driveToleranceMeters, 3.0, 0.0);
    }

    private DriveGains(double turnP, double turnD, double driveP, double driveD, double kS, double kV,
                       double maxAutoVolts, double turnToleranceRad, double driveToleranceMeters,
                       double pathKP, double kA) {
        this.turnP = turnP;
        this.turnD = turnD;
        this.driveP = driveP;
        this.driveD = driveD;
        this.kS = kS;
        this.kV = kV;
        this.maxAutoVolts = maxAutoVolts;
        this.turnToleranceRad = turnToleranceRad;
        this.driveToleranceMeters = driveToleranceMeters;
        this.pathKP = pathKP;
        this.kA = kA;
    }

    /** Adds kS in the direction of travel so small outputs still overcome friction. */
    public double addStatic(double volts) {
        if (Math.abs(volts) < 1e-3) {
            return 0.0;
        }
        return volts + Math.signum(volts) * kS;
    }

    /** Volts to hold a wheel speed (m/s): kS + kV * speed. */
    public double feedforward(double metersPerSecond) {
        return feedforward(metersPerSecond, 0.0);
    }

    /** Volts for a wheel speed (m/s) and acceleration (m/s²): kS + kV * speed + kA * accel. */
    public double feedforward(double metersPerSecond, double accel) {
        if (Math.abs(metersPerSecond) < 1e-4 && Math.abs(accel) < 1e-4) {
            return 0.0;
        }
        double sign = Math.abs(metersPerSecond) > 1e-4 ? Math.signum(metersPerSecond) : Math.signum(accel);
        return sign * kS + kV * metersPerSecond + kA * accel;
    }

    /** Copy with different turn gains (for tuning experiments). */
    public DriveGains withTurn(double p, double d) {
        return new DriveGains(p, d, driveP, driveD, kS, kV, maxAutoVolts, turnToleranceRad, driveToleranceMeters, pathKP, kA);
    }

    /** Copy with different feedforward (for tuning experiments). */
    public DriveGains withFeedforward(double newKS, double newKV) {
        return new DriveGains(turnP, turnD, driveP, driveD, newKS, newKV, maxAutoVolts, turnToleranceRad, driveToleranceMeters, pathKP, kA);
    }

    /** Copy with a different kS (for tuning experiments). */
    public DriveGains withKS(double newKS) {
        return withFeedforward(newKS, kV);
    }

    /** Copy with an acceleration feedforward gain (tuning test A measures it). */
    public DriveGains withKA(double newKA) {
        return new DriveGains(turnP, turnD, driveP, driveD, kS, kV, maxAutoVolts, turnToleranceRad, driveToleranceMeters, pathKP, newKA);
    }

    /** Copy with a different path-following velocity gain. */
    public DriveGains withPathKP(double newPathKP) {
        return new DriveGains(turnP, turnD, driveP, driveD, kS, kV, maxAutoVolts, turnToleranceRad, driveToleranceMeters, newPathKP, kA);
    }
}
