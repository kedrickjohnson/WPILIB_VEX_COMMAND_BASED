package com.robojacks.robot.subsystems;

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
import com.robojacks.lib.hardware.VexMotorGroup;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;
import java.util.function.DoubleSupplier;

import static com.robojacks.robot.Constants.DriveConstants.*;

/** Tank drive with IMU + encoder odometry, laid out like WPILib's DriveSubsystem example. */
public class Drivetrain extends SubsystemBase implements TankDrive {
    // The motors on the left side of the drive.
    private final VexMotorGroup m_leftMotors =
            new VexMotorGroup(kLeftMotorPorts, kLeftMotorsReversed, kCartridge);

    // The motors on the right side of the drive.
    private final VexMotorGroup m_rightMotors =
            new VexMotorGroup(kRightMotorPorts, kRightMotorsReversed, kCartridge);

    // The robot's drive.
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftMotors, m_rightMotors);

    // The Inertial Sensor (gyro).
    private final VexInertial m_gyro = new VexInertial(kImuPort);

    // Odometry class for tracking robot pose.
    private final DifferentialDriveOdometry m_odometry;

    // Gains used by the library drive commands (TurnToHeading, DriveToPoint, ...).
    private DriveGains m_gains = new DriveGains(
            kTurnP, kTurnD, kDriveP, kDriveD, kS, kMaxAutoVolts,
            kTurnToleranceDegrees, kDriveToleranceMeters);

    // Odometry waits for the IMU to finish calibrating.
    private boolean m_odometryStarted = false;
    private Pose2d m_startPose = Pose2d.kZero;

    /** Creates a new Drivetrain. */
    public Drivetrain() {
        super("Drivetrain");
        m_leftMotors.setNeutralMode(NeutralMode.BRAKE);
        m_rightMotors.setNeutralMode(NeutralMode.BRAKE);
        resetEncoders();
        m_odometry = new DifferentialDriveOdometry(
                m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance());
    }

    @Override
    public void periodic() {
        if (m_gyro.isCalibrating()) {
            return; // don't trust the heading yet
        }
        if (!m_odometryStarted) {
            m_odometryStarted = true;
            resetOdometry(m_startPose);
        }
        // Update the odometry in the periodic block
        m_odometry.update(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance());

        Pose2d pose = getPose();
        Telemetry.put("drive/x", pose.getX());
        Telemetry.put("drive/y", pose.getY());
        Telemetry.put("drive/deg", pose.getRotation().getDegrees());
    }

    // =====================================================================
    // Drive methods
    // =====================================================================

    /**
     * Drives the robot using arcade controls.
     *
     * @param fwd the commanded forward movement, -1..1
     * @param rot the commanded rotation, -1..1, counter-clockwise positive
     */
    public void arcadeDrive(double fwd, double rot) {
        m_drive.arcadeDrive(fwd, rot);
    }

    /** Controls the left and right sides of the drive directly with voltages. */
    @Override
    public void tankDriveVolts(double leftVolts, double rightVolts) {
        m_leftMotors.setVoltage(leftVolts);
        m_rightMotors.setVoltage(rightVolts);
    }

    /** Sets the max output of the drive. Useful for scaling the drive to drive more slowly. */
    public void setMaxOutput(double maxOutput) {
        m_drive.setMaxOutput(maxOutput);
    }

    // =====================================================================
    // Sensors and odometry
    // =====================================================================

    /** Resets the drive encoders to currently read a position of 0. */
    public void resetEncoders() {
        m_leftMotors.resetPosition();
        m_rightMotors.resetPosition();
    }

    /** Left wheel distance traveled, meters. */
    public double getLeftEncoderDistance() {
        return m_leftMotors.getPosition() * kMetersPerMotorRotation;
    }

    /** Right wheel distance traveled, meters. */
    public double getRightEncoderDistance() {
        return m_rightMotors.getPosition() * kMetersPerMotorRotation;
    }

    @Override
    public double getAverageEncoderDistance() {
        return (getLeftEncoderDistance() + getRightEncoderDistance()) / 2.0;
    }

    @Override
    public double getLeftEncoderRate() {
        return m_leftMotors.getVelocity() / 60.0 * kMetersPerMotorRotation;
    }

    @Override
    public double getRightEncoderRate() {
        return m_rightMotors.getVelocity() / 60.0 * kMetersPerMotorRotation;
    }

    /** Returns the currently-estimated pose of the robot (field-centered meters). */
    @Override
    public Pose2d getPose() {
        return m_odometry.getPoseMeters();
    }

    /** Resets the odometry to the specified pose. */
    public void resetOdometry(Pose2d pose) {
        if (!m_odometryStarted) {
            m_startPose = pose; // applied once the IMU finishes calibrating
            return;
        }
        m_odometry.resetPosition(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance(), pose);
    }

    /** Zeroes the heading of the robot. */
    public void zeroHeading() {
        m_gyro.reset();
        resetOdometry(new Pose2d(getPose().getTranslation(), Rotation2d.kZero));
    }

    /** The robot's field heading, -180..180 degrees, counter-clockwise positive. */
    public double getHeading() {
        return getPose().getRotation().getDegrees();
    }

    @Override
    public DriveGains getGains() {
        return m_gains;
    }

    /** Swap gains at runtime, e.g. to try new numbers from the tuning tests. */
    public void setGains(DriveGains gains) {
        m_gains = gains;
    }

    // =====================================================================
    // Commands
    // =====================================================================

    /** Arcade drive from the sticks. Used as the default command. */
    public Command arcadeDriveCommand(DoubleSupplier fwd, DoubleSupplier rot) {
        return run(() -> arcadeDrive(fwd.getAsDouble(), rot.getAsDouble())).withName("ArcadeDrive");
    }

    /** Slows the drive while the command runs (bind with whileTrue). */
    public Command slowModeCommand() {
        return startEnd(() -> setMaxOutput(0.5), () -> setMaxOutput(1.0)).withName("SlowMode");
    }

    /** Tells odometry where the robot is, e.g. its starting spot at the beginning of auto. */
    public Command resetOdometryCommand(Pose2d pose) {
        return runOnce(() -> resetOdometry(pose)).withName("ResetOdometry");
    }

    public Command stopCommand() {
        return runOnce(this::stop).withName("StopDrive");
    }

    /** Drives straight (negative = backward) while holding the current heading. */
    public Command driveDistanceCommand(double meters) {
        return new DriveDistance(this, meters);
    }

    /** Turns in place to a field heading: 0 = +X, 90 = +Y (counter-clockwise positive). */
    public Command turnToAngleCommand(double degrees) {
        return new TurnToHeading(this, Rotation2d.fromDegrees(degrees));
    }

    /** Drives forward to a point on the field, steering as it goes. */
    public Command driveToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), false);
    }

    /** Backs up into a point on the field. */
    public Command backToPointCommand(double xMeters, double yMeters) {
        return new DriveToPoint(this, new Translation2d(xMeters, yMeters), true);
    }
}
