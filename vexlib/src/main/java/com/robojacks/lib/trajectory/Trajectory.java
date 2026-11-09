package com.robojacks.lib.trajectory;

import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;

/**
 * A time-stamped path for a tank drive: where the robot should be, which way it should face
 * and how fast it should go, every {@code dt} seconds. The path planner generates these on your
 * computer, so the brain only has to look up the next sample.
 *
 * <p>Samples are stored flat: x, y (meters), heading (radians, CCW+), velocity (m/s, negative
 * when driving backward), angular velocity (rad/s, CCW+).
 */
public final class Trajectory {
    private static final int STRIDE = 5;

    private final double dt;
    private final double[] data;
    private final int count;

    /** One instant along the trajectory. */
    public static final class State {
        public final double timeSeconds;
        public final Pose2d pose;
        public final double velocity;
        public final double angularVelocity;

        public State(double timeSeconds, Pose2d pose, double velocity, double angularVelocity) {
            this.timeSeconds = timeSeconds;
            this.pose = pose;
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
        }
    }

    public Trajectory(double dt, double[] data) {
        if (dt <= 0 || data.length < STRIDE || data.length % STRIDE != 0) {
            throw new IllegalArgumentException("Trajectory data must be groups of 5 numbers");
        }
        this.dt = dt;
        this.data = data;
        this.count = data.length / STRIDE;
    }

    /**
     * Reads the compact text format the path planner exports: numbers separated by spaces,
     * five per sample. Using a string keeps big paths out of Java's 64 KB method limit.
     */
    public static Trajectory parse(double dt, String samples) {
        String s = samples.trim();
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                n++;
            }
        }
        double[] out = new double[n];
        int start = 0;
        int k = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == ' ') {
                out[k++] = Double.parseDouble(s.substring(start, i));
                start = i + 1;
            }
        }
        return new Trajectory(dt, out);
    }

    public double getTotalTimeSeconds() {
        return (count - 1) * dt;
    }

    public int size() {
        return count;
    }

    public State getInitialState() {
        return state(0, 0.0);
    }

    public State getFinalState() {
        return state(count - 1, getTotalTimeSeconds());
    }

    public Pose2d getInitialPose() {
        return getInitialState().pose;
    }

    private State state(int i, double t) {
        int b = i * STRIDE;
        return new State(t, new Pose2d(data[b], data[b + 1], new Rotation2d(data[b + 2])), data[b + 3], data[b + 4]);
    }

    /** The state at time t, linearly interpolated between samples (clamped to the ends). */
    public State sample(double t) {
        if (t <= 0) {
            return getInitialState();
        }
        if (t >= getTotalTimeSeconds()) {
            return getFinalState();
        }
        double f = t / dt;
        int i = (int) Math.floor(f);
        double u = f - i;
        int a = i * STRIDE;
        int b = (i + 1) * STRIDE;
        double x = data[a] + (data[b] - data[a]) * u;
        double y = data[a + 1] + (data[b + 1] - data[a + 1]) * u;
        Rotation2d h0 = new Rotation2d(data[a + 2]);
        Rotation2d dh = new Rotation2d(data[b + 2]).minus(h0);
        double heading = h0.getRadians() + dh.getRadians() * u;
        double v = data[a + 3] + (data[b + 3] - data[a + 3]) * u;
        double w = data[a + 4] + (data[b + 4] - data[a + 4]) * u;
        return new State(t, new Pose2d(x, y, new Rotation2d(heading)), v, w);
    }
}
