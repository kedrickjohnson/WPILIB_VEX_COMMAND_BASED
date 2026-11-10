package com.robojacks.robot.subsystems;

import com.robojacks.lib.command.SubsystemBase;
import com.robojacks.lib.hardware.VexGps;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.telemetry.Telemetry;
import com.robojacks.lib.vision.EstimateConsumer;
import com.robojacks.lib.vision.EstimatedRobotPose;
import com.robojacks.lib.vision.GpsPoseEstimator;
import java.util.Optional;

import static com.robojacks.robot.Constants.VisionConstants.*;

/**
 * Vision, the way FRC robots do it with PhotonVision: the GPS Sensor is the "camera", a
 * {@link GpsPoseEstimator} turns its readings into pose estimates, and each estimate goes to
 * the drive subsystem's pose estimator through the consumer passed in.
 *
 * <pre>{@code
 * private final Vision m_vision = new Vision(m_robotDrive::addVisionMeasurement);
 * }</pre>
 */
public class Vision extends SubsystemBase {
    private final VexGps m_gps;
    private final GpsPoseEstimator m_photonEstimator;
    private final EstimateConsumer m_estimateConsumer;
    private Pose2d m_lastPose;

    /** @param estimateConsumer where estimates go, usually {@code drive::addVisionMeasurement} */
    public Vision(EstimateConsumer estimateConsumer) {
        super("Vision");
        m_estimateConsumer = estimateConsumer;
        m_gps = new VexGps(kGpsPort, kGpsLeftOffsetMeters, kGpsForwardOffsetMeters);
        m_photonEstimator = new GpsPoseEstimator(m_gps);
        m_photonEstimator.setMaxError(kMaxErrorMeters);
    }

    @Override
    public void periodic() {
        Optional<EstimatedRobotPose> visionEst = m_photonEstimator.update();
        visionEst.ifPresent(est -> {
            m_lastPose = est.estimatedPose;
            m_estimateConsumer.accept(est.estimatedPose, est.timestampSeconds, m_photonEstimator.getEstimationStdDevs());
        });
        Telemetry.put("vision/error", m_gps.getError());
        Telemetry.put("vision/hasEstimate", visionEst.isPresent());
        if (m_lastPose != null) {
            Telemetry.putPose("vision/pose", m_lastPose);
        }
    }

    /** Tells the GPS where the robot starts, so its first readings are good before it sees the strip. */
    public void setStartingPose(Pose2d pose) {
        m_gps.setStartingPose(pose);
    }

    /** The last pose the GPS reported, or null if none yet. */
    public Pose2d getLastPose() {
        return m_lastPose;
    }
}
