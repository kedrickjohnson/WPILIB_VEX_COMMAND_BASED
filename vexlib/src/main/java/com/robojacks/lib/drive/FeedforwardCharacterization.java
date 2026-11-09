package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.robot.Timer;

/**
 * Measures the drivetrain's feedforward: volts = kS + kV * speed + kA * accel.
 *
 * <ol>
 *   <li>Ramp: slowly raises the voltage while driving forward. At such low acceleration the
 *       voltage is almost all friction (kS) and speed (kV), so a straight-line fit gives both.</li>
 *   <li>Pause, then step: backs up with a sudden 6 V. Whatever voltage kS and kV don't explain
 *       went into accelerating, which gives kA.</li>
 * </ol>
 * Needs about 1.5 m clear in front of the robot. Prints a line to paste into DriveConstants.
 */
public class FeedforwardCharacterization extends Command {
    private static final double RAMP_V_PER_S = 1.0;
    private static final double RAMP_MAX_VOLTS = 4.0;
    private static final double PAUSE_S = 0.6;
    private static final double STEP_VOLTS = -6.0;
    private static final double STEP_S = 0.5;
    private static final double MIN_SPEED = 0.02;   // m/s, ignore samples before it moves
    private static final double MIN_ACCEL = 0.3;    // m/s^2, only fit kA where it's accelerating

    private final TankDriveSubsystem drive;
    private final Timer timer = new Timer();

    // ramp fit: volts = kS + kV * v
    private int n;
    private double sumX;
    private double sumY;
    private double sumXX;
    private double sumXY;
    // step fit: residual = kA * a
    private double sumRA;
    private double sumAA;
    private int nA;
    private double lastSpeed;
    private double lastTime;

    private double kS = Double.NaN;
    private double kV = Double.NaN;
    private double kA = Double.NaN;

    public FeedforwardCharacterization(TankDriveSubsystem drive) {
        this.drive = drive;
        addRequirements(drive);
        setName("FeedforwardCharacterization");
    }

    private double speed() {
        return (drive.getLeftEncoderRate() + drive.getRightEncoderRate()) / 2.0;
    }

    @Override
    public void initialize() {
        n = 0;
        nA = 0;
        sumX = sumY = sumXX = sumXY = sumRA = sumAA = 0.0;
        kS = kV = kA = Double.NaN;
        lastSpeed = 0.0;
        lastTime = 0.0;
        timer.restart();
        System.out.println("[Characterize] ramping voltage forward, then backing up. Keep 1.5 m clear...");
    }

    private double rampEnd() {
        return RAMP_MAX_VOLTS / RAMP_V_PER_S;
    }

    @Override
    public void execute() {
        double t = timer.get();
        double v = speed();
        double volts;
        if (t < rampEnd()) {
            volts = t * RAMP_V_PER_S;
            if (v > MIN_SPEED) {
                n++;
                sumX += v;
                sumY += volts;
                sumXX += v * v;
                sumXY += v * volts;
            }
        } else if (t < rampEnd() + PAUSE_S) {
            volts = 0.0;
            if (n >= 20 && Double.isNaN(kV)) {
                fitRamp();
            }
        } else {
            volts = STEP_VOLTS;
            double dt = t - lastTime;
            if (!Double.isNaN(kV) && dt > 1e-4) {
                double a = (v - lastSpeed) / dt;
                if (Math.abs(a) > MIN_ACCEL) {
                    double residual = volts - (Math.signum(volts) * kS + kV * v);
                    sumRA += residual * a;
                    sumAA += a * a;
                    nA++;
                }
            }
        }
        lastSpeed = v;
        lastTime = t;
        drive.tankDriveVolts(volts, volts);
    }

    private void fitRamp() {
        double denom = n * sumXX - sumX * sumX;
        if (Math.abs(denom) < 1e-9) {
            return;
        }
        kV = (n * sumXY - sumX * sumY) / denom;
        kS = (sumY - kV * sumX) / n;
    }

    @Override
    public boolean isFinished() {
        return timer.get() >= rampEnd() + PAUSE_S + STEP_S;
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
        if (Double.isNaN(kV)) {
            fitRamp();
        }
        if (Double.isNaN(kV)) {
            System.out.println("[Characterize] not enough data (robot didn't move or test was stopped early).");
            return;
        }
        if (nA >= 3 && sumAA > 1e-9) {
            kA = Math.max(0.0, sumRA / sumAA);
        }
        System.out.println("[Characterize] " + n + " ramp samples, " + nA + " step samples");
        System.out.println("[Characterize] Paste into DriveConstants: kS = " + round(kS) + ", kV = " + round(kV)
                + (Double.isNaN(kA) ? " (kA: not enough step data)" : ", kA = " + round(kA)));
    }

    public double getKS() {
        return kS;
    }

    public double getKV() {
        return kV;
    }

    public double getKA() {
        return kA;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
