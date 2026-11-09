package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.telemetry.Telemetry;
import java.util.function.DoubleSupplier;

import static com.robojacks.robot.Constants.DriveConstants.*;

/** Tank drivetrain with IMU + encoder odometry. Reads the same as an FRC drive subsystem. */
public class Drivetrain extends SubsystemBase {
    private final MotorIO[] left = new MotorIO[LEFT_PORTS.length];
    private final MotorIO[] right = new MotorIO[RIGHT_PORTS.length];
    private final ImuIO imu;
    private final DifferentialDriveOdometry odometry;
    private boolean odometryReady;
    private Pose2d pendingReset = Pose2d.kZero;

    public Drivetrain() {
        super("Drivetrain");
        for (int i = 0; i < LEFT_PORTS.length; i++) {
            left[i] = HAL.get().motor(LEFT_PORTS[i], LEFT_REVERSED[i]);
            left[i].setBrakeMode(true);
        }
        for (int i = 0; i < RIGHT_PORTS.length; i++) {
            right[i] = HAL.get().motor(RIGHT_PORTS[i], RIGHT_REVERSED[i]);
            right[i].setBrakeMode(true);
        }
        imu = HAL.get().imu(IMU_PORT);
        imu.calibrate();
        odometry = new DifferentialDriveOdometry(Rotation2d.kZero, 0.0, 0.0);
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

    public void stop() {
        tankDriveVolts(0.0, 0.0);
    }

    // ----- Sensors -----

    private static double average(MotorIO[] motors) {
        double sum = 0.0;
        for (MotorIO m : motors) {
            sum += m.getPositionRotations();
        }
        return sum / motors.length;
    }

    private static double rotationsToMeters(double motorRotations) {
        return motorRotations * GEAR_RATIO * Math.PI * WHEEL_DIAMETER_M;
    }

    public double getLeftDistanceMeters() {
        return rotationsToMeters(average(left));
    }

    public double getRightDistanceMeters() {
        return rotationsToMeters(average(right));
    }

    public double getAverageDistanceMeters() {
        return (getLeftDistanceMeters() + getRightDistanceMeters()) / 2.0;
    }

    /** CCW-positive heading from the IMU (VEX reports CW-positive, so negate). */
    public Rotation2d getGyroAngle() {
        return Rotation2d.fromDegrees(-imu.getRotationDegreesCW());
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
        if (imu.isCalibrating()) {
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

    /** Arcade drive from suppliers (forward, CCW turn), used as the default command. */
    public Command arcadeDriveCommand(DoubleSupplier forward, DoubleSupplier turn) {
        return run(() -> arcadeDrive(forward.getAsDouble(), turn.getAsDouble()))
                .withName("ArcadeDrive");
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
}
