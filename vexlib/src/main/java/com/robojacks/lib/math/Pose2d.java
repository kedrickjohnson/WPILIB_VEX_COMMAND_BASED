package com.robojacks.lib.math;

/** A position and heading on the field. Meters + Rotation2d. Immutable. */
public final class Pose2d {
    public static final Pose2d kZero = new Pose2d();

    private final Translation2d translation;
    private final Rotation2d rotation;

    public Pose2d() {
        this(Translation2d.kZero, Rotation2d.kZero);
    }

    public Pose2d(Translation2d translation, Rotation2d rotation) {
        this.translation = translation;
        this.rotation = rotation;
    }

    public Pose2d(double x, double y, Rotation2d rotation) {
        this(new Translation2d(x, y), rotation);
    }

    public Translation2d getTranslation() {
        return translation;
    }

    public Rotation2d getRotation() {
        return rotation;
    }

    public double getX() {
        return translation.getX();
    }

    public double getY() {
        return translation.getY();
    }

    /** Returns this pose expressed in the frame of {@code other}. */
    public Pose2d relativeTo(Pose2d other) {
        Translation2d t = translation.minus(other.translation).rotateBy(other.rotation.unaryMinus());
        return new Pose2d(t, rotation.minus(other.rotation));
    }

    /**
     * Moves the pose along a constant-curvature arc described by a twist
     * (robot-relative). This is the same math as WPILib's Pose2d.exp().
     */
    public Pose2d exp(Twist2d twist) {
        double dx = twist.dx;
        double dy = twist.dy;
        double dtheta = twist.dtheta;

        double sinTheta = Math.sin(dtheta);
        double cosTheta = Math.cos(dtheta);

        double s;
        double c;
        if (Math.abs(dtheta) < 1e-9) {
            s = 1.0 - dtheta * dtheta / 6.0;
            c = 0.5 * dtheta;
        } else {
            s = sinTheta / dtheta;
            c = (1.0 - cosTheta) / dtheta;
        }

        Translation2d deltaRobot = new Translation2d(dx * s - dy * c, dx * c + dy * s);
        Rotation2d deltaRot = new Rotation2d(cosTheta, sinTheta);

        return new Pose2d(
                translation.plus(deltaRobot.rotateBy(rotation)),
                rotation.plus(deltaRot));
    }


    /**
     * The twist that takes this pose to {@code end}: the inverse of {@link #exp}. Same math as WPILib.
     */
    public Twist2d log(Pose2d end) {
        Pose2d transform = end.relativeTo(this);
        double dtheta = transform.getRotation().getRadians();
        double halfDtheta = dtheta / 2.0;
        double cosMinusOne = transform.getRotation().getCos() - 1.0;
        double halfThetaByTanOfHalfDtheta;
        if (Math.abs(cosMinusOne) < 1e-9) {
            halfThetaByTanOfHalfDtheta = 1.0 - 1.0 / 12.0 * dtheta * dtheta;
        } else {
            halfThetaByTanOfHalfDtheta = -(halfDtheta * transform.getRotation().getSin()) / cosMinusOne;
        }
        Translation2d part = transform.getTranslation()
                .rotateBy(new Rotation2d(halfThetaByTanOfHalfDtheta, -halfDtheta))
                .times(Math.hypot(halfThetaByTanOfHalfDtheta, halfDtheta));
        return new Twist2d(part.getX(), part.getY(), dtheta);
    }

    /** A pose part way to {@code end} along a constant-curvature arc: t = 0 is this pose, 1 is end. */
    public Pose2d interpolate(Pose2d end, double t) {
        if (t <= 0) {
            return this;
        }
        if (t >= 1) {
            return end;
        }
        Twist2d twist = log(end);
        return exp(new Twist2d(twist.dx * t, twist.dy * t, twist.dtheta * t));
    }

    /** Moves this pose by a transform expressed in this pose's own frame (WPILib {@code transformBy}). */
    public Pose2d plus(Transform2d transform) {
        return new Pose2d(
                getTranslation().plus(transform.getTranslation().rotateBy(getRotation())),
                getRotation().plus(transform.getRotation()));
    }

    /** The transform that takes {@code other} to this pose, in other's frame. */
    public Transform2d minus(Pose2d other) {
        Pose2d rel = relativeTo(other);
        return new Transform2d(rel.getTranslation(), rel.getRotation());
    }

    @Override
    public String toString() {
        return "Pose2d(" + Rotation2d.round(getX()) + ", " + Rotation2d.round(getY())
                + ", " + Rotation2d.round(rotation.getDegrees()) + " deg)";
    }
}
