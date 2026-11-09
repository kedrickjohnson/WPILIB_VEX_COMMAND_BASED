package com.robojacks.lib.hal.hydrozoa;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.Clock;
import com.robojacks.lib.hal.CompetitionIO;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.Hardware;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;
import dev.vexide.hydrozoa.CompetitionRuntime;
import dev.vexide.hydrozoa.DeviceException;
import dev.vexide.hydrozoa.Peripherals;
import dev.vexide.hydrozoa.Platform;
import dev.vexide.hydrozoa.RobotDisabledException;
import dev.vexide.hydrozoa.devices.smart.EncoderPosition;
import dev.vexide.hydrozoa.devices.smart.Motor;
import dev.vexide.hydrozoa.devices.smart.SmartDevice;
import dev.vexide.hydrozoa.devices.smart.SmartPort;
import dev.vexide.hydrozoa.sdk.VexSdk;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * The real brain, through the Hydrozoa SDK. This is the ONLY file that imports Hydrozoa.
 * Robot code never sees it: {@link HydrozoaRuntime#start} sets it up.
 *
 * <p>Device errors (unplugged motor, robot disabled by field control) never crash the
 * program. Commands to a missing device are skipped and reported once over serial, and
 * sensors keep returning their last good value.
 */
public class HydrozoaHardware implements Hardware {
    private final Peripherals peripherals;
    private final Instant start = Instant.now();

    public HydrozoaHardware(Peripherals peripherals) {
        this.peripherals = peripherals;
    }

    // ---------------------------------------------------------------- clock

    private final Clock clock = new Clock() {
        @Override
        public double nowSeconds() {
            return Duration.between(start, Instant.now()).toNanos() / 1e9;
        }

        @Override
        public void sleepUntil(double timeSeconds) {
            // Always yield at least once: vexTasksRun() refreshes sensors, controllers and serial.
            do {
                Platform.yield();
            } while (nowSeconds() < timeSeconds);
        }
    };

    @Override
    public Clock clock() {
        return clock;
    }

    // ---------------------------------------------------------------- motors

    private static Motor.Gearset gearset(Cartridge c) {
        switch (c) {
            case RED: return Motor.Gearset.Red;
            case GREEN: return Motor.Gearset.Green;
            default: return Motor.Gearset.Blue;
        }
    }

    @Override
    public MotorIO motor(int port, boolean reversed, Cartridge cartridge) {
        Motor motor = new Motor(peripherals.takePort(port), gearset(cartridge),
                reversed ? Motor.Direction.REVERSE : Motor.Direction.FORWARD);
        return new HydrozoaMotor(motor, port);
    }

    private static final class HydrozoaMotor implements MotorIO {
        private final Motor motor;
        private final int port;
        private final VexSdk.V5Device handle;
        private NeutralMode neutral = NeutralMode.COAST;
        private double lastPosition;
        private boolean warned;

        HydrozoaMotor(Motor motor, int port) {
            this.motor = motor;
            this.port = port;
            this.handle = VexSdk.Device.getByIndex(port - 1);
        }

        @Override
        public void setVoltage(double volts) {
            try {
                if (volts == 0.0 && neutral != NeutralMode.COAST) {
                    motor.brake(neutral == NeutralMode.HOLD ? Motor.BrakeMode.HOLD : Motor.BrakeMode.BRAKE);
                } else {
                    motor.setVoltage(volts);
                }
            } catch (DeviceException e) {
                report(e);
            }
        }

        @Override
        public void setNeutralMode(NeutralMode mode) {
            neutral = mode;
        }

        @Override
        public void setInverted(boolean inverted) {
            motor.setDirection(inverted ? Motor.Direction.REVERSE : Motor.Direction.FORWARD);
        }

        @Override
        public void setCurrentLimit(double amps) {
            VexSdk.Motor.setCurrentLimit(handle, (int) Math.round(amps * 1000.0)); // milliamps
        }

        @Override
        public double getCurrentAmps() {
            return isConnected() ? VexSdk.Motor.getCurrent(handle) / 1000.0 : 0.0;
        }

        @Override
        public double getTemperatureCelsius() {
            return isConnected() ? VexSdk.Motor.getTemperature(handle) : 0.0;
        }

        @Override
        public void setPositionTarget(double rotations, double maxRpm) {
            try {
                motor.moveToPosition(EncoderPosition.ofRotations(rotations), (int) Math.round(maxRpm));
            } catch (DeviceException e) {
                report(e);
            }
        }

        @Override
        public double getPositionRotations() {
            try {
                lastPosition = motor.getPosition().rotations();
            } catch (DeviceException e) {
                report(e);
            }
            return lastPosition;
        }

        @Override
        public double getVelocityRpm() {
            return isConnected() ? VexSdk.Motor.getActualVelocity(handle) : 0.0;
        }

        @Override
        public void resetPosition() {
            try {
                motor.setPosition(EncoderPosition.ofRotations(0.0));
                lastPosition = 0.0;
            } catch (DeviceException e) {
                report(e);
            }
        }

        @Override
        public boolean isConnected() {
            return motor.isConnected();
        }

        private void report(DeviceException e) {
            if (e instanceof RobotDisabledException) {
                return; // normal: field control disabled the robot
            }
            if (!warned) {
                warned = true;
                System.out.println("[HAL] motor on port " + port + ": " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- IMU

    @Override
    public ImuIO imu(int port) {
        return new HydrozoaImu(peripherals.takePort(port), port);
    }

    /**
     * Calibration follows PROS (vdml_imu.c): after reset, VEXos takes a moment to raise the
     * calibrating flag, so wait for it to appear (up to 1 s) and then to clear (up to 3 s total).
     * Status 0xFF means the sensor reported an error.
     */
    private final class HydrozoaImu implements ImuIO {
        private static final int STATUS_CALIBRATING = 0x01;
        private static final int STATUS_ERROR = 0xFF;
        private static final double FLAG_SET_TIMEOUT_S = 1.0;
        private static final double CALIBRATION_TIMEOUT_S = 3.0;

        private static final int IDLE = 0;
        private static final int WAITING_FOR_FLAG = 1;
        private static final int CALIBRATING = 2;

        private final SmartPort smartPort;
        private final VexSdk.V5Device handle;
        private final int port;
        private int phase = IDLE;
        private double calibrationStart;
        private double offset;
        private double last;
        private boolean warnedMissing;
        private boolean warnedError;

        HydrozoaImu(SmartPort smartPort, int port) {
            this.smartPort = smartPort;
            this.port = port;
            this.handle = VexSdk.Device.getByIndex(port - 1);
        }

        private boolean connected() {
            Optional<SmartDevice.Type> type = smartPort.deviceType();
            boolean ok = type.isPresent() && type.get() == SmartDevice.Type.IMU;
            if (!ok && !warnedMissing) {
                warnedMissing = true;
                System.out.println("[HAL] no Inertial Sensor found on port " + port);
            }
            return ok;
        }

        private int status() {
            int status = VexSdk.Imu.getStatus(handle) & 0xFF;
            if (status == STATUS_ERROR && !warnedError) {
                warnedError = true;
                System.out.println("[HAL] Inertial Sensor on port " + port + " reports an error");
            }
            return status;
        }

        @Override
        public boolean isCalibrating() {
            if (!connected()) {
                return false;
            }
            int status = status();
            if (status == STATUS_ERROR) {
                phase = IDLE;
                return false;
            }
            boolean flag = (status & STATUS_CALIBRATING) != 0;
            double elapsed = clock.nowSeconds() - calibrationStart;
            switch (phase) {
                case WAITING_FOR_FLAG:
                    if (flag) {
                        phase = CALIBRATING;
                        return true;
                    }
                    if (elapsed > FLAG_SET_TIMEOUT_S) {
                        System.out.println("[HAL] Inertial Sensor on port " + port + " never started calibrating");
                        phase = IDLE;
                        return false;
                    }
                    return true;
                case CALIBRATING:
                    if (!flag) {
                        phase = IDLE;
                        return false;
                    }
                    if (elapsed > CALIBRATION_TIMEOUT_S) {
                        System.out.println("[HAL] Inertial Sensor on port " + port + " calibration timed out");
                        phase = IDLE;
                        return false;
                    }
                    return true;
                default:
                    return flag;
            }
        }

        @Override
        public void calibrate() {
            if (!connected() || status() == STATUS_ERROR) {
                return;
            }
            if ((status() & STATUS_CALIBRATING) != 0) {
                return; // already calibrating (PROS refuses this too)
            }
            VexSdk.Imu.reset(handle);
            calibrationStart = clock.nowSeconds();
            phase = WAITING_FOR_FLAG;
            offset = 0.0;
        }

        @Override
        public double getRotationDegreesCW() {
            if (connected()) {
                // vexDeviceImuHeadingGet: unbounded, clockwise-positive (PROS imu_get_rotation uses it).
                last = VexSdk.Imu.getHeading(handle) - offset;
            }
            return last;
        }

        @Override
        public void resetRotation() {
            if (connected()) {
                offset = VexSdk.Imu.getHeading(handle);
                last = 0.0;
            }
        }
    }

    // ---------------------------------------------------------------- controllers

    @Override
    public GamepadIO primaryController() {
        return new HydrozoaGamepad(VexSdk.Controller.Id.MASTER);
    }

    @Override
    public GamepadIO partnerController() {
        return new HydrozoaGamepad(VexSdk.Controller.Id.PARTNER);
    }

    private static final class HydrozoaGamepad implements GamepadIO {
        private static final VexSdk.Controller.Index[] AXES = {
                VexSdk.Controller.Index.ANA_LEFT_X,
                VexSdk.Controller.Index.ANA_LEFT_Y,
                VexSdk.Controller.Index.ANA_RIGHT_X,
                VexSdk.Controller.Index.ANA_RIGHT_Y,
        };
        // Same order as the GamepadIO.BUTTON_* constants.
        private static final VexSdk.Controller.Index[] BUTTONS = {
                VexSdk.Controller.Index.BUTTON_A,
                VexSdk.Controller.Index.BUTTON_B,
                VexSdk.Controller.Index.BUTTON_X,
                VexSdk.Controller.Index.BUTTON_Y,
                VexSdk.Controller.Index.BUTTON_UP,
                VexSdk.Controller.Index.BUTTON_DOWN,
                VexSdk.Controller.Index.BUTTON_LEFT,
                VexSdk.Controller.Index.BUTTON_RIGHT,
                VexSdk.Controller.Index.BUTTON_L1,
                VexSdk.Controller.Index.BUTTON_L2,
                VexSdk.Controller.Index.BUTTON_R1,
                VexSdk.Controller.Index.BUTTON_R2,
        };

        private final VexSdk.Controller.Id id;

        HydrozoaGamepad(VexSdk.Controller.Id id) {
            this.id = id;
        }

        @Override
        public double getAxis(int axis) {
            if (!isConnected()) {
                return 0.0;
            }
            return VexSdk.Controller.getController(id, AXES[axis]) / 127.0;
        }

        @Override
        public boolean getButton(int button) {
            return isConnected() && VexSdk.Controller.getController(id, BUTTONS[button]) == 1;
        }

        @Override
        public boolean isConnected() {
            // Status is a record built fresh on every call: compare by value, not with !=.
            return !VexSdk.Controller.Status.OFFLINE.equals(VexSdk.Controller.getConnectionStatus(id));
        }
    }

    // ---------------------------------------------------------------- competition

    @Override
    public CompetitionIO competition() {
        return new CompetitionIO() {
            @Override
            public boolean isEnabled() {
                return !CompetitionRuntime.status().disabled();
            }

            @Override
            public boolean isAutonomous() {
                return CompetitionRuntime.status().autonomous();
            }

            @Override
            public boolean isConnectedToField() {
                return CompetitionRuntime.status().connected();
            }
        };
    }
}
