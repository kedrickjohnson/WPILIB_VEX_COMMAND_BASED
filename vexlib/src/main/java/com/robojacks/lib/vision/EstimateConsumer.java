package com.robojacks.lib.vision;

import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Vector3;

/**
 * Where a vision subsystem sends its estimates: usually the drive subsystem's
 * {@code addVisionMeasurement}, passed as a method reference. Same shape as the consumer in
 * PhotonVision's example Vision class.
 *
 * <pre>{@code
 * private final Vision m_vision = new Vision(m_robotDrive::addVisionMeasurement);
 * }</pre>
 */
@FunctionalInterface
public interface EstimateConsumer {
    void accept(Pose2d pose, double timestampSeconds, Vector3 stdDevs);
}
