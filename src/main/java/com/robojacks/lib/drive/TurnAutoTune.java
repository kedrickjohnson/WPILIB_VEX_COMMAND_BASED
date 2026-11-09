package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.robot.Timer;

/**
 * Finds turn PID gains automatically with a relay (bang-bang) test.
 *
 * <p>The robot rocks back and forth around its starting heading with a fixed voltage.
 * From how far it swings (amplitude) and how fast (period) we get the "ultimate gain" Ku
 * and period Tu, then use the Ziegler-Nichols "no overshoot" rule. Needs a clear area to rotate.
 * Results are printed over serial and readable from {@link #getKP()} / {@link #getKD()}.
 */
public class TurnAutoTune extends Command {
    private static final int SETTLE_CYCLES = 2;  // ignore the first swings
    private static final int MEASURE_CYCLES = 4;
    private static final double TIMEOUT_S = 12.0;

    private final TankDrivetrain drive;
    private final double relayVolts;
    private final double hysteresisRad = Math.toRadians(1.0);
    private final Timer timer = new Timer();

    private double targetRad;
    private double output;
    private double lastUpCrossTime;
    private int cycles;
    private double periodSum;
    private double ampSum;
    private int measured;
    private double cycleMax;
    private double cycleMin;

    private double kP = Double.NaN;
    private double kD = Double.NaN;

    public TurnAutoTune(TankDrivetrain drive, double relayVolts) {
        this.drive = drive;
        this.relayVolts = relayVolts;
        addRequirements(drive);
        setName("TurnAutoTune");
    }

    public TurnAutoTune(TankDrivetrain drive) {
        this(drive, 6.0);
    }

    @Override
    public void initialize() {
        targetRad = drive.getGyroAngle().getRadians();
        output = relayVolts;
        lastUpCrossTime = Double.NaN;
        cycles = 0;
        periodSum = 0.0;
        ampSum = 0.0;
        measured = 0;
        cycleMax = Double.NEGATIVE_INFINITY;
        cycleMin = Double.POSITIVE_INFINITY;
        kP = Double.NaN;
        kD = Double.NaN;
        timer.restart();
        System.out.println("[TurnAutoTune] rocking around current heading, keep clear...");
    }

    @Override
    public void execute() {
        double error = MathUtil.angleModulus(targetRad - drive.getGyroAngle().getRadians());
        cycleMax = Math.max(cycleMax, error);
        cycleMin = Math.min(cycleMin, error);

        double previous = output;
        if (error > hysteresisRad) {
            output = relayVolts;
        } else if (error < -hysteresisRad) {
            output = -relayVolts;
        }

        // A switch from - to + output marks one full oscillation.
        if (previous < 0 && output > 0) {
            double now = timer.get();
            if (!Double.isNaN(lastUpCrossTime)) {
                cycles++;
                if (cycles > SETTLE_CYCLES) {
                    periodSum += now - lastUpCrossTime;
                    ampSum += (cycleMax - cycleMin) / 2.0;
                    measured++;
                }
            }
            lastUpCrossTime = now;
            cycleMax = Double.NEGATIVE_INFINITY;
            cycleMin = Double.POSITIVE_INFINITY;
        }

        drive.tankDriveVolts(-output, output); // + = CCW
    }

    @Override
    public boolean isFinished() {
        return measured >= MEASURE_CYCLES || timer.hasElapsed(TIMEOUT_S);
    }

    @Override
    public void end(boolean interrupted) {
        drive.stop();
        if (measured == 0) {
            System.out.println("[TurnAutoTune] no oscillation measured (interrupted or timed out). "
                    + "Try a lower relay voltage or check the IMU.");
            return;
        }
        double tu = periodSum / measured;
        double a = ampSum / measured;
        double effectiveA = Math.sqrt(Math.max(a * a - hysteresisRad * hysteresisRad, 1e-9));
        double ku = 4.0 * relayVolts / (Math.PI * effectiveA);

        // Ziegler-Nichols "no overshoot" rule (gentler than classic ZN): kP = 0.2 Ku, Td = Tu / 3.
        // Overshooting a turn costs more in VEX than arriving a little slower.
        kP = 0.2 * ku;
        kD = kP * tu / 3.0;

        System.out.println("[TurnAutoTune] Ku=" + round(ku) + " V/rad, Tu=" + round(tu) + " s, swing=+/-"
                + round(Math.toDegrees(a)) + " deg");
        System.out.println("[TurnAutoTune] Paste into Constants.java: .turnPID(" + round(kP) + ", " + round(kD) + ")");
        System.out.println("[TurnAutoTune] If turns overshoot, lower kP ~20% or raise kD. Check with turnTest.");
    }

    public double getKP() {
        return kP;
    }

    public double getKD() {
        return kD;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
