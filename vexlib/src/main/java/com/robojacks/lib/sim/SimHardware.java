package com.robojacks.lib.sim;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.Clock;
import com.robojacks.lib.hal.CompetitionIO;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.Hardware;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Desktop stand-in for the brain. Motors are first-order (voltage -> speed with lag) with
 * static friction, time runs as fast as the CPU allows, and you script the competition mode
 * and controller from a sim main().
 */
public class SimHardware implements Hardware {
    private double time;
    private final Map<Integer, SimMotor> motors = new HashMap<>();
    private final SimImu imu = new SimImu();
    public final SimGamepad primary = new SimGamepad();
    public final SimGamepad partner = new SimGamepad();
    public final SimCompetition competition = new SimCompetition();

    private final Clock clock = new Clock() {
        @Override
        public double nowSeconds() {
            return time;
        }

        @Override
        public void sleepUntil(double t) {
            if (t > time) {
                step(t - time);
                time = t;
            }
        }
    };

    private void step(double dt) {
        for (SimMotor m : motors.values()) {
            m.step(dt);
        }
        imu.step(dt);
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public MotorIO motor(int port, boolean reversed, Cartridge cartridge) {
        if (motors.containsKey(port)) {
            throw new IllegalStateException("Port " + port + " has already been used"); // same as the brain
        }
        SimMotor m = new SimMotor(cartridge.freeRpm);
        motors.put(port, m);
        return m;
    }

    public SimMotor getMotor(int port) {
        return motors.get(port);
    }

    @Override
    public ImuIO imu(int port) {
        return imu;
    }

    public SimImu getImu() {
        return imu;
    }

    @Override
    public GamepadIO primaryController() {
        return primary;
    }

    @Override
    public GamepadIO partnerController() {
        return partner;
    }

    @Override
    public CompetitionIO competition() {
        return competition;
    }

    /**
     * Makes the IMU turn the way a tank drive would: heading rate is the difference in side
     * speeds over the track width. Call after the drivetrain is created.
     */
    public void simulateTankDrive(int[] leftPorts, int[] rightPorts,
                                  double metersPerMotorRotation, double trackWidthMeters) {
        imu.setRateSupplier(() -> {
            double l = sideSpeed(leftPorts, metersPerMotorRotation);
            double r = sideSpeed(rightPorts, metersPerMotorRotation);
            return -Math.toDegrees((r - l) / trackWidthMeters); // IMU is CW-positive
        });
    }

    private double sideSpeed(int[] ports, double metersPerMotorRotation) {
        double rpm = 0.0;
        for (int port : ports) {
            rpm += motors.get(port).getVelocityRpm();
        }
        return rpm / ports.length / 60.0 * metersPerMotorRotation;
    }

    // ---------------------------------------------------------------------------------

    public static class SimMotor implements MotorIO {
        private static final double TIME_CONSTANT = 0.08; // s, how fast speed follows voltage
        private static final double STATIC_VOLTS = 0.6;   // voltage needed to start moving
        private static final double POSITION_KP = 40.0;   // volts per rotation, built-in PID stand-in

        private final double freeRpm;
        private double volts;
        private double rpm;
        private double rotations;
        private NeutralMode neutral = NeutralMode.COAST;
        private boolean positionMode;
        private double targetRotations;
        private double maxRpm;
        private double minLimit = Double.NEGATIVE_INFINITY;
        private double maxLimit = Double.POSITIVE_INFINITY;

        SimMotor(double freeRpm) {
            this.freeRpm = freeRpm;
        }

        void step(double dt) {
            double applied = volts;
            if (positionMode) {
                double cap = 12.0 * Math.min(1.0, maxRpm / freeRpm);
                applied = Math.max(-cap, Math.min(cap, POSITION_KP * (targetRotations - rotations)));
            }
            double effective = Math.abs(applied) <= STATIC_VOLTS ? 0.0
                    : applied - Math.signum(applied) * STATIC_VOLTS;
            double target = effective / (12.0 - STATIC_VOLTS) * freeRpm;
            boolean holding = !positionMode && applied == 0.0 && neutral != NeutralMode.COAST;
            double tau = holding ? TIME_CONSTANT / 4.0 : TIME_CONSTANT; // brake stops faster
            rpm += (target - rpm) * Math.min(1.0, dt / tau);
            rotations += rpm / 60.0 * dt;
            // Hard stops: the mechanism can't move past them, so the motor stalls.
            if (rotations > maxLimit) {
                rotations = maxLimit;
                rpm = Math.min(rpm, 0.0);
            } else if (rotations < minLimit) {
                rotations = minLimit;
                rpm = Math.max(rpm, 0.0);
            }
        }

        /** Simulate hard stops (an arm hitting its frame, a claw closing on an object). */
        public void setHardStops(double minRotations, double maxRotations) {
            minLimit = minRotations;
            maxLimit = maxRotations;
        }

        @Override public void setVoltage(double v) {
            positionMode = false;
            volts = Math.max(-12, Math.min(12, v));
        }
        @Override public void setNeutralMode(NeutralMode mode) { neutral = mode; }
        @Override public void setPositionTarget(double rot, double rpmLimit) {
            positionMode = true;
            targetRotations = rot;
            maxRpm = rpmLimit;
        }
        @Override public double getPositionRotations() { return rotations; }
        @Override public double getVelocityRpm() { return rpm; }
        @Override public void resetPosition() { rotations = 0.0; }
        public double getAppliedVolts() { return volts; }
    }

    public static class SimImu implements ImuIO {
        private double calibrationLeft;
        private double rotationCW;
        private DoubleSupplier rateDegPerSecCW = () -> 0.0;

        public void setRateSupplier(DoubleSupplier rateDegPerSecCW) {
            this.rateDegPerSecCW = rateDegPerSecCW;
        }

        void step(double dt) {
            if (calibrationLeft > 0) {
                calibrationLeft -= dt;
                return;
            }
            rotationCW += rateDegPerSecCW.getAsDouble() * dt;
        }

        @Override public boolean isCalibrating() { return calibrationLeft > 0; }
        @Override public void calibrate() { calibrationLeft = 2.0; }
        @Override public double getRotationDegreesCW() { return rotationCW; }
        @Override public void resetRotation() { rotationCW = 0.0; }
    }

    public static class SimGamepad implements GamepadIO {
        public final double[] axes = new double[4];
        public final boolean[] buttons = new boolean[12];

        @Override public double getAxis(int axis) { return axes[axis]; }
        @Override public boolean getButton(int button) { return buttons[button]; }
    }

    public static class SimCompetition implements CompetitionIO {
        public boolean enabled;
        public boolean autonomous;

        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean isAutonomous() { return autonomous; }
    }
}
