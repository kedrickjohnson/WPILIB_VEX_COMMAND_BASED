package com.robojacks.robot;

import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;

/**
 * FIELD COORDINATE CONVENTION — read this before writing any auto.
 *
 * <ul>
 *   <li>Origin (0, 0) is the CENTER of the field, the same as the VEX GPS sensor and FTC.</li>
 *   <li>X and Y axes are the same directions as the VEX GPS axes, so GPS x/y map straight in.</li>
 *   <li>Units are METERS internally. The field spans about -1.8 m to +1.8 m on each axis.</li>
 *   <li>Heading is WPILib-style: 0 points along +X, COUNTER-CLOCKWISE is positive, radians internally.</li>
 * </ul>
 *
 * Mirroring for the other side of a rotationally-symmetric field is just a 180 degree
 * rotation about the origin: (x, y, theta) -> (-x, -y, theta + 180).
 */
public final class FieldConstants {
    private FieldConstants() {}

    /** VEX field is 12 ft (about 3.66 m) square. */
    public static final double FIELD_SIZE_M = Units.feetToMeters(12);
    public static final double HALF_FIELD_M = FIELD_SIZE_M / 2.0;

    /**
     * GPS heading is compass-style (degrees, clockwise-positive). This offset converts it to our
     * CCW-from-+X convention: ourDeg = GPS_HEADING_OFFSET_DEG - gpsHeadingDeg.
     * TODO: VERIFY on a real field: point the robot along +X, read GPS heading, adjust until
     * fromGps(...) reports 0 degrees.
     */
    public static final double GPS_HEADING_OFFSET_DEG = 90.0;

    /** Converts a GPS reading (mm, mm, degrees CW) into our field Pose2d. */
    public static Pose2d fromGps(double xMm, double yMm, double headingDegCW) {
        return new Pose2d(Units.millimetersToMeters(xMm), Units.millimetersToMeters(yMm),
                Rotation2d.fromDegrees(GPS_HEADING_OFFSET_DEG - headingDegCW));
    }

    /** Rotates a pose 180 degrees about the field center (use for the opposite starting side). */
    public static Pose2d mirror(Pose2d pose) {
        return new Pose2d(pose.getTranslation().unaryMinus(), pose.getRotation().plus(Rotation2d.k180deg));
    }

    public static Translation2d mirror(Translation2d point) {
        return point.unaryMinus();
    }

    /** True if a point is on the field (with a margin for the robot's half-width). */
    public static boolean isOnField(Translation2d p, double marginM) {
        double lim = HALF_FIELD_M - marginM;
        return Math.abs(p.getX()) <= lim && Math.abs(p.getY()) <= lim;
    }

    // ----- Named poses for autos. Edit these each season. -----
    public static final class StartPoses {
        /** Example: back corner, facing +X toward the field center line. */
        public static final Pose2d LEFT_START = new Pose2d(-1.5, 0.6, Rotation2d.kZero);
        public static final Pose2d RIGHT_START = new Pose2d(-1.5, -0.6, Rotation2d.kZero);
    }
}
