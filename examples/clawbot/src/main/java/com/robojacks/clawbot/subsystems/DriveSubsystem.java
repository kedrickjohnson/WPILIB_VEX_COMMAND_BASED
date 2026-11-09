package com.robojacks.clawbot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.drive.DifferentialDrive;
import com.robojacks.lib.drive.DriveDistance;
import com.robojacks.lib.drive.DriveGains;
import com.robojacks.lib.drive.DriveToPoint;
import com.robojacks.lib.drive.TankDriveSubsystem;
import com.robojacks.lib.drive.TurnToHeading;
import com.robojacks.lib.hal.MotorIO.NeutralMode;
import com.robojacks.lib.hardware.VexInertial;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;

import static com.robojacks.clawbot.Constants.DriveConstants.*;

/**
 * Contains all methods for control of the Clawbot's two-motor tank drive.
 *
 * @see com.robojacks.clawbot.commands.ArcadeDrive
 * @see com.robojacks.clawbot.commands.TankDrive
 */
public class DriveSubsystem extends SubsystemBase implements TankDriveSubsystem {
    private final VexMotor m_leftMotor = new VexMotor(kLeftMotorPort, kLeftMotorReversed, kCartridge);
    private final VexMotor m_rightMotor = new VexMotor(kRightMotorPort, kRightMotorReversed, kCartridge);

    // The robot's drive
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftMotor, m_rightMotor);

    // Optional: the kit Clawbot doesn't come with an Inertial Sensor
    private final VexInertial m_gyro = kHasImu ? new VexInertial(kImuPort) : null;

    // Odometry for tracking the robot's position
    private final DifferentialDriveOdometry m_odometry;

    // Gains used by the library's auto commands (TurnToHeading, DriveToPoint, ...)
    private DriveGains m_gains = new DriveGains(
            kTurnP, kTurnD, kDriveP, kDriveD, kS, kMaxAutoVolts,
            kTurnToleranceDegrees, kDriveToleranceMeters);

    private boolean m_odometryStarted = false;
    private Pose2d m_startPose = Pose2d.kZero;

    /** Creates a new DriveSubsystem. */
    public DriveSubsystem() {
        super("DriveSubsystem");
        setBrakeMode(true);
        resetEncoders();
        m_odometry = new DifferentialDriveOdometry(
                getGyroRotation(), getLeftEncoderDistance(), getRightEncoderDistance());
    }

    /**
     * Heading for odometry, counter-clockwise positive: from the Inertial Sensor if there is
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
    // Driving
    // =====================================================================

    /** Tank controls: each side -1..1. */
    public void tankDrive(double leftSpeed, double rightSpeed) {
        m_drive.tankDrive(leftSpeed, rightSpeed);
    }

    /** Arcade controls: forward -1..1, rotation -1..1 counter-clockwise (left) positive. */
    public void arcadeDrive(double forward, double rotation) {
        m_drive.arcadeDrive(forward, rotation);
    }

    /** Sets each side's voltage directly. Used by auto commands. */
    @Override
    public void tankDriveVolts(double leftVolts, double rightVolts) {
        m_leftMotor.setVoltage(leftVolts);
        m_rightMotor.setVoltage(rightVolts);
    }

    /** Scales all driver output, e.g. 0.5 for a slow/precision mode. */
    public void setMaxOutput(double maxOutput) {
        m_drive.setMaxOutput(maxOutput);
    }

    public void setBrakeMode(boolean brake) {
        NeutralMode mode = brake ? NeutralMode.BRAKE : NeutralMode.COAST;
        m_leftMotor.setNeutralMode(mode);
        m_rightMotor.setNeutralMode(mode);
    }

    /** Stops the drivetrain. */
    @Override
    public void stop() {
        m_drive.stopMotor();
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
    // Auto commands
    // =====================================================================

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
