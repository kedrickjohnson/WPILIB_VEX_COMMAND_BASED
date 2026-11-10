package com.robojacks.lib.math;

/**
 * A move and turn expressed in a robot's own frame (x forward, y left), like WPILib's
 * Transform2d. Typical use: where a sensor sits on the robot.
 *
 * <pre>{@code
 * // GPS mounted 10 cm behind the robot's center, facing forward
 * Transform2d robotToGps = new Transform2d(new Translation2d(-0.10, 0.0), Rotation2d.kZero);
 * Pose2d gpsPose = robotPose.plus(robotToGps);
 * }</pre>
 */
public final class Transform2d {
    private final Translation2d translation;
    private final Rotation2d rotation;

    public Transform2d(Translation2d translation, Rotation2d rotation) {
        this.translation = translation;
        this.rotation = rotation;
    }

    public Transform2d(double x, double y, Rotation2d rotation) {
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

    /** The transform that undoes this one. */
    public Transform2d inverse() {
        return new Transform2d(translation.unaryMinus().rotateBy(rotation.unaryMinus()), rotation.unaryMinus());
    }

    @Override
    public String toString() {
        return "Transform2d(" + translation + ", " + rotation + ")";
    }
}
