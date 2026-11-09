package com.robojacks.lib.math;

/**
 * A 2D rotation. Counter-clockwise is positive, 0 points along +X (same as WPILib).
 * Immutable.
 */
public final class Rotation2d {
    public static final Rotation2d kZero = new Rotation2d(0.0);
    public static final Rotation2d k180deg = new Rotation2d(Math.PI);

    private final double radians;
    private final double cos;
    private final double sin;

    public Rotation2d(double radians) {
        this.radians = radians;
        this.cos = Math.cos(radians);
        this.sin = Math.sin(radians);
    }

    /** Builds a rotation from a direction vector (x, y). */
    public Rotation2d(double x, double y) {
        double mag = Math.hypot(x, y);
        if (mag > 1e-9) {
            this.cos = x / mag;
            this.sin = y / mag;
        } else {
            this.cos = 1.0;
            this.sin = 0.0;
        }
        this.radians = Math.atan2(this.sin, this.cos);
    }

    public static Rotation2d fromRadians(double radians) {
        return new Rotation2d(radians);
    }

    public static Rotation2d fromDegrees(double degrees) {
        return new Rotation2d(Math.toRadians(degrees));
    }

    public double getRadians() {
        return radians;
    }

    public double getDegrees() {
        return Math.toDegrees(radians);
    }

    public double getCos() {
        return cos;
    }

    public double getSin() {
        return sin;
    }

    public Rotation2d plus(Rotation2d other) {
        return rotateBy(other);
    }

    /** Shortest signed difference, wrapped to [-pi, pi). */
    public Rotation2d minus(Rotation2d other) {
        return rotateBy(other.unaryMinus());
    }

    public Rotation2d unaryMinus() {
        return new Rotation2d(-radians);
    }

    public Rotation2d rotateBy(Rotation2d other) {
        return new Rotation2d(
                cos * other.cos - sin * other.sin,
                cos * other.sin + sin * other.cos);
    }

    @Override
    public String toString() {
        return "Rotation2d(" + round(getDegrees()) + " deg)";
    }

    static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
