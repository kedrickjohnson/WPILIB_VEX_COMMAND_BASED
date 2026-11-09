package com.robojacks.lib.math;

/** A 2D point / vector in meters. Immutable. */
public final class Translation2d {
    public static final Translation2d kZero = new Translation2d(0.0, 0.0);

    private final double x;
    private final double y;

    public Translation2d(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** Polar constructor: distance along an angle. */
    public Translation2d(double distance, Rotation2d angle) {
        this(distance * angle.getCos(), distance * angle.getSin());
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getNorm() {
        return Math.hypot(x, y);
    }

    public Rotation2d getAngle() {
        return new Rotation2d(x, y);
    }

    public double getDistance(Translation2d other) {
        return Math.hypot(other.x - x, other.y - y);
    }

    public Translation2d plus(Translation2d other) {
        return new Translation2d(x + other.x, y + other.y);
    }

    public Translation2d minus(Translation2d other) {
        return new Translation2d(x - other.x, y - other.y);
    }

    public Translation2d times(double scalar) {
        return new Translation2d(x * scalar, y * scalar);
    }

    public Translation2d unaryMinus() {
        return new Translation2d(-x, -y);
    }

    public Translation2d rotateBy(Rotation2d rot) {
        return new Translation2d(
                x * rot.getCos() - y * rot.getSin(),
                x * rot.getSin() + y * rot.getCos());
    }

    @Override
    public String toString() {
        return "Translation2d(" + Rotation2d.round(x) + ", " + Rotation2d.round(y) + ")";
    }
}
