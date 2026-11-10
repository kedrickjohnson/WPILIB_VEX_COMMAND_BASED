package com.robojacks.lib.vision;

import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hardware.VexGps;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.VecBuilder;
import com.robojacks.lib.math.Vector3;
import java.util.Optional;

/**
 * Turns GPS readings into pose estimates for the drive's pose estimator: VEX's version of
 * PhotonVision's PhotonPoseEstimator. It drops readings the GPS isn't sure of, rate-limits
 * repeats, corrects for latency, and says how much to trust each estimate.
 *
 * <pre>{@code
 * private final VexGps m_gps = new VexGps(kGpsPort, kGpsLeftOffset, kGpsForwardOffset);
 * private final GpsPoseEstimator m_estimator = new GpsPoseEstimator(m_gps);
 *
 * public void periodic() {
 *     m_estimator.update().ifPresent(est ->
 *             m_consumer.accept(est.estimatedPose, est.timestampSeconds, m_estimator.getEstimationStdDevs()));
 * }
 * }</pre>
 *
 * <p>Like PhotonVision, the standard deviations grow with the reading's uncertainty: the GPS's
 * reported error plays the part of tag distance and ambiguity.
 */
public class GpsPoseEstimator {
    /** Readings this far outside the field (meters) are thrown away. */
    private static final double kFieldHalf = 1.8288 + 0.15;

    private final VexGps gps;
    private double latencySeconds = 0.020;
    private double minPeriodSeconds = 0.050;
    private double maxErrorMeters = VexGps.kDefaultMaxError;
    private double minXyStdDev = 0.02;
    private double errorScale = 2.0;
    private double headingStdDev = Double.POSITIVE_INFINITY;
    private double lastUpdate = Double.NEGATIVE_INFINITY;
    private Vector3 currentStdDevs = VecBuilder.fill(1.0, 1.0, 1e6);

    public GpsPoseEstimator(VexGps gps) {
        this.gps = gps;
    }

    /**
     * The newest estimate, or empty when the GPS can't see the field strip, its error is too
     * big, the reading is off the field, or it's too soon after the last estimate.
     */
    public Optional<EstimatedRobotPose> update() {
        double now = HAL.now();
        if (now - lastUpdate < minPeriodSeconds || !gps.isConnected()) {
            return Optional.empty();
        }
        double error = gps.getError();
        if (!(error <= maxErrorMeters)) {
            return Optional.empty();
        }
        Pose2d pose = gps.getPose();
        if (Math.abs(pose.getX()) > kFieldHalf || Math.abs(pose.getY()) > kFieldHalf) {
            return Optional.empty();
        }
        lastUpdate = now;
        double xy = Math.max(minXyStdDev, error * errorScale);
        double theta = Double.isInfinite(headingStdDev) ? 1e6 : headingStdDev;
        currentStdDevs = VecBuilder.fill(xy, xy, theta);
        return Optional.of(new EstimatedRobotPose(pose, now - latencySeconds, error));
    }

    /** How much to trust the last estimate {@link #update()} returned: x, y in meters, heading in radians. */
    public Vector3 getEstimationStdDevs() {
        return currentStdDevs;
    }

    /** How old a reading is when it arrives, seconds (default 0.02). */
    public void setLatency(double seconds) {
        latencySeconds = seconds;
    }

    /** At most one estimate per this many seconds (default 0.05), so repeated readings don't count twice. */
    public void setMinUpdatePeriod(double seconds) {
        minPeriodSeconds = seconds;
    }

    /** Ignore readings with a larger reported error than this, meters (default 0.05). */
    public void setMaxError(double meters) {
        maxErrorMeters = meters;
    }

    /**
     * x/y standard deviation = max(minimum, reported error x scale). Defaults: 0.02 m and 2.
     * Raise the scale to trust the GPS less.
     */
    public void setStdDevModel(double minimumMeters, double errorScale) {
        minXyStdDev = minimumMeters;
        this.errorScale = errorScale;
    }

    /**
     * Also correct heading from the GPS, trusting it to this many radians. By default the GPS
     * heading is ignored and the Inertial Sensor is in charge.
     */
    public void useHeading(double stdDevRadians) {
        headingStdDev = stdDevRadians;
    }

    public VexGps getGps() {
        return gps;
    }
}
