package com.robojacks.lib.sim;

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
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Transform2d;
import com.robojacks.lib.math.Translation2d;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import javax.imageio.ImageIO;

/**
 * Desktop stand-in for the brain. Motors are first-order (voltage -> speed with lag) with
 * static friction, time runs as fast as the CPU allows, and you script the competition mode
 * and controller from a sim main().
 *
 * <p>Sensors are plain objects your sim script can set ({@code sim.getDistanceSensor(5).distanceMeters = 0.3})
 * or tie to the simulated robot: {@link #simulateTankDrive} tracks the robot's true pose, which
 * the GPS reads and tracking wheels ({@link #simulateTrackingWheel}) follow. The brain screen is
 * drawn into an image you can save with {@link SimScreen#save}.
 */
public class SimHardware implements Hardware {
    private double time;
    private final Map<Integer, SimMotor> motors = new HashMap<>();
    private final SimImu imu = new SimImu();
    public final SimGamepad primary = new SimGamepad();
    public final SimGamepad partner = new SimGamepad();
    public final SimCompetition competition = new SimCompetition();
    public final SimScreen screen = new SimScreen();
    public final SimPower power = new SimPower();
    private final Set<Integer> usedPorts = new HashSet<>();
    private final Map<Integer, SimRotationSensor> rotationSensors = new HashMap<>();
    private final Map<Integer, SimDistanceSensor> distanceSensors = new HashMap<>();
    private final Map<Integer, SimOpticalSensor> opticalSensors = new HashMap<>();
    private final Map<Integer, SimGps> gpsSensors = new HashMap<>();
    private final Map<Integer, SimAdiPort> adiPorts = new HashMap<>();
    private Pose2d truePose = new Pose2d();
    private Runnable driveStep = () -> {};

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
        lastDt = dt;
        driveStep.run();
        for (SimRotationSensor r : rotationSensors.values()) {
            r.step(dt);
        }
    }

    private double lastDt;

    private void claim(int port, String what) {
        if (port < 1 || port > 21) {
            throw new IllegalArgumentException(what + ": smart port must be 1-21, got " + port);
        }
        if (!usedPorts.add(port)) {
            throw new IllegalStateException("Port " + port + " has already been used"); // same as the brain
        }
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public MotorIO motor(int port, boolean reversed, Cartridge cartridge) {
        claim(port, "Motor");
        SimMotor m = new SimMotor(cartridge.freeRpm);
        motors.put(port, m);
        return m;
    }

    public SimMotor getMotor(int port) {
        return motors.get(port);
    }

    @Override
    public ImuIO imu(int port) {
        claim(port, "Inertial Sensor");
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

    @Override
    public RotationSensorIO rotationSensor(int port) {
        claim(port, "Rotation Sensor");
        SimRotationSensor s = new SimRotationSensor();
        rotationSensors.put(port, s);
        return s;
    }

    public SimRotationSensor getRotationSensor(int port) {
        return rotationSensors.get(port);
    }

    @Override
    public DistanceSensorIO distanceSensor(int port) {
        claim(port, "Distance Sensor");
        SimDistanceSensor s = new SimDistanceSensor();
        distanceSensors.put(port, s);
        return s;
    }

    public SimDistanceSensor getDistanceSensor(int port) {
        return distanceSensors.get(port);
    }

    @Override
    public OpticalSensorIO opticalSensor(int port) {
        claim(port, "Optical Sensor");
        SimOpticalSensor s = new SimOpticalSensor();
        opticalSensors.put(port, s);
        return s;
    }

    public SimOpticalSensor getOpticalSensor(int port) {
        return opticalSensors.get(port);
    }

    @Override
    public GpsIO gps(int port) {
        claim(port, "GPS Sensor");
        SimGps s = new SimGps(() -> truePose);
        gpsSensors.put(port, s);
        return s;
    }

    public SimGps getGps(int port) {
        return gpsSensors.get(port);
    }

    @Override
    public AdiPortIO adiPort(int expanderPort, int adiPort) {
        if (adiPort < 1 || adiPort > 8) {
            throw new IllegalArgumentException("3-wire port must be 1-8 (A-H), got " + adiPort);
        }
        int key = expanderPort * 10 + adiPort;
        if (adiPorts.containsKey(key)) {
            throw new IllegalStateException("3-wire port " + (char) ('A' + adiPort - 1) + " has already been used");
        }
        if (expanderPort != 0 && adiPorts.keySet().stream().noneMatch(k -> k / 10 == expanderPort)) {
            claim(expanderPort, "3-Wire Expander");
        }
        SimAdiPort p = new SimAdiPort();
        adiPorts.put(key, p);
        return p;
    }

    /** A 3-wire port your sim script can read (solenoid state) or set (press a limit switch). */
    public SimAdiPort getAdiPort(int expanderPort, char letter) {
        return adiPorts.get(expanderPort * 10 + (Character.toUpperCase(letter) - 'A' + 1));
    }

    public SimAdiPort getAdiPort(char letter) {
        return getAdiPort(0, letter);
    }

    @Override
    public ScreenIO screen() {
        return screen;
    }

    @Override
    public PowerIO power() {
        return power;
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
        driveStep = () -> {
            double l = sideSpeed(leftPorts, metersPerMotorRotation);
            double r = sideSpeed(rightPorts, metersPerMotorRotation);
            double v = (l + r) / 2.0;
            double w = (r - l) / trackWidthMeters;
            truePose = truePose.exp(new com.robojacks.lib.math.Twist2d(v * lastDt, 0.0, w * lastDt));
        };
    }

    /** Where the simulated robot really is (no wheel slip is modeled, so odometry should match it). */
    public Pose2d getTruePose() {
        return truePose;
    }

    /** Puts the simulated robot somewhere, e.g. its starting tile. */
    public void setTruePose(Pose2d pose) {
        truePose = pose;
    }

    /**
     * Makes a Rotation Sensor act as an unpowered tracking wheel on the simulated robot.
     *
     * @param port                the Rotation Sensor's port
     * @param forward             true for a wheel that rolls forward/back, false for one that rolls sideways
     * @param robotToWheel        where the wheel touches the floor, from the robot's center
     *                            (WPILib robot frame: +X forward, +Y left)
     * @param wheelDiameterMeters tracking wheel diameter
     */
    public void simulateTrackingWheel(int port, boolean forward, Translation2d robotToWheel, double wheelDiameterMeters) {
        simulateTrackingWheel(port, forward, forward ? robotToWheel.getY() : robotToWheel.getX(), wheelDiameterMeters);
    }

    /**
     * Same, with the wheel's offset as a single number: for a forward wheel, how far LEFT of
     * center it is; for a sideways wheel, how far IN FRONT of center.
     */
    public void simulateTrackingWheel(int port, boolean forward, double offsetMeters, double wheelDiameterMeters) {
        SimRotationSensor sensor = rotationSensors.get(port);
        if (sensor == null) {
            throw new IllegalStateException("Create the Rotation Sensor on port " + port + " before simulating it");
        }
        final Pose2d[] last = { truePose };
        sensor.velocitySupplier = null;
        sensor.stepHook = () -> {
            Pose2d now = truePose;
            com.robojacks.lib.math.Twist2d t = last[0].log(now);
            last[0] = now;
            // Robot-relative motion of the wheel's contact point.
            double along = forward ? t.dx - offsetMeters * t.dtheta : t.dy + offsetMeters * t.dtheta;
            sensor.position += along / (Math.PI * wheelDiameterMeters);
        };
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
        // Direction is logical in sim: what the code commands is what the mechanism does.
        @Override public void setInverted(boolean inverted) {}
        @Override public void setCurrentLimit(double amps) {}
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
        /** What the controller's screen shows (3 lines of 19 characters). */
        public final String[] screenLines = { "", "", "" };
        /** The last rumble pattern sent, or null. */
        public String lastRumble;

        @Override public double getAxis(int axis) { return axes[axis]; }
        @Override public boolean getButton(int button) { return buttons[button]; }

        @Override
        public void setText(int line, int column, String text) {
            if (line < 0 || line > 2) {
                return;
            }
            StringBuilder sb = new StringBuilder(screenLines[line]);
            while (sb.length() < 19) {
                sb.append(' ');
            }
            for (int i = 0; i < text.length() && column + i < 19; i++) {
                sb.setCharAt(column + i, text.charAt(i));
            }
            screenLines[line] = sb.toString();
        }

        @Override
        public void rumble(String pattern) {
            lastRumble = pattern;
        }
    }

    public static class SimCompetition implements CompetitionIO {
        public boolean enabled;
        public boolean autonomous;

        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean isAutonomous() { return autonomous; }
    }

    // ---------------------------------------------------------------------------------

    public static class SimRotationSensor implements RotationSensorIO {
        public double position;
        /** If set, position integrates this (rotations per second) every step. */
        public DoubleSupplier velocitySupplier;
        Runnable stepHook;
        private double lastPosition;
        private double velocity;
        private boolean reversed;

        void step(double dt) {
            if (stepHook != null) {
                stepHook.run();
            } else if (velocitySupplier != null) {
                position += velocitySupplier.getAsDouble() * dt;
            }
            velocity = dt > 0 ? (position - lastPosition) / dt : 0.0;
            lastPosition = position;
        }

        @Override public double getPositionRotations() { return reversed ? -position : position; }
        @Override public double getVelocityRps() { return reversed ? -velocity : velocity; }
        @Override public double getAbsoluteRotations() {
            double p = getPositionRotations() % 1.0;
            return p < 0 ? p + 1.0 : p;
        }
        @Override public void setPositionRotations(double rotations) {
            position = reversed ? -rotations : rotations;
            lastPosition = position;
        }
        @Override public void setReversed(boolean r) { reversed = r; }
        @Override public boolean isConnected() { return true; }
    }

    public static class SimDistanceSensor implements DistanceSensorIO {
        /** NaN = nothing in range. */
        public double distanceMeters = Double.NaN;
        public int confidence = 63;
        public int objectSize = 200;
        public double objectVelocityMps;

        @Override public double getDistanceMeters() { return distanceMeters; }
        @Override public int getConfidence() { return Double.isNaN(distanceMeters) ? 0 : confidence; }
        @Override public int getObjectSize() { return Double.isNaN(distanceMeters) ? 0 : objectSize; }
        @Override public double getObjectVelocityMps() { return objectVelocityMps; }
        @Override public boolean isConnected() { return true; }
    }

    public static class SimOpticalSensor implements OpticalSensorIO {
        public double hue;
        public double saturation;
        public double brightness;
        public int proximity;
        public double ledPower;

        /** Shows the sensor a color: hue in degrees, close enough to read. */
        public void showColor(double hueDegrees) {
            hue = hueDegrees;
            saturation = 0.8;
            brightness = 0.5;
            proximity = 200;
        }

        /** Nothing in front of the sensor. */
        public void clear() {
            hue = 0;
            saturation = 0;
            brightness = 0.05;
            proximity = 0;
        }

        @Override public double getHue() { return hue; }
        @Override public double getSaturation() { return saturation; }
        @Override public double getBrightness() { return brightness; }
        @Override public int getProximity() { return proximity; }
        @Override public void setLedPower(double power) { ledPower = power; }
        @Override public boolean isConnected() { return true; }
    }

    /**
     * GPS that reads the simulated robot's true pose, as seen by a sensor mounted on it, in the
     * sensor's compass frame. The mounting is whatever the robot code declares with
     * {@code VexGps(port, robotToGps)}, unless the sim script sets one with {@link #setMounting}
     * (e.g. to test what happens when the code's transform is wrong).
     */
    public static class SimGps implements GpsIO {
        private final Supplier<Pose2d> pose;
        /** Reported error, meters. Set it high to simulate the sensor losing sight of the field strip. */
        public double errorMeters = 0.01;
        /** Adds this much random noise (meters) to each position reading. */
        public double noiseMeters;
        private final java.util.Random random = new java.util.Random(1);
        private Transform2d mounting = new Transform2d(0.0, 0.0, Rotation2d.kZero);
        private boolean mountingFromScript;

        SimGps(Supplier<Pose2d> pose) {
            this.pose = pose;
        }

        /** The sensor's real mounting on the simulated robot (robot-to-GPS, WPILib robot frame). */
        public void setMounting(Transform2d robotToGps) {
            mounting = robotToGps;
            mountingFromScript = true;
        }

        public Transform2d getMounting() {
            return mounting;
        }

        @Override
        public void declareMounting(Transform2d robotToGps) {
            if (!mountingFromScript) {
                mounting = robotToGps;
            }
        }

        private Pose2d sensorPose() {
            return pose.get().plus(mounting);
        }

        private double noise() {
            return noiseMeters == 0 ? 0 : random.nextGaussian() * noiseMeters;
        }

        @Override public double getXMeters() { return sensorPose().getX() + noise(); }
        @Override public double getYMeters() { return sensorPose().getY() + noise(); }
        @Override public double getHeadingDegreesCW() {
            // Library heading: CCW from +X. GPS heading: CW from +Y, the way the sensor faces.
            double h = 90.0 - sensorPose().getRotation().getDegrees();
            h %= 360.0;
            return h < 0 ? h + 360.0 : h;
        }
        @Override public double getErrorMeters() { return errorMeters; }
        @Override public void setOffset(double x, double y) {}
        @Override public void setInitialPose(double x, double y, double headingCW) {}
        @Override public boolean isConnected() { return true; }
    }

    public static class SimAdiPort implements AdiPortIO {
        public Mode mode;
        public int value;

        @Override public void configure(Mode m) { mode = m; }
        @Override public int getValue() { return value; }
        @Override public void setValue(int v) { value = v; }

        /** For a limit switch or bumper. */
        public void setPressed(boolean pressed) { value = pressed ? 1 : 0; }

        /** For a solenoid or other output: what the robot code last wrote. */
        public boolean isOn() { return value != 0; }
    }

    /** Draws into an image the same size as the brain's screen. Script touches with {@link #press}. */
    public static class SimScreen implements ScreenIO {
        private final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        private final Graphics2D g = image.createGraphics();
        private TouchState touch = new TouchState(false, 0, 0, 0);
        private int presses;

        public SimScreen() {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        private Font font(TextSize size) {
            int px = size == TextSize.SMALL ? 12 : size == TextSize.LARGE ? 26 : 16;
            return new Font(Font.MONOSPACED, Font.PLAIN, px);
        }

        @Override public void clear(int color) { fillRect(0, 0, WIDTH, HEIGHT, color); }
        @Override public void fillRect(int x, int y, int w, int h, int color) {
            g.setColor(new Color(color));
            g.fillRect(x, y, w, h);
        }
        @Override public void drawRect(int x, int y, int w, int h, int color) {
            g.setColor(new Color(color));
            g.drawRect(x, y, w - 1, h - 1);
        }
        @Override public void fillCircle(int cx, int cy, int r, int color) {
            g.setColor(new Color(color));
            g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        }
        @Override public void drawText(int x, int y, String text, TextSize size, int color, int background) {
            Font f = font(size);
            g.setFont(f);
            int w = g.getFontMetrics().stringWidth(text);
            int h = g.getFontMetrics().getHeight();
            g.setColor(new Color(background));
            g.fillRect(x, y, w, h);
            g.setColor(new Color(color));
            g.drawString(text, x, y + g.getFontMetrics().getAscent());
        }
        @Override public int textWidth(String text, TextSize size) {
            g.setFont(font(size));
            return g.getFontMetrics().stringWidth(text);
        }
        @Override public TouchState getTouch() { return touch; }

        /** Presses the screen at (x, y). Hold it until {@link #release()}. */
        public void press(int x, int y) {
            touch = new TouchState(true, x, y, ++presses);
        }

        public void release() {
            touch = new TouchState(false, touch.x, touch.y, presses);
        }

        /** The color of one pixel, 0xRRGGBB. */
        public int getPixel(int x, int y) {
            return image.getRGB(x, y) & 0xFFFFFF;
        }

        /** Saves what the screen shows as a PNG. */
        public void save(String path) throws IOException {
            ImageIO.write(image, "png", new File(path));
        }
    }

    public static class SimPower implements PowerIO {
        public double volts = 12.8;
        public double percent = 100.0;
        public double amps = 1.0;
        public double temperatureCelsius = 25.0;

        @Override public double getBatteryVolts() { return volts; }
        @Override public double getBatteryPercent() { return percent; }
        @Override public double getBatteryAmps() { return amps; }
        @Override public double getBatteryTemperatureCelsius() { return temperatureCelsius; }
    }
}
