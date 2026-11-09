package com.robojacks.lib.drive;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;
import java.util.function.DoubleSupplier;

/**
 * Ready-made tank drivetrain with IMU + encoder odometry. Teams don't edit this file;
 * everything team-specific comes from {@link DrivetrainConfig}.
 */
public class TankDrivetrain extends SubsystemBase {
    private final DrivetrainConfig config;
    private final MotorIO[] left;
    private final MotorIO[] right;
    private final ImuIO imu;
    private final DifferentialDriveOdometry odometry;
    private boolean odometryReady;
    private Pose2d pendingReset = Pose2d.kZero;

    public TankDrivetrain(DrivetrainConfig config) {
        super("Drivetrain");
        this.config = config;
        left = new MotorIO[config.leftPorts.length];
        right = new MotorIO[config.rightPorts.length];
        for (int i = 0; i < left.length; i++) {
            left[i] = HAL.get().motor(config.leftPorts[i], config.leftReversed[i]);
            left[i].setBrakeMode(config.brakeMode);
        }
        for (int i = 0; i < right.length; i++) {
            right[i] = HAL.get().motor(config.rightPorts[i], config.rightReversed[i]);
            right[i].setBrakeMode(config.brakeMode);
        }
        imu = HAL.get().imu(config.imuPort);
        imu.calibrate();
        odometry = new DifferentialDriveOdometry(Rotation2d.kZero, 0.0, 0.0);
    }

    public DrivetrainConfig getConfig() {
        return config;
    }

    // ----- Output -----

    public void tankDriveVolts(double leftVolts, double rightVolts) {
        leftVolts = MathUtil.clamp(leftVolts, -12.0, 12.0);
        rightVolts = MathUtil.clamp(rightVolts, -12.0, 12.0);
        for (MotorIO m : left) {
            m.setVoltage(leftVolts);
        }
        for (MotorIO m : right) {
            m.setVoltage(rightVolts);
        }
    }

    /**
     * @param forward -1..1, positive = forward
     * @param turn    -1..1, positive = counter-clockwise (left), matching WPILib
     */
    public void arcadeDrive(double forward, double turn) {
        double l = forward - turn;
        double r = forward + turn;
        double max = Math.max(1.0, Math.max(Math.abs(l), Math.abs(r)));
        tankDriveVolts(12.0 * l / max, 12.0 * r / max);
    }

    /**
     * Drive with auto-command style outputs: forward and CCW turn in volts, each capped at
     * maxAutoVolts, with kS added so small corrections still overcome friction.
     */
    public void autoDriveVolts(double forwardVolts, double turnVolts) {
        double cap = config.maxAutoVolts;
        forwardVolts = MathUtil.clamp(forwardVolts, -cap, cap);
        turnVolts = MathUtil.clamp(turnVolts, -cap, cap);
        double l = withStatic(forwardVolts - turnVolts);
        double r = withStatic(forwardVolts + turnVolts);
        tankDriveVolts(l, r);
    }

    private double withStatic(double volts) {
        if (Math.abs(volts) < 1e-3) {
            return 0.0;
        }
        return volts + Math.signum(volts) * config.kS;
    }

    public void stop() {
        tankDriveVolts(0.0, 0.0);
    }

    // ----- Sensors -----

    private static double averageRotations(MotorIO[] motors) {
        double sum = 0.0;
        for (MotorIO m : motors) {
            sum += m.getPositionRotations();
        }
        return sum / motors.length;
    }

    private static double averageRpm(MotorIO[] motors) {
        double sum = 0.0;
        for (MotorIO m : motors) {
            sum += m.getVelocityRpm();
        }
        return sum / motors.length;
    }

    public double getLeftDistanceMeters() {
        return averageRotations(left) * config.metersPerMotorRotation();
    }

    public double getRightDistanceMeters() {
        return averageRotations(right) * config.metersPerMotorRotation();
    }

    public double getAverageDistanceMeters() {
        return (getLeftDistanceMeters() + getRightDistanceMeters()) / 2.0;
    }

    public double getLeftVelocityMps() {
        return averageRpm(left) / 60.0 * config.metersPerMotorRotation();
    }

    public double getRightVelocityMps() {
        return averageRpm(right) / 60.0 * config.metersPerMotorRotation();
    }

    /** CCW-positive, unwrapped heading straight from the IMU (VEX reports CW-positive). */
    public Rotation2d getGyroAngle() {
        return Rotation2d.fromDegrees(-imu.getRotationDegreesCW());
    }

    /** Unwrapped IMU heading in degrees, CCW-positive. Useful for multi-turn tests. */
    public double getGyroDegreesContinuous() {
        return -imu.getRotationDegreesCW();
    }

    public boolean isImuCalibrating() {
        return imu.isCalibrating();
    }

    // ----- Odometry -----

    public Pose2d getPose() {
        return odometry.getPoseMeters();
    }

    public Rotation2d getHeading() {
        return getPose().getRotation();
    }

    /** Sets where the robot is on the field. Safe to call while the IMU is still calibrating. */
    public void resetPose(Pose2d pose) {
        if (imu.isCalibrating() || !odometryReady) {
            pendingReset = pose; // applied as soon as calibration finishes
            return;
        }
        odometry.resetPosition(getGyroAngle(), getLeftDistanceMeters(), getRightDistanceMeters(), pose);
    }

    @Override
    public void periodic() {
        if (imu.isCalibrating()) {
            Telemetry.put("drive/imuCalibrating", true);
            return;
        }
        if (!odometryReady) {
            odometryReady = true;
            odometry.resetPosition(getGyroAngle(), getLeftDistanceMeters(), getRightDistanceMeters(), pendingReset);
        }
        Pose2d pose = odometry.update(getGyroAngle(), getLeftDistanceMeters(), getRightDistanceMeters());

        Telemetry.put("drive/imuCalibrating", false);
        Telemetry.put("drive/x", pose.getX());
        Telemetry.put("drive/y", pose.getY());
        Telemetry.put("drive/deg", pose.getRotation().getDegrees());
    }

    // ----- Command factories -----

    /** Arcade drive from suppliers (forward, CCW turn), the usual default command. */
    public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier turn) {
        return run(() -> arcadeDrive(forward.getAsDouble(), turn.getAsDouble())).withName("ArcadeDrive");
    }

    public Command tankDriveCommand(DoubleSupplier leftPct, DoubleSupplier rightPct) {
        return run(() -> tankDriveVolts(12.0 * leftPct.getAsDouble(), 12.0 * rightPct.getAsDouble()))
                .withName("TankDrive");
    }

    public Command resetPoseCommand(Pose2d pose) {
        return runOnce(() -> resetPose(pose)).withName("ResetPose");
    }

    public Command stopCommand() {
        return runOnce(this::stop).withName("StopDrive");
    }

    public Command turnTo(Rotation2d fieldHeading) {
        return new TurnToHeading(this, fieldHeading);
    }

    public Command turnToDegrees(double fieldHeadingDeg) {
        return turnTo(Rotation2d.fromDegrees(fieldHeadingDeg));
    }

    public Command driveTo(Translation2d fieldPoint) {
        return new DriveToPoint(this, fieldPoint, false);
    }

    public Command driveTo(double xMeters, double yMeters) {
        return driveTo(new Translation2d(xMeters, yMeters));
    }

    public Command backTo(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), true);
    }

    /** Straight line forward (negative = backward), holding the starting heading. */
    public Command driveDistance(double meters) {
        return new DriveDistance(this, meters);
    }
}
