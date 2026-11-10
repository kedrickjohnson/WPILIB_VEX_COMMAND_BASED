package com.robojacks.lib.vision;

import com.robojacks.lib.math.Pose2d;

/**
 * One pose estimate from a vision source, like PhotonVision's EstimatedRobotPose: where the robot
 * was, and when.
 */
public final class EstimatedRobotPose {
    /** The robot's pose on the field (library frame: meters from center, CCW heading from +X). */
    public final Pose2d estimatedPose;
    /** When the reading was taken, in {@code Timer.getFPGATimestamp()} seconds (already latency-corrected). */
    public final double timestampSeconds;
    /** The sensor's own error estimate, meters. */
    public final double errorMeters;

    public EstimatedRobotPose(Pose2d estimatedPose, double timestampSeconds, double errorMeters) {
        this.estimatedPose = estimatedPose;
        this.timestampSeconds = timestampSeconds;
        this.errorMeters = errorMeters;
    }

    @Override
    public String toString() {
        return "EstimatedRobotPose(" + estimatedPose + ", t=" + timestampSeconds + ", error=" + errorMeters + " m)";
    }
}
