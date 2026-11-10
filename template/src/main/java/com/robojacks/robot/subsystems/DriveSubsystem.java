package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.drive.DifferentialDrive;
import com.robojacks.lib.drive.DriveDistance;
import com.robojacks.lib.drive.DriveGains;
import com.robojacks.lib.drive.DriveToPoint;
import com.robojacks.lib.drive.TankDriveSubsystem;
import com.robojacks.lib.drive.TurnToHeading;
import com.robojacks.lib.hardware.VexEncoder;
import com.robojacks.lib.hardware.VexInertial;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;
import com.robojacks.lib.estimator.DifferentialDrivePoseEstimator;
import com.robojacks.lib.math.Vector3;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import com.robojacks.lib.telemetry.Telemetry;

import static com.robojacks.robot.Constants.DriveConstants.*;

/**
 * Contains all methods for control of the drivetrain: a 6-motor tank drive with an
 * Inertial Sensor and odometry. Vision (the GPS) corrects odometry through
 * {@link #addVisionMeasurement}. Motors are set up REV-style: one leader per side with two
 * followers, configured through VexMotorConfig.
 *
 * @see com.robojacks.robot.commands.TankDrive
 * @see com.robojacks.robot.commands.ArcadeDrive
 */
public class DriveSubsystem extends SubsystemBase implements TankDriveSubsystem {
    // Left side: the front motor leads, the others follow it
    private final VexMotor m_leftLeader = new VexMotor(kLeftFrontMotorPort, kCartridge);
    private final VexMotor m_leftFollower1 = new VexMotor(kLeftMiddleMotorPort, kCartridge);
    private final VexMotor m_leftFollower2 = new VexMotor(kLeftRearMotorPort, kCartridge);

    // Right side
    private final VexMotor m_rightLeader = new VexMotor(kRightFrontMotorPort, kCartridge);
    private final VexMotor m_rightFollower1 = new VexMotor(kRightMiddleMotorPort, kCartridge);
    private final VexMotor m_rightFollower2 = new VexMotor(kRightRearMotorPort, kCartridge);

    // Encoders on the leaders, reading meters (see the conversion factors below)
    private final VexEncoder m_leftEncoder = m_leftLeader.getEncoder();
    private final VexEncoder m_rightEncoder = m_rightLeader.getEncoder();

    // The robot's drive
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftLeader, m_rightLeader);

    // The Inertial Sensor (gyro)
    private final VexInertial m_gyro = new VexInertial(kImuPort);

    // Tracks the robot's position on the field: odometry, corrected by vision (the GPS) when
    // the Vision subsystem sends measurements
    private final DifferentialDrivePoseEstimator m_poseEstimator;

    // Gains used by the library's auto commands (TurnToHeading, DriveToPoint, ...)
    private DriveGains m_gains = new DriveGains(
            kTurnP, kTurnD, kDriveP, kDriveD, kS, kV, kMaxAutoVolts,
            kTurnToleranceDegrees, kDriveToleranceMeters).withKA(kA);

    // Odometry waits for the Inertial Sensor to finish calibrating
    private boolean m_odometryStarted = false;
    private Pose2d m_startPose = Pose2d.kZero;

    /** Creates a new DriveSubsystem. */
    public DriveSubsystem() {
        super("DriveSubsystem");

        // Settings every drive motor shares
        VexMotorConfig globalConfig = new VexMotorConfig();
        globalConfig
                .idleMode(IdleMode.kBrake)
                .smartCurrentLimit(kCurrentLimitAmps);
        globalConfig.encoder
                .positionConversionFactor(kMetersPerMotorRotation)          // meters
                .velocityConversionFactor(kMetersPerMotorRotation / 60.0);  // meters per second

        VexMotorConfig leftLeaderConfig = new VexMotorConfig();
        VexMotorConfig leftFollower1Config = new VexMotorConfig();
        VexMotorConfig leftFollower2Config = new VexMotorConfig();
        VexMotorConfig rightLeaderConfig = new VexMotorConfig();
        VexMotorConfig rightFollower1Config = new VexMotorConfig();
        VexMotorConfig rightFollower2Config = new VexMotorConfig();

        leftLeaderConfig.apply(globalConfig).inverted(kLeftFrontMotorReversed);
        leftFollower1Config.apply(globalConfig).inverted(kLeftMiddleMotorReversed).follow(m_leftLeader);
        leftFollower2Config.apply(globalConfig).inverted(kLeftRearMotorReversed).follow(m_leftLeader);
        rightLeaderConfig.apply(globalConfig).inverted(kRightFrontMotorReversed);
        rightFollower1Config.apply(globalConfig).inverted(kRightMiddleMotorReversed).follow(m_rightLeader);
        rightFollower2Config.apply(globalConfig).inverted(kRightRearMotorReversed).follow(m_rightLeader);

        m_leftLeader.configure(leftLeaderConfig);
        m_leftFollower1.configure(leftFollower1Config);
        m_leftFollower2.configure(leftFollower2Config);
        m_rightLeader.configure(rightLeaderConfig);
        m_rightFollower1.configure(rightFollower1Config);
        m_rightFollower2.configure(rightFollower2Config);

        resetEncoders();
        m_poseEstimator = new DifferentialDrivePoseEstimator(
                m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance(), Pose2d.kZero);
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
        m_poseEstimator.update(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance());

        Pose2d pose = getPose();
        Telemetry.putPose("drive/pose", pose);
        Telemetry.put("drive/leftMps", getLeftEncoderRate());
        Telemetry.put("drive/rightMps", getRightEncoderRate());
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
        m_leftLeader.setVoltage(leftVolts);
        m_rightLeader.setVoltage(rightVolts);
    }

    /** Scales all driver output, e.g. 0.5 for a slow/precision mode. */
    public void setMaxOutput(double maxOutput) {
        m_drive.setMaxOutput(maxOutput);
    }

    /** Brake (holds its spot when pushed) or coast (rolls freely, good for defense). */
    public void setIdleMode(IdleMode mode) {
        VexMotorConfig config = new VexMotorConfig().idleMode(mode);
        m_leftLeader.configure(config);
        m_leftFollower1.configure(config);
        m_leftFollower2.configure(config);
        m_rightLeader.configure(config);
        m_rightFollower1.configure(config);
        m_rightFollower2.configure(config);
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
        m_leftEncoder.setPosition(0.0);
        m_rightEncoder.setPosition(0.0);
    }

    /** Left side distance traveled, meters. */
    public double getLeftEncoderDistance() {
        return m_leftEncoder.getPosition();
    }

    /** Right side distance traveled, meters. */
    public double getRightEncoderDistance() {
        return m_rightEncoder.getPosition();
    }

    @Override
    public double getAverageEncoderDistance() {
        return (getLeftEncoderDistance() + getRightEncoderDistance()) / 2.0;
    }

    @Override
    public double getLeftEncoderRate() {
        return m_leftEncoder.getVelocity();
    }

    @Override
    public double getRightEncoderRate() {
        return m_rightEncoder.getVelocity();
    }

    /** The robot's position on the field (meters, field-centered). */
    @Override
    public Pose2d getPose() {
        return m_poseEstimator.getEstimatedPosition();
    }

    /** Tells odometry where the robot is. */
    @Override
    public void resetOdometry(Pose2d pose) {
        if (!m_odometryStarted) {
            m_startPose = pose; // applied once the Inertial Sensor finishes calibrating
            return;
        }
        m_poseEstimator.resetPosition(m_gyro.getRotation2d(), getLeftEncoderDistance(), getRightEncoderDistance(), pose);

    }

    /**
     * Adds a vision measurement (from the Vision subsystem's GPS) to the pose estimate, the way
     * FRC drive subsystems take PhotonVision estimates.
     *
     * @param visionPose       where vision says the robot was
     * @param timestampSeconds when ({@code Timer.getFPGATimestamp()} time)
     * @param stdDevs          how much to trust it: x, y (m), heading (rad)
     */
    public void addVisionMeasurement(Pose2d visionPose, double timestampSeconds, Vector3 stdDevs) {
        if (m_odometryStarted) {
            m_poseEstimator.addVisionMeasurement(visionPose, timestampSeconds, stdDevs);
        }
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
    public double getTrackWidthMeters() {
        return kTrackWidthMeters;
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
