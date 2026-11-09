package com.robojacks.lib.trajectory;

import com.robojacks.lib.math.Pose2d;

/**
 * Path-tracking controller for tank drives (same math as WPILib's RamseteController).
 * Given where the robot is and where the trajectory says it should be, it returns the forward
 * speed and turn rate that pull the robot back onto the path.
 */
public class RamseteController {
    private final double b;
    private final double zeta;

    /** Robot command: forward speed (m/s) and turn rate (rad/s, CCW+). */
    public static final class Output {
        public final double velocity;
        public final double angularVelocity;

        Output(double velocity, double angularVelocity) {
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
        }
    }

    /**
     * @param b    aggressiveness (larger corrects position errors harder), default 2.0
     * @param zeta damping, 0..1, default 0.7
     */
    public RamseteController(double b, double zeta) {
        this.b = b;
        this.zeta = zeta;
    }

    public RamseteController() {
        this(2.0, 0.7);
    }

    public Output calculate(Pose2d current, Pose2d desired, double desiredVelocity, double desiredAngularVelocity) {
        Pose2d error = desired.relativeTo(current);
        double ex = error.getX();
        double ey = error.getY();
        double eTheta = error.getRotation().getRadians();
        double vd = desiredVelocity;
        double wd = desiredAngularVelocity;

        double k = 2.0 * zeta * Math.sqrt(wd * wd + b * vd * vd);
        double v = vd * Math.cos(eTheta) + k * ex;
        double w = wd + k * eTheta + b * vd * sinc(eTheta) * ey;
        return new Output(v, w);
    }

    private static double sinc(double x) {
        return Math.abs(x) < 1e-9 ? 1.0 - x * x / 6.0 : Math.sin(x) / x;
    }
}
