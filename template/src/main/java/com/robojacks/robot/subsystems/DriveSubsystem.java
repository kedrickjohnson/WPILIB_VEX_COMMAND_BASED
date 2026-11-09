package com.robojacks.robot.subsystems;

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
import com.robojacks.lib.hardware.VexMotorGroup;
import com.robojacks.lib.kinematics.DifferentialDriveOdometry;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;

import static com.robojacks.robot.Constants.DriveConstants.*;

/**
 * Contains all methods for control of the drivetrain: a 6-motor tank drive with an
 * Inertial Sensor and odometry.
 *
 * @see com.robojacks.robot.commands.TankDrive
 * @see com.robojacks.robot.commands.ArcadeDrive
 */
public class DriveSubsystem extends SubsystemBase implements TankDriveSubsystem {
    // Left side motors
    private final VexMotor m_leftFront = new VexMotor(kLeftFrontMotorPort, kLeftFrontMotorReversed, kCartridge);
    private final VexMotor m_leftMiddle = new VexMotor(kLeftMiddleMotorPort, kLeftMiddleMotorReversed, kCartridge);
    private final VexMotor m_leftRear = new VexMotor(kLeftRearMotorPort, kLeftRearMotorReversed, kCartridge);

    // Right side motors
    private final VexMotor m_rightFront = new VexMotor(kRightFrontMotorPort, kRightFrontMotorReversed, kCartridge);
    private final VexMotor m_rightMiddle = new VexMotor(kRightMiddleMotorPort, kRightMiddleMotorReversed, kCartridge);
    private final VexMotor m_rightRear = new VexMotor(kRightRearMotorPort, kRightRearMotorReversed, kCartridge);

    // Motor groups for each side
    private final VexMotorGroup m_leftMotors = new VexMotorGroup(m_leftFront, m_leftMiddle, m_leftRear);
    private final VexMotorGroup m_rightMotors = new VexMotorGroup(m_rightFront, m_rightMiddle, m_rightRear);

    // The robot's drive
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftMotors, m_rightMotors);

    // The Inertial Sensor (gyro)
    private final VexInertial m_gyro = new VexInertial(kImuPort);

    // Odometry for tracking the robot's position on the field
    private final DifferentialDriveOdometry m_odometry;

    // Gains used by the library's auto commands (TurnToHeading, DriveToPoint, ...)
    private DriveGains m_gains = new DriveGains(
            kTurnP, kTurnD, kDriveP, kDriveD, kS, kMaxAutoVolts,
            kTurnToleranceDegrees, kDriveToleranceMeters);

    // Odometry waits for the Inertial Sensor to finish calibrating
    private boolean m_odometryStarted = false;
    private Pose2d m_startPose = Pose2d.kZero;

    /** Creates a new DriveSubsystem. */
    public DriveSubsystem() {
        super("DriveSubsystem");
        setBrakeMode(true);
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
        m_odometry.update(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance());

        Pose2d pose = getPose();
        Telemetry.put("drive/x", pose.getX());
        Telemetry.put("drive/y", pose.getY());
        Telemetry.put("drive/deg", pose.getRotation().getDegrees());
    }

    // =====================================================================
    // Driving
    // =====================================================================

    /**
     * Drives the robot with tank controls.
     *
     * @param leftSpeed  left side, -1..1
     * @param rightSpeed right side, -1..1
     */
    public void tankDrive(double leftSpeed, double rightSpeed) {
        m_drive.tankDrive(leftSpeed, rightSpeed);
    }

    /**
     * Drives the robot with arcade controls.
     *
     * @param forward  forward speed, -1..1
     * @param rotation rotation, -1..1, counter-clockwise (left) positive
     */
    public void arcadeDrive(double forward, double rotation) {
        m_drive.arcadeDrive(forward, rotation);
    }

    /** Sets each side's voltage directly. Used by auto commands. */
    @Override
    public void tankDriveVolts(double leftVolts, double rightVolts) {
        m_leftMotors.setVoltage(leftVolts);
        m_rightMotors.setVoltage(rightVolts);
    }

    /** Scales all driver output, e.g. 0.5 for a slow/precision mode. */
    public void setMaxOutput(double maxOutput) {
        m_drive.setMaxOutput(maxOutput);
    }

    /** Brake (holds its spot when pushed) or coast (rolls freely, good for defense). */
    public void setBrakeMode(boolean brake) {
        NeutralMode mode = brake ? NeutralMode.BRAKE : NeutralMode.COAST;
        m_leftMotors.setNeutralMode(mode);
        m_rightMotors.setNeutralMode(mode);
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
        m_leftMotors.resetPosition();
        m_rightMotors.resetPosition();
    }

    /** Left side distance traveled, meters. */
    public double getLeftEncoderDistance() {
        return m_leftMotors.getPosition() * kMetersPerMotorRotation;
    }

    /** Right side distance traveled, meters. */
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

    /** The robot's position on the field (meters, field-centered). */
    @Override
    public Pose2d getPose() {
        return m_odometry.getPoseMeters();
    }

    /** Tells odometry where the robot is. */
    public void resetOdometry(Pose2d pose) {
        if (!m_odometryStarted) {
            m_startPose = pose; // applied once the Inertial Sensor finishes calibrating
            return;
        }
        m_odometry.resetPosition(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance(), pose);
    }

    /** Makes the direction the robot is facing now read as 0 degrees. */
    public void zeroHeading() {
        resetOdometry(new Pose2d(getPose().getTranslation(), Rotation2d.kZero));
    }

    /** Field heading in degrees, -180..180, counter-clockwise positive. */
    public double getHeading() {
        return getPose().getRotation().getDegrees();
    }

    @Override
    public DriveGains getGains() {
        return m_gains;
    }

    /** Swap gains at runtime, e.g. to try numbers from the tuning tests. */
    public void setGains(DriveGains gains) {
        m_gains = gains;
    }

    // =====================================================================
    // Auto commands
    // =====================================================================

    /** Tells odometry where the robot starts, e.g. first step of an auto. */
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
