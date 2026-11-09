package com.robojacks.clawbot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.drive.DifferentialDrive;
import com.robojacks.lib.drive.DriveDistance;
import com.robojacks.lib.drive.DriveGains;
import com.robojacks.lib.drive.DriveToPoint;
import com.robojacks.lib.drive.TankDrive;
import com.robojacks.lib.drive.TurnToHeading;
import com.robojacks.lib.hal.MotorIO.NeutralMode;
import com.robojacks.lib.hardware.VexInertial;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;
import java.util.function.DoubleSupplier;

import static com.robojacks.clawbot.Constants.DriveConstants.*;

/** The Clawbot's two-motor tank drive with odometry. */
public class Drivetrain extends SubsystemBase implements TankDrive {
    private final VexMotor m_leftMotor = new VexMotor(kLeftMotorPort, kLeftMotorReversed, kCartridge);
    private final VexMotor m_rightMotor = new VexMotor(kRightMotorPort, kRightMotorReversed, kCartridge);

    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftMotor, m_rightMotor);

    // Optional: the kit Clawbot doesn't come with an Inertial Sensor.
    private final VexInertial m_gyro = kHasImu ? new VexInertial(kImuPort) : null;

    private final DifferentialDriveOdometry m_odometry;

    private DriveGains m_gains = new DriveGains(
            kTurnP, kTurnD, kDriveP, kDriveD, kS, kMaxAutoVolts,
            kTurnToleranceDegrees, kDriveToleranceMeters);

    private boolean m_odometryStarted = false;
    private Pose2d m_startPose = Pose2d.kZero;

    public Drivetrain() {
        super("Drivetrain");
        m_leftMotor.setNeutralMode(NeutralMode.BRAKE);
        m_rightMotor.setNeutralMode(NeutralMode.BRAKE);
        resetEncoders();
        m_odometry = new DifferentialDriveOdometry(
                getGyroRotation(), getLeftEncoderDistance(), getRightEncoderDistance());
    }

    /**
     * Heading for odometry, counter-clockwise positive. From the Inertial Sensor if there is
     * one, otherwise from how much farther one wheel has gone than the other.
     */
    private Rotation2d getGyroRotation() {
        if (m_gyro != null) {
            return m_gyro.getRotation2d();
        }
        double radians = (getRightEncoderDistance() - getLeftEncoderDistance()) / kTrackWidthMeters;
        return Rotation2d.fromRadians(radians);
    }

    private boolean isGyroCalibrating() {
        return m_gyro != null && m_gyro.isCalibrating();
    }

    @Override
    public void periodic() {
        if (isGyroCalibrating()) {
            return;
        }
        if (!m_odometryStarted) {
            m_odometryStarted = true;
            resetOdometry(m_startPose);
        }
        m_odometry.update(getGyroRotation(), getLeftEncoderDistance(), getRightEncoderDistance());

        Pose2d pose = getPose();
        Telemetry.put("drive/x", pose.getX());
        Telemetry.put("drive/y", pose.getY());
        Telemetry.put("drive/deg", pose.getRotation().getDegrees());
    }

    // =====================================================================
    // Drive methods
    // =====================================================================

    /** @param fwd forward -1..1, @param rot counter-clockwise rotation -1..1 */
    public void arcadeDrive(double fwd, double rot) {
        m_drive.arcadeDrive(fwd, rot);
    }

    @Override
    public void tankDriveVolts(double leftVolts, double rightVolts) {
        m_leftMotor.setVoltage(leftVolts);
        m_rightMotor.setVoltage(rightVolts);
    }

    public void setMaxOutput(double maxOutput) {
        m_drive.setMaxOutput(maxOutput);
    }

    // =====================================================================
    // Sensors and odometry
    // =====================================================================

    public void resetEncoders() {
        m_leftMotor.resetPosition();
        m_rightMotor.resetPosition();
    }

    public double getLeftEncoderDistance() {
        return m_leftMotor.getPosition() * kMetersPerMotorRotation;
    }

    public double getRightEncoderDistance() {
        return m_rightMotor.getPosition() * kMetersPerMotorRotation;
    }

    @Override
    public double getAverageEncoderDistance() {
        return (getLeftEncoderDistance() + getRightEncoderDistance()) / 2.0;
    }

    @Override
    public double getLeftEncoderRate() {
        return m_leftMotor.getVelocity() / 60.0 * kMetersPerMotorRotation;
    }

    @Override
    public double getRightEncoderRate() {
        return m_rightMotor.getVelocity() / 60.0 * kMetersPerMotorRotation;
    }

    @Override
    public Pose2d getPose() {
        return m_odometry.getPoseMeters();
    }

    public void resetOdometry(Pose2d pose) {
        if (!m_odometryStarted) {
            m_startPose = pose;
            return;
        }
        m_odometry.resetPosition(getGyroRotation(), getLeftEncoderDistance(), getRightEncoderDistance(), pose);
    }

    /** Heading in degrees, counter-clockwise positive. */
    public double getHeading() {
        return getPose().getRotation().getDegrees();
    }

    @Override
    public DriveGains getGains() {
        return m_gains;
    }

    public void setGains(DriveGains gains) {
        m_gains = gains;
    }

    // =====================================================================
    // Commands
    // =====================================================================

    public Command arcadeDriveCommand(DoubleSupplier fwd, DoubleSupplier rot) {
        return run(() -> arcadeDrive(fwd.getAsDouble(), rot.getAsDouble())).withName("ArcadeDrive");
    }

    public Command slowModeCommand() {
        return startEnd(() -> setMaxOutput(0.5), () -> setMaxOutput(1.0)).withName("SlowMode");
    }

    public Command resetOdometryCommand(Pose2d pose) {
        return runOnce(() -> resetOdometry(pose)).withName("ResetOdometry");
    }

    public Command stopCommand() {
        return runOnce(this::stop).withName("StopDrive");
    }

    /** Straight line (negative = backward), holding heading. */
    public Command driveDistanceCommand(double meters) {
        return new DriveDistance(this, meters);
    }

    /** Turn to a heading relative to where odometry was last reset (CCW positive). */
    public Command turnToAngleCommand(double degrees) {
        return new TurnToHeading(this, Rotation2d.fromDegrees(degrees));
    }

    public Command driveToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), false);
    }

    public Command backToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), true);
    }
}
