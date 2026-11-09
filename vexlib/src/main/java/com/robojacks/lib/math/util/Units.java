package com.robojacks.lib.math.util;

/**
 * Unit conversions, same names as WPILib's {@code edu.wpi.first.math.util.Units}, plus a few
 * VEX ones (field tiles, GPS millimeters, millivolts, milliamps).
 *
 * <p>The library works in meters, radians, seconds, volts and amps. Measure in whatever is
 * convenient and convert once, in {@code Constants}:
 *
 * <pre>{@code
 * public static final double kWheelDiameterMeters = Units.inchesToMeters(3.25);
 * public static final double kTurnToleranceRad = Units.degreesToRadians(2.0);
 * public static final double kStartX = Units.tilesToMeters(-2.5);
 * }</pre>
 */
public final class Units {
    private Units() {}

    private static final double kInchesPerFoot = 12.0;
    private static final double kMetersPerInch = 0.0254;
    private static final double kSecondsPerMinute = 60.0;
    private static final double kMillisecondsPerSecond = 1000.0;
    private static final double kKilogramsPerLb = 0.453592;
    /** A VRC field tile is 24 inches (nominal; real foam tiles are close to this). */
    private static final double kInchesPerTile = 24.0;

    // ---------------------------------------------------------------- length

    /** @return feet */
    public static double metersToFeet(double meters) {
        return metersToInches(meters) / kInchesPerFoot;
    }

    /** @return meters */
    public static double feetToMeters(double feet) {
        return inchesToMeters(feet * kInchesPerFoot);
    }

    /** @return inches */
    public static double metersToInches(double meters) {
        return meters / kMetersPerInch;
    }

    /** @return meters */
    public static double inchesToMeters(double inches) {
        return inches * kMetersPerInch;
    }

    /** The VEX GPS Sensor reports position in millimeters. @return meters */
    public static double millimetersToMeters(double millimeters) {
        return millimeters / 1000.0;
    }

    /** @return millimeters */
    public static double metersToMillimeters(double meters) {
        return meters * 1000.0;
    }

    /** Field tiles (24 in each) to meters. The field is 6 tiles across. @return meters */
    public static double tilesToMeters(double tiles) {
        return inchesToMeters(tiles * kInchesPerTile);
    }

    /** Meters to field tiles (24 in each). @return tiles */
    public static double metersToTiles(double meters) {
        return metersToInches(meters) / kInchesPerTile;
    }

    // ---------------------------------------------------------------- angle

    /** @return radians */
    public static double degreesToRadians(double degrees) {
        return Math.toRadians(degrees);
    }

    /** @return degrees */
    public static double radiansToDegrees(double radians) {
        return Math.toDegrees(radians);
    }

    /** @return rotations */
    public static double radiansToRotations(double radians) {
        return radians / (Math.PI * 2);
    }

    /** @return rotations */
    public static double degreesToRotations(double degrees) {
        return degrees / 360.0;
    }

    /** @return degrees */
    public static double rotationsToDegrees(double rotations) {
        return rotations * 360.0;
    }

    /** @return radians */
    public static double rotationsToRadians(double rotations) {
        return rotations * 2 * Math.PI;
    }

    // ---------------------------------------------------------------- angular velocity

    /** @return radians per second */
    public static double rotationsPerMinuteToRadiansPerSecond(double rpm) {
        return rpm * Math.PI / (kSecondsPerMinute / 2);
    }

    /** @return rotations per minute */
    public static double radiansPerSecondToRotationsPerMinute(double radiansPerSecond) {
        return radiansPerSecond * (kSecondsPerMinute / 2) / Math.PI;
    }

    // ---------------------------------------------------------------- time

    /** @return seconds */
    public static double millisecondsToSeconds(double milliseconds) {
        return milliseconds / kMillisecondsPerSecond;
    }

    /** @return milliseconds */
    public static double secondsToMilliseconds(double seconds) {
        return seconds * kMillisecondsPerSecond;
    }

    // ---------------------------------------------------------------- mass

    /** @return pounds */
    public static double kilogramsToLbs(double kilograms) {
        return kilograms / kKilogramsPerLb;
    }

    /** @return kilograms */
    public static double lbsToKilograms(double lbs) {
        return lbs * kKilogramsPerLb;
    }

    // ---------------------------------------------------------------- electrical (V5 SDK units)

    /** The V5 SDK takes voltage in millivolts (-12000 to 12000). @return volts */
    public static double millivoltsToVolts(double millivolts) {
        return millivolts / 1000.0;
    }

    /** @return millivolts */
    public static double voltsToMillivolts(double volts) {
        return volts * 1000.0;
    }

    /** The V5 SDK reports and limits current in milliamps (2500 mA max per motor). @return amps */
    public static double milliampsToAmps(double milliamps) {
        return milliamps / 1000.0;
    }

    /** @return milliamps */
    public static double ampsToMilliamps(double amps) {
        return amps * 1000.0;
    }
}
