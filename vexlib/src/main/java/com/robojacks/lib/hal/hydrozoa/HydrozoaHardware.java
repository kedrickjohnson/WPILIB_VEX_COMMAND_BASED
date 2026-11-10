package com.robojacks.lib.hal.hydrozoa;

import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.Clock;
import com.robojacks.lib.hal.CompetitionIO;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.Hardware;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;
import com.robojacks.lib.hal.AdiPortIO;
import com.robojacks.lib.hal.DistanceSensorIO;
import com.robojacks.lib.hal.GpsIO;
import com.robojacks.lib.hal.OpticalSensorIO;
import com.robojacks.lib.hal.PowerIO;
import com.robojacks.lib.hal.RotationSensorIO;
import com.robojacks.lib.hal.ScreenIO;
import dev.vexide.hydrozoa.sdk.Hydrozoa;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            VexSdk.Motor.setCurrentLimit(handle, (int) Math.round(Units.ampsToMilliamps(amps)));
        }

        @Override
        public double getCurrentAmps() {
            return isConnected() ? Units.milliampsToAmps(VexSdk.Motor.getCurrent(handle)) : 0.0;
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

    private final HydrozoaGamepad primary = new HydrozoaGamepad(VexSdk.Controller.Id.MASTER);
    private final HydrozoaGamepad partner = new HydrozoaGamepad(VexSdk.Controller.Id.PARTNER);

    @Override
    public GamepadIO primaryController() {
        return primary;
    }

    @Override
    public GamepadIO partnerController() {
        return partner;
    }

    @Override
    public void periodic() {
        double now = clock.nowSeconds();
        primary.flushText(now);
        partner.flushText(now);
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
        /** VEXos drops controller screen updates sent faster than this (PROS waits 50 ms too). */
        private static final double TEXT_INTERVAL_S = 0.050;

        private final VexSdk.Controller.Id id;
        private final String[] pendingLines = new String[3];
        private String pendingRumble;
        private int nextLine;
        private double lastSend = -1.0;

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

        @Override
        public void setText(int line, int column, String text) {
            if (line < 0 || line > 2) {
                return;
            }
            // Keep a full 19-column line so old characters are overwritten.
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < column; i++) {
                sb.append(' ');
            }
            sb.append(text);
            while (sb.length() < 19) {
                sb.append(' ');
            }
            pendingLines[line] = sb.substring(0, 19);
        }

        @Override
        public void rumble(String pattern) {
            pendingRumble = pattern.length() > 8 ? pattern.substring(0, 8) : pattern;
        }

        /** Sends at most one queued update per 50 ms: rumble first, then lines in turn. */
        void flushText(double now) {
            if (now - lastSend < TEXT_INTERVAL_S || !isConnected()) {
                return;
            }
            if (pendingRumble != null) {
                // PROS controller_rumble: line 3, column 0.
                send(3, 0, pendingRumble);
                pendingRumble = null;
                lastSend = now;
                return;
            }
            for (int i = 0; i < 3; i++) {
                int line = (nextLine + i) % 3;
                if (pendingLines[line] != null) {
                    // PROS controller_set_text: lines and columns are 1-based in the SDK.
                    send(line + 1, 1, pendingLines[line]);
                    pendingLines[line] = null;
                    nextLine = (line + 1) % 3;
                    lastSend = now;
                    return;
                }
            }
        }

        private void send(int line, int column, String text) {
            // Plain ASCII, zero-terminated for C. (Built by hand: TeaVM's charset support is limited.)
            byte[] cString = new byte[text.length() + 1];
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                cString[i] = (byte) (c < 128 ? c : '?');
            }
            VexSdk.Controller.setText(id.value(), line, column, Hydrozoa.getByteArrayPointer(cString));
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

    // ---------------------------------------------------------------- smart sensors

    private SmartPort sensorPort(int port) {
        return peripherals.takePort(port);
    }

    private static boolean isType(SmartPort port, SmartDevice.Type type) {
        Optional<SmartDevice.Type> t = port.deviceType();
        return t.isPresent() && t.get() == type;
    }

    /** Prints a one-time warning when a sensor isn't plugged in (or is a different device). */
    private static final class Presence {
        private final SmartPort port;
        private final SmartDevice.Type type;
        private final String name;
        private boolean warned;

        Presence(SmartPort port, SmartDevice.Type type, String name) {
            this.port = port;
            this.type = type;
            this.name = name;
        }

        boolean ok() {
            boolean ok = isType(port, type);
            if (!ok && !warned) {
                warned = true;
                System.out.println("[HAL] no " + name + " found on port " + port.getNumber());
            }
            return ok;
        }
    }

    @Override
    public RotationSensorIO rotationSensor(int port) {
        SmartPort sp = sensorPort(port);
        VexSdk.V5Device h = VexSdk.Device.getByIndex(port - 1);
        Presence present = new Presence(sp, SmartDevice.Type.ROTATION, "Rotation Sensor");
        return new RotationSensorIO() {
            // The sensor reports centidegrees (PROS rotation_get_position does too).
            private double offset;
            private double last;

            @Override
            public double getPositionRotations() {
                if (present.ok()) {
                    last = VexSdk.AbsEnc.getPosition(h) / 36000.0 - offset;
                }
                return last;
            }

            @Override
            public double getVelocityRps() {
                return present.ok() ? VexSdk.AbsEnc.getVelocity(h) / 36000.0 : 0.0;
            }

            @Override
            public double getAbsoluteRotations() {
                return present.ok() ? VexSdk.AbsEnc.getAngle(h) / 36000.0 : 0.0;
            }

            @Override
            public void setPositionRotations(double rotations) {
                if (present.ok()) {
                    offset = VexSdk.AbsEnc.getPosition(h) / 36000.0 - rotations;
                    last = rotations;
                }
            }

            @Override
            public void setReversed(boolean reversed) {
                if (present.ok()) {
                    VexSdk.AbsEnc.setReverseFlag(h, reversed);
                }
            }

            @Override
            public boolean isConnected() {
                return isType(sp, SmartDevice.Type.ROTATION);
            }
        };
    }

    @Override
    public DistanceSensorIO distanceSensor(int port) {
        SmartPort sp = sensorPort(port);
        VexSdk.V5Device h = VexSdk.Device.getByIndex(port - 1);
        Presence present = new Presence(sp, SmartDevice.Type.DISTANCE, "Distance Sensor");
        return new DistanceSensorIO() {
            @Override
            public double getDistanceMeters() {
                if (!present.ok()) {
                    return Double.NaN;
                }
                int mm = VexSdk.Distance.getDistance(h);
                return mm >= 9999 || mm < 0 ? Double.NaN : mm / 1000.0; // 9999 = nothing in range
            }

            @Override
            public int getConfidence() {
                return present.ok() ? VexSdk.Distance.getConfidence(h) : 0;
            }

            @Override
            public int getObjectSize() {
                return present.ok() ? VexSdk.Distance.getObjectSize(h) : 0;
            }

            @Override
            public double getObjectVelocityMps() {
                return present.ok() ? VexSdk.Distance.getObjectVelocity(h) : 0.0;
            }

            @Override
            public boolean isConnected() {
                return isType(sp, SmartDevice.Type.DISTANCE);
            }
        };
    }

    @Override
    public OpticalSensorIO opticalSensor(int port) {
        SmartPort sp = sensorPort(port);
        VexSdk.V5Device h = VexSdk.Device.getByIndex(port - 1);
        Presence present = new Presence(sp, SmartDevice.Type.OPTICAL, "Optical Sensor");
        return new OpticalSensorIO() {
            @Override
            public double getHue() {
                return present.ok() ? VexSdk.Optical.getHue(h) : 0.0;
            }

            @Override
            public double getSaturation() {
                return present.ok() ? VexSdk.Optical.getSat(h) : 0.0;
            }

            @Override
            public double getBrightness() {
                return present.ok() ? VexSdk.Optical.getBrightness(h) : 0.0;
            }

            @Override
            public int getProximity() {
                return present.ok() ? VexSdk.Optical.getProximity(h) : 0;
            }

            @Override
            public void setLedPower(double power) {
                if (present.ok()) {
                    VexSdk.Optical.setLedPwm(h, (int) Math.round(Math.max(0, Math.min(1, power)) * 100));
                }
            }

            @Override
            public boolean isConnected() {
                return isType(sp, SmartDevice.Type.OPTICAL);
            }
        };
    }

    @Override
    public GpsIO gps(int port) {
        SmartPort sp = sensorPort(port);
        VexSdk.V5Device h = VexSdk.Device.getByIndex(port - 1);
        Presence present = new Presence(sp, SmartDevice.Type.GPS, "GPS Sensor");
        return new GpsIO() {
            // V5_DeviceGpsAttitude: nine doubles (pitch, roll, yaw, x, y, z, az, el, rot), little-endian.
            private final byte[] attitude = new byte[72];
            private double lastX;
            private double lastY;

            private void read() {
                if (!present.ok()) {
                    return;
                }
                VexSdk.Gps.getAttitude(h, Hydrozoa.getByteArrayPointer(attitude), false);
                ByteBuffer b = ByteBuffer.wrap(attitude).order(ByteOrder.LITTLE_ENDIAN);
                lastX = b.getDouble(3 * 8);
                lastY = b.getDouble(4 * 8);
            }

            @Override
            public double getXMeters() {
                read();
                return lastX;
            }

            @Override
            public double getYMeters() {
                read();
                return lastY;
            }

            @Override
            public double getHeadingDegreesCW() {
                return present.ok() ? VexSdk.Gps.getDegrees(h) : 0.0;
            }

            @Override
            public double getErrorMeters() {
                return present.ok() ? VexSdk.Gps.getError(h) : Double.POSITIVE_INFINITY;
            }

            @Override
            public void setOffset(double xMeters, double yMeters) {
                if (present.ok()) {
                    VexSdk.Gps.setOrigin(h, xMeters, yMeters);
                }
            }

            @Override
            public void setInitialPose(double xMeters, double yMeters, double headingDegreesCW) {
                if (present.ok()) {
                    VexSdk.Gps.setInitialPosition(h, xMeters, yMeters, headingDegreesCW);
                }
            }

            @Override
            public boolean isConnected() {
                return isType(sp, SmartDevice.Type.GPS);
            }
        };
    }

    // ---------------------------------------------------------------- 3-wire

    /** The brain's own 3-wire ports are an internal ADI device at index 21 (PROS INTERNAL_ADI_PORT 22). */
    private static final int INTERNAL_ADI_INDEX = 21;
    private final Map<Integer, VexSdk.V5Device> adiDevices = new HashMap<>();
    private final Map<Integer, Boolean> adiPortsUsed = new HashMap<>();

    @Override
    public AdiPortIO adiPort(int expanderPort, int adiPort) {
        if (adiPort < 1 || adiPort > 8) {
            throw new IllegalArgumentException("3-wire port must be 1-8 (A-H), got " + adiPort);
        }
        int key = expanderPort * 10 + adiPort;
        if (adiPortsUsed.containsKey(key)) {
            throw new IllegalStateException("3-wire port " + (char) ('A' + adiPort - 1)
                    + (expanderPort == 0 ? "" : " on expander " + expanderPort) + " has already been used");
        }
        adiPortsUsed.put(key, true);
        VexSdk.V5Device device = adiDevices.computeIfAbsent(expanderPort, p -> {
            if (p == 0) {
                return VexSdk.Device.getByIndex(INTERNAL_ADI_INDEX);
            }
            peripherals.takePort(p); // reserve the expander's smart port
            return VexSdk.Device.getByIndex(p - 1);
        });
        int index = adiPort - 1;
        return new AdiPortIO() {
            @Override
            public void configure(Mode mode) {
                VexSdk.Adi.PortConfiguration config;
                switch (mode) {
                    case DIGITAL_IN: config = VexSdk.Adi.PortConfiguration.TYPE_DIGITAL_IN; break;
                    case DIGITAL_OUT: config = VexSdk.Adi.PortConfiguration.TYPE_DIGITAL_OUT; break;
                    case ANALOG_IN: config = VexSdk.Adi.PortConfiguration.TYPE_ANALOG_IN; break;
                    default: config = VexSdk.Adi.PortConfiguration.TYPE_QUAD_ENCODER; break;
                }
                VexSdk.Adi.setPortConfig(device, index, config);
            }

            @Override
            public int getValue() {
                return VexSdk.Adi.getValue(device, index);
            }

            @Override
            public void setValue(int value) {
                VexSdk.Adi.setValue(device, index, value);
            }
        };
    }

    // ---------------------------------------------------------------- screen

    private final ScreenIO screen = new ScreenIO() {
        // V5_TouchStatus: u8 event, (pad), i16 x, i16 y, (pad), i32 pressCount, i32 releaseCount
        private final byte[] touch = new byte[16];

        @Override
        public void clear(int color) {
            fillRect(0, 0, WIDTH, HEIGHT, color);
        }

        @Override
        public void fillRect(int x, int y, int width, int height, int color) {
            VexSdk.Display.vexDisplayForegroundColor(color);
            VexSdk.Display.vexDisplayRectFill(x, y, x + width - 1, y + height - 1);
        }

        @Override
        public void drawRect(int x, int y, int width, int height, int color) {
            VexSdk.Display.vexDisplayForegroundColor(color);
            VexSdk.Display.vexDisplayRectDraw(x, y, x + width - 1, y + height - 1);
        }

        @Override
        public void fillCircle(int centerX, int centerY, int radius, int color) {
            VexSdk.Display.vexDisplayForegroundColor(color);
            VexSdk.Display.vexDisplayCircleFill(centerX, centerY, radius);
        }

        @Override
        public void drawText(int x, int y, String text, TextSize size, int color, int background) {
            VexSdk.Display.vexDisplayForegroundColor(color);
            VexSdk.Display.vexDisplayBackgroundColor(background);
            // The SDK places text by its baseline; move down so (x, y) is the top left.
            int baseline = y + (int) Math.round(textHeight(size) * 0.8);
            switch (size) {
                case SMALL: VexSdk.Display.vexDisplaySmallStringAt(x, baseline, text); break;
                case LARGE: VexSdk.Display.vexDisplayBigStringAt(x, baseline, text); break;
                default: VexSdk.Display.vexDisplayStringAt(x, baseline, text); break;
            }
        }

        private int textHeight(TextSize size) {
            switch (size) {
                case SMALL: return 15;
                case LARGE: return 32;
                default: return 20;
            }
        }

        @Override
        public int textWidth(String text, TextSize size) {
            int medium = VexSdk.Display.vexDisplayStringWidthGet(text);
            switch (size) {
                case SMALL: return (int) Math.round(medium * 0.75);
                case LARGE: return (int) Math.round(medium * 1.6);
                default: return medium;
            }
        }

        @Override
        public TouchState getTouch() {
            VexSdk.Touch.getData(Hydrozoa.getByteArrayPointer(touch));
            ByteBuffer b = ByteBuffer.wrap(touch).order(ByteOrder.LITTLE_ENDIAN);
            int event = b.get(0);
            return new TouchState(event != 0, b.getShort(2), b.getShort(4), b.getInt(8));
        }
    };

    @Override
    public ScreenIO screen() {
        return screen;
    }

    // ---------------------------------------------------------------- battery

    private final PowerIO power = new PowerIO() {
        @Override
        public double getBatteryVolts() {
            return VexSdk.Battery.getVoltage() / 1000.0; // millivolts
        }

        @Override
        public double getBatteryPercent() {
            return VexSdk.Battery.getCapacity();
        }

        @Override
        public double getBatteryAmps() {
            return VexSdk.Battery.getCurrent() / 1000.0; // milliamps
        }

        @Override
        public double getBatteryTemperatureCelsius() {
            return VexSdk.Battery.getTemperature();
        }
    };

    @Override
    public PowerIO power() {
        return power;
    }
}
