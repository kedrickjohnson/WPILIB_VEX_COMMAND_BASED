package com.robojacks.docs;

import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.drive.DifferentialDrive;
import com.robojacks.lib.drive.DriveGains;
import com.robojacks.lib.drive.TankDriveSubsystem;
import com.robojacks.lib.estimator.TrackingWheelPoseEstimator;
import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hardware.VexInertial;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexMotorConfig;
import com.robojacks.lib.hardware.VexMotorConfig.IdleMode;
import com.robojacks.lib.hardware.VexRotationSensor;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Vector3;
import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.telemetry.Telemetry;

/**
 * A tank drive that tracks its position with two tracking wheels (on Rotation Sensors) and the
 * Inertial Sensor, corrected by vision. Implements TankDriveSubsystem, so the library's auto
 * commands (DriveToPoint, TurnToHeading, path following) work with it.
 */
public class ExampleTrackingDrive extends SubsystemBase implements TankDriveSubsystem {
    // [tracking-constants]
    // Drive geometry
    private static final double kWheelDiameterMeters = Units.inchesToMeters(3.25);
    private static final double kGearRatio = 36.0 / 48.0;
    private static final double kTrackWidthMeters = Units.inchesToMeters(11.5);
    private static final double kMetersPerMotorRotation = kGearRatio * Math.PI * kWheelDiameterMeters;

    // Tracking wheels: 2" omni wheels on Rotation Sensors
    private static final double kTrackingWheelDiameter = Units.inchesToMeters(2.0);
    /** The forward-rolling wheel is 1.25" LEFT of the robot's turning center. */
    private static final double kForwardWheelLeftOffset = Units.inchesToMeters(1.25);
    /** The sideways-rolling wheel is 2.5" BEHIND the turning center. */
    private static final double kSidewaysWheelForwardOffset = Units.inchesToMeters(-2.5);
    // [/tracking-constants]

    private final VexMotor m_leftLeader = new VexMotor(1, Cartridge.BLUE);
    private final VexMotor m_leftFollower = new VexMotor(2, Cartridge.BLUE);
    private final VexMotor m_rightLeader = new VexMotor(3, Cartridge.BLUE);
    private final VexMotor m_rightFollower = new VexMotor(4, Cartridge.BLUE);
    private final DifferentialDrive m_drive = new DifferentialDrive(m_leftLeader, m_rightLeader);
    private final VexInertial m_gyro = new VexInertial(10);

    // [tracking-fields]
    private final VexRotationSensor m_forwardWheel = new VexRotationSensor(13);
    private final VexRotationSensor m_sidewaysWheel = new VexRotationSensor(14, true); // mounted facing the other way
    private final TrackingWheelPoseEstimator m_poseEstimator;
    // [/tracking-fields]

    private final DriveGains m_gains = new DriveGains(0.8, 0.05, 3.0, 0.0, 0.5, 2.1, 10.0, 2.0, 0.02);

    public ExampleTrackingDrive() {
        super("Drive");
        VexMotorConfig config = new VexMotorConfig();
        config.idleMode(IdleMode.kBrake).smartCurrentLimit(2.5);
        config.encoder.positionConversionFactor(kMetersPerMotorRotation)
                .velocityConversionFactor(kMetersPerMotorRotation / 60.0);
        m_leftLeader.configure(new VexMotorConfig().apply(config));
        m_leftFollower.configure(new VexMotorConfig().apply(config).follow(m_leftLeader));
        m_rightLeader.configure(new VexMotorConfig().apply(config).inverted(true));
        m_rightFollower.configure(new VexMotorConfig().apply(config).inverted(true).follow(m_rightLeader));

        // [tracking-setup]
        // Rotation Sensors read in meters of wheel travel
        m_forwardWheel.setPositionConversionFactor(Math.PI * kTrackingWheelDiameter);
        m_sidewaysWheel.setPositionConversionFactor(Math.PI * kTrackingWheelDiameter);
        m_forwardWheel.reset();
        m_sidewaysWheel.reset();

        m_poseEstimator = new TrackingWheelPoseEstimator(
                kForwardWheelLeftOffset, kSidewaysWheelForwardOffset,
                m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition(),
                Pose2d.kZero);
        // [/tracking-setup]
    }

    // [tracking-periodic]
    @Override
    public void periodic() {
        if (m_gyro.isCalibrating()) {
            return; // heading isn't valid yet
        }
        m_poseEstimator.update(m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition());
        Telemetry.putPose("drive/pose", getPose());
    }

    /** Vision (the GPS) sends its estimates here; see the Vision guide. */
    public void addVisionMeasurement(Pose2d pose, double timestampSeconds, Vector3 stdDevs) {
        m_poseEstimator.addVisionMeasurement(pose, timestampSeconds, stdDevs);
    }

    @Override
    public Pose2d getPose() {
        return m_poseEstimator.getEstimatedPosition();
    }

    @Override
    public void resetOdometry(Pose2d pose) {
        m_poseEstimator.resetPosition(m_gyro.getRotation2d(), m_forwardWheel.getPosition(), m_sidewaysWheel.getPosition(), pose);
    }
    // [/tracking-periodic]

    public void arcadeDrive(double forward, double rotation) {
        m_drive.arcadeDrive(forward, rotation);
    }

    @Override
    public void tankDriveVolts(double leftVolts, double rightVolts) {
        m_leftLeader.setVoltage(leftVolts);
        m_rightLeader.setVoltage(rightVolts);
    }

    @Override
    public double getAverageEncoderDistance() {
        // Distance commands measure with the forward tracking wheel: it doesn't slip.
        return m_forwardWheel.getPosition();
    }

    @Override
    public double getLeftEncoderRate() {
        return m_leftLeader.getEncoder().getVelocity();
    }

    @Override
    public double getRightEncoderRate() {
        return m_rightLeader.getEncoder().getVelocity();
    }

    @Override
    public DriveGains getGains() {
        return m_gains;
    }

    @Override
    public double getTrackWidthMeters() {
        return kTrackWidthMeters;
    }
}
