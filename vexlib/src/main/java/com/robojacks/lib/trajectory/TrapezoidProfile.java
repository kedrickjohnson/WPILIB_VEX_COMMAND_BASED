package com.robojacks.lib.trajectory;

/**
 * A motion profile that accelerates at a constant rate, cruises, then decelerates, like WPILib's
 * TrapezoidProfile. It turns "go to 90 degrees" into a smooth series of setpoints a PID
 * controller can follow without jerking the mechanism.
 *
 * <pre>{@code
 * TrapezoidProfile profile = new TrapezoidProfile(new TrapezoidProfile.Constraints(2.0, 4.0));
 * TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
 * TrapezoidProfile.State goal = new TrapezoidProfile.State(1.2, 0); // 1.2 m, stopped
 *
 * // every loop:
 * setpoint = profile.calculate(0.010, setpoint, goal);
 * }</pre>
 *
 * Most code uses {@link com.robojacks.lib.controller.ProfiledPIDController}, which does this for you.
 */
public class TrapezoidProfile {
    /** Speed and acceleration limits, in your units per second and per second squared. */
    public static final class Constraints {
        public final double maxVelocity;
        public final double maxAcceleration;

        public Constraints(double maxVelocity, double maxAcceleration) {
            if (maxVelocity <= 0 || maxAcceleration <= 0) {
                throw new IllegalArgumentException("Constraints must be positive");
            }
            this.maxVelocity = maxVelocity;
            this.maxAcceleration = maxAcceleration;
        }
    }

    /** A position and velocity. */
    public static final class State {
        public final double position;
        public final double velocity;

        public State(double position, double velocity) {
            this.position = position;
            this.velocity = velocity;
        }

        public State() {
            this(0.0, 0.0);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof State && ((State) o).position == position && ((State) o).velocity == velocity;
        }

        @Override
        public int hashCode() {
            return Double.hashCode(position) * 31 + Double.hashCode(velocity);
        }

        @Override
        public String toString() {
            return "State(position=" + position + ", velocity=" + velocity + ")";
        }
    }

    private final Constraints constraints;
    private double totalTime;

    public TrapezoidProfile(Constraints constraints) {
        this.constraints = constraints;
    }

    /**
     * Where the profile is {@code t} seconds after {@code current}, heading for {@code goal}.
     * Call it every loop with t = the loop period and the previous result as current.
     */
    public State calculate(double t, State current, State goal) {
        // Work in the direction of travel so the math is for a positive move.
        boolean flip = current.position > goal.position;
        double dir = flip ? -1.0 : 1.0;
        double p0 = current.position * dir;
        double v0 = current.velocity * dir;
        double pg = goal.position * dir;
        double vg = goal.velocity * dir;
        double vmax = constraints.maxVelocity;
        double a = constraints.maxAcceleration;

        // Start with a profile that can begin and end at the given speeds (clamped to vmax).
        v0 = Math.max(-vmax, Math.min(vmax, v0));
        vg = Math.max(-vmax, Math.min(vmax, vg));

        // Distances to accelerate from v0 to vmax and decelerate from vmax to vg
        double cutoffBegin = v0 / a;
        double cutoffDistBegin = cutoffBegin * cutoffBegin * a / 2.0;
        double cutoffEnd = vg / a;
        double cutoffDistEnd = cutoffEnd * cutoffEnd * a / 2.0;

        // Treat the move as if it started and ended at rest, lengthened by the cutoffs (WPILib's approach).
        double fullTrapezoidDist = cutoffDistBegin + (pg - p0) + cutoffDistEnd;
        double accelerationTime = vmax / a;
        double fullSpeedDist = fullTrapezoidDist - accelerationTime * accelerationTime * a;

        if (fullSpeedDist < 0) { // never reaches vmax: triangle
            accelerationTime = Math.sqrt(fullTrapezoidDist / a);
            fullSpeedDist = 0;
        }
        double endAccel = accelerationTime - cutoffBegin;
        double endFullSpeed = endAccel + fullSpeedDist / vmax;
        double endDecel = endFullSpeed + accelerationTime - cutoffEnd;
        totalTime = endDecel;

        double pos;
        double vel;
        if (t < endAccel) {
            vel = v0 + t * a;
            pos = p0 + (v0 + t * a / 2.0) * t;
        } else if (t < endFullSpeed) {
            vel = vmax;
            pos = p0 + (v0 + endAccel * a / 2.0) * endAccel + vmax * (t - endAccel);
        } else if (t <= endDecel) {
            double timeLeft = endDecel - t;
            vel = vg + timeLeft * a;
            pos = pg - (vg + timeLeft * a / 2.0) * timeLeft;
        } else {
            vel = vg;
            pos = pg;
        }
        return new State(pos * dir, vel * dir);
    }

    /** Total time of the profile computed by the last {@link #calculate} call. */
    public double totalTime() {
        return totalTime;
    }

    /** True once {@code t} is past the end of the last computed profile. */
    public boolean isFinished(double t) {
        return t >= totalTime;
    }
}
