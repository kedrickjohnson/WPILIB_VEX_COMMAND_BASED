package com.robojacks.lib.estimator;

import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Twist2d;
import com.robojacks.lib.math.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 * Blends odometry with absolute position measurements (the GPS Sensor), like WPILib's pose
 * estimators. Odometry is smooth but drifts; the GPS doesn't drift but is noisy and sometimes
 * blind. The estimate follows odometry and is pulled toward each measurement by an amount that
 * depends on how much you trust each one (the standard deviations).
 *
 * <p>Use {@link DifferentialDrivePoseEstimator} or {@link TrackingWheelPoseEstimator}; this class
 * holds the shared fusion logic.
 *
 * <p>Measurements can be late (a camera frame from 40 ms ago): pass the time the measurement was
 * taken, and the estimator corrects the pose it had at that time and replays odometry since.
 */
public abstract class PoseEstimator {
    /** How far back measurements can be applied, seconds. */
    private static final double kBufferSeconds = 0.75;

    private static final class Sample {
        final double time;
        final Pose2d odometry;
        Pose2d estimate;

        Sample(double time, Pose2d odometry, Pose2d estimate) {
            this.time = time;
            this.odometry = odometry;
            this.estimate = estimate;
        }
    }

    private final List<Sample> buffer = new ArrayList<>();
    private final double[] stateVariance = new double[3];
    private final double[] gain = new double[3];
    private Pose2d estimate;
    private Pose2d lastOdometry;

    /**
     * @param initialPose      where the robot starts
     * @param stateStdDevs     how much odometry drifts per update: x and y in meters, heading in
     *                         radians (WPILib's default is 0.02, 0.02, 0.01)
     * @param measurementStdDevs how noisy measurements are: x, y in meters, heading in radians
     */
    protected PoseEstimator(Pose2d initialPose, double[] stateStdDevs, double[] measurementStdDevs) {
        estimate = initialPose;
        lastOdometry = initialPose;
        for (int i = 0; i < 3; i++) {
            stateVariance[i] = stateStdDevs[i] * stateStdDevs[i];
        }
        setVisionMeasurementStdDevs(measurementStdDevs[0], measurementStdDevs[1], measurementStdDevs[2]);
    }

    /**
     * How much to trust measurements by default: standard deviations of x and y (meters) and
     * heading (radians). Larger = trust less. For the GPS, a huge heading value (like 1000) keeps
     * the Inertial Sensor in charge of heading.
     */
    public final void setVisionMeasurementStdDevs(double x, double y, double theta) {
        double[] r = { x * x, y * y, theta * theta };
        for (int i = 0; i < 3; i++) {
            gain[i] = gainFor(stateVariance[i], r[i]);
        }
    }

    /** WPILib's form: {@code setVisionMeasurementStdDevs(VecBuilder.fill(x, y, theta))}. */
    public final void setVisionMeasurementStdDevs(Vector3 stdDevs) {
        setVisionMeasurementStdDevs(stdDevs.get(0), stdDevs.get(1), stdDevs.get(2));
    }

    /** Steady-state Kalman gain for one axis, the same formula WPILib uses. */
    private static double gainFor(double q, double r) {
        if (q == 0.0) {
            return 0.0;
        }
        return q / (q + Math.sqrt(q * r));
    }

    /** The best estimate of where the robot is now. */
    public Pose2d getEstimatedPosition() {
        return estimate;
    }

    /** Called by subclasses each time odometry produces a new pose. */
    protected final Pose2d addOdometry(double time, Pose2d odometryPose) {
        estimate = estimate.exp(lastOdometry.log(odometryPose));
        lastOdometry = odometryPose;
        buffer.add(new Sample(time, odometryPose, estimate));
        while (!buffer.isEmpty() && buffer.get(0).time < time - kBufferSeconds) {
            buffer.remove(0);
        }
        return estimate;
    }

    /** Called by subclasses when the robot is teleported (odometry reset). */
    protected final void resetEstimate(Pose2d odometryPose, Pose2d pose) {
        buffer.clear();
        estimate = pose;
        lastOdometry = odometryPose;
    }

    /**
     * Adds an absolute measurement of the robot's pose, using the default standard deviations.
     *
     * @param measuredPose where the sensor says the robot is
     * @param timestamp    when the measurement was taken ({@code Timer.getFPGATimestamp()} if it's
     *                     current)
     */
    public void addVisionMeasurement(Pose2d measuredPose, double timestamp) {
        applyMeasurement(measuredPose, timestamp, gain);
    }

    /** Adds a measurement with its own standard deviations (e.g. scaled by the GPS's reported error). */
    public void addVisionMeasurement(Pose2d measuredPose, double timestamp, double xStdDev, double yStdDev, double thetaStdDev) {
        double[] k = {
                gainFor(stateVariance[0], xStdDev * xStdDev),
                gainFor(stateVariance[1], yStdDev * yStdDev),
                gainFor(stateVariance[2], thetaStdDev * thetaStdDev),
        };
        applyMeasurement(measuredPose, timestamp, k);
    }

    /** WPILib's form, as used with PhotonVision: {@code addVisionMeasurement(pose, timestamp, stdDevs)}. */
    public void addVisionMeasurement(Pose2d measuredPose, double timestamp, Vector3 stdDevs) {
        addVisionMeasurement(measuredPose, timestamp, stdDevs.get(0), stdDevs.get(1), stdDevs.get(2));
    }

    private void applyMeasurement(Pose2d measured, double timestamp, double[] k) {
        if (buffer.isEmpty() || timestamp < buffer.get(0).time) {
            return; // older than our history: can't place it
        }
        // Find the samples around the measurement time.
        int after = buffer.size();
        for (int i = 0; i < buffer.size(); i++) {
            if (buffer.get(i).time >= timestamp) {
                after = i;
                break;
            }
        }
        Pose2d odomAt;
        Pose2d estAt;
        if (after == buffer.size()) {
            Sample last = buffer.get(buffer.size() - 1);
            odomAt = last.odometry;
            estAt = last.estimate;
        } else if (after == 0 || buffer.get(after).time == timestamp) {
            odomAt = buffer.get(after).odometry;
            estAt = buffer.get(after).estimate;
        } else {
            Sample a = buffer.get(after - 1);
            Sample b = buffer.get(after);
            double t = (timestamp - a.time) / (b.time - a.time);
            odomAt = a.odometry.interpolate(b.odometry, t);
            estAt = a.estimate.interpolate(b.estimate, t);
        }

        // Pull the old estimate toward the measurement.
        Twist2d error = estAt.log(measured);
        Pose2d corrected = estAt.exp(new Twist2d(k[0] * error.dx, k[1] * error.dy, k[2] * error.dtheta));

        // Replay odometry from then to now on top of the corrected pose.
        for (int i = after; i < buffer.size(); i++) {
            Sample s = buffer.get(i);
            s.estimate = corrected.exp(odomAt.log(s.odometry));
        }
        estimate = corrected.exp(odomAt.log(lastOdometry));
    }
}
