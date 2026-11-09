package com.robojacks.lib.math;

/** A change in pose along an arc: forward (dx), sideways (dy) and rotation (dtheta, radians). */
public final class Twist2d {
    public final double dx;
    public final double dy;
    public final double dtheta;

    public Twist2d(double dx, double dy, double dtheta) {
        this.dx = dx;
        this.dy = dy;
        this.dtheta = dtheta;
    }
}
