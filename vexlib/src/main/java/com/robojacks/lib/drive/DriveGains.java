package com.robojacks.lib.drive;

/**
 * Tuning numbers the library's drive commands use. Build it from your DriveConstants:
 * <pre>{@code
 * new DriveGains(kTurnP, kTurnD, kDriveP, kDriveD, kS, kMaxAutoVolts,
 *                kTurnToleranceDegrees, kDriveToleranceMeters)
 * }</pre>
 * Outputs are volts: turn gains are volts per radian, drive gains volts per meter.
 */
public final class DriveGains {
    public final double turnP;
    public final double turnD;
    public final double driveP;
    public final double driveD;
    /** Volts added to every nonzero auto output to overcome friction. */
    public final double kS;
    public final double maxAutoVolts;
    public final double turnToleranceRad;
    public final double driveToleranceMeters;

    public DriveGains(double turnP, double turnD, double driveP, double driveD, double kS,
                      double maxAutoVolts, double turnToleranceDegrees, double driveToleranceMeters) {
        this.turnP = turnP;
        this.turnD = turnD;
        this.driveP = driveP;
        this.driveD = driveD;
        this.kS = kS;
        this.maxAutoVolts = maxAutoVolts;
        this.turnToleranceRad = Math.toRadians(turnToleranceDegrees);
        this.driveToleranceMeters = driveToleranceMeters;
    }

    /** Adds kS in the direction of travel so small outputs still overcome friction. */
    public double addStatic(double volts) {
        if (Math.abs(volts) < 1e-3) {
            return 0.0;
        }
        return volts + Math.signum(volts) * kS;
    }

    /** Copy with different turn gains (for tuning experiments). */
    public DriveGains withTurn(double p, double d) {
        return new DriveGains(p, d, driveP, driveD, kS, maxAutoVolts, Math.toDegrees(turnToleranceRad), driveToleranceMeters);
    }

    /** Copy with a different kS (for tuning experiments). */
    public DriveGains withKS(double newKS) {
        return new DriveGains(turnP, turnD, driveP, driveD, newKS, maxAutoVolts, Math.toDegrees(turnToleranceRad), driveToleranceMeters);
    }
}
