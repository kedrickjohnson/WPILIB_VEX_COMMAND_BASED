package com.robojacks.sim;

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
 * Desktop stand-in for the brain. Motors are first-order (voltage -> speed with lag),
 * time runs as fast as the CPU allows, and the competition mode follows a script.
 */
public class SimHardware implements Hardware {
    private double time;
    private final double freeRpm;
    private final Map<Integer, SimMotor> motors = new HashMap<>();
    private final SimImu imu = new SimImu();
    public final SimGamepad primary = new SimGamepad();
    public final SimGamepad partner = new SimGamepad();
    public final SimCompetition competition = new SimCompetition();

    public SimHardware(double motorFreeRpm) {
        this.freeRpm = motorFreeRpm;
    }

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
    public MotorIO motor(int port, boolean reversed) {
        // Reversal cancels out in sim: the "physical" motor is what code commands.
        return motors.computeIfAbsent(port, p -> new SimMotor(freeRpm));
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

    // ---------------------------------------------------------------------------------

    public static class SimMotor implements MotorIO {
        private static final double TIME_CONSTANT = 0.08; // s, how fast speed follows voltage
        private static final double STATIC_VOLTS = 0.6;   // voltage needed to start moving
        private final double freeRpm;
        private double volts;
        private double rpm;
        private double rotations;

        SimMotor(double freeRpm) {
            this.freeRpm = freeRpm;
        }

        void step(double dt) {
            // Static friction: the first STATIC_VOLTS do nothing, like a real drivetrain.
            double effective = Math.abs(volts) <= STATIC_VOLTS ? 0.0 : volts - Math.signum(volts) * STATIC_VOLTS;
            double target = effective / (12.0 - STATIC_VOLTS) * freeRpm;
            rpm += (target - rpm) * Math.min(1.0, dt / TIME_CONSTANT);
            rotations += rpm / 60.0 * dt;
        }

        @Override public void setVoltage(double v) { volts = Math.max(-12, Math.min(12, v)); }
        @Override public double getPositionRotations() { return rotations; }
        @Override public double getVelocityRpm() { return rpm; }
        @Override public void resetPosition() { rotations = 0.0; }
        @Override public void setBrakeMode(boolean brake) {}
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
