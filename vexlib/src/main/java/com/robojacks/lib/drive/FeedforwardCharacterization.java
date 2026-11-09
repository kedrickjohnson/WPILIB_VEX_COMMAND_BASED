package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.robot.Timer;

/**
 * Quasistatic test: slowly ramps drive voltage and fits volts = kS + kV * speed.
 * kS is the voltage needed to break friction; kV is volts per (m/s).
 * Needs about 1.5 m of clear space in front of the robot.
 */
public class FeedforwardCharacterization extends Command {
    private static final double RAMP_V_PER_S = 1.0;
    private static final double MAX_VOLTS = 4.0;
    private static final double MIN_SPEED = 0.02; // m/s, ignore samples before it moves

    private final TankDrive drive;
    private final Timer timer = new Timer();

    private int n;
    private double sumX;
    private double sumY;
    private double sumXX;
    private double sumXY;

    private double kS = Double.NaN;
    private double kV = Double.NaN;

    public FeedforwardCharacterization(TankDrive drive) {
        this.drive = drive;
        addRequirements(drive);
        setName("FeedforwardCharacterization");
    }

    @Override
    public void initialize() {
        n = 0;
        sumX = sumY = sumXX = sumXY = 0.0;
        kS = kV = Double.NaN;
        timer.restart();
        System.out.println("[Characterize] ramping voltage forward, keep 1.5 m clear...");
    }

    @Override
    public void execute() {
        double volts = Math.min(timer.get() * RAMP_V_PER_S, MAX_VOLTS);
        double speed = (drive.getLeftEncoderRate() + drive.getRightEncoderRate()) / 2.0;
        if (speed > MIN_SPEED) {
            n++;
            sumX += speed;
            sumY += volts;
            sumXX += speed * speed;
            sumXY += speed * volts;
        }
        drive.tankDriveVolts(volts, volts);
    }

    @Override
    public boolean isFinished() {
        return timer.get() * RAMP_V_PER_S >= MAX_VOLTS;
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
        double denom = n * sumXX - sumX * sumX;
        if (n < 20 || Math.abs(denom) < 1e-9) {
            System.out.println("[Characterize] not enough data (robot didn't move or test was stopped early).");
            return;
        }
        kV = (n * sumXY - sumX * sumY) / denom;
        kS = (sumY - kV * sumX) / n;
        System.out.println("[Characterize] " + n + " samples");
        System.out.println("[Characterize] Paste into DriveConstants: kS = "
                + round(kS) + ", kV = " + round(kV));
    }

    public double getKS() {
        return kS;
    }

    public double getKV() {
        return kV;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
