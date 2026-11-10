package com.robojacks.lib.math;

import static org.junit.Assert.assertEquals;

import com.robojacks.lib.math.util.Units;
import org.junit.Test;

public class GeometryTest {
    private static final double EPS = 1e-9;

    private static void assertPose(Pose2d expected, Pose2d actual, double eps) {
        assertEquals("x", expected.getX(), actual.getX(), eps);
        assertEquals("y", expected.getY(), actual.getY(), eps);
        assertEquals("heading", 0.0, expected.getRotation().minus(actual.getRotation()).getRadians(), eps);
    }

    @Test
    public void logUndoesExp() {
        Pose2d start = new Pose2d(1.0, -0.5, Rotation2d.fromDegrees(30));
        Twist2d twist = new Twist2d(0.7, 0.2, Math.toRadians(80));
        Pose2d end = start.exp(twist);
        Twist2d back = start.log(end);
        assertEquals(twist.dx, back.dx, EPS);
        assertEquals(twist.dy, back.dy, EPS);
        assertEquals(twist.dtheta, back.dtheta, EPS);
    }

    @Test
    public void straightLineTwist() {
        Pose2d end = Pose2d.kZero.exp(new Twist2d(2.0, 0.0, 0.0));
        assertPose(new Pose2d(2.0, 0.0, Rotation2d.kZero), end, EPS);
    }

    @Test
    public void quarterCircle() {
        // Arc of radius 1 turning left 90 degrees ends at (1, 1) facing +Y
        Pose2d end = Pose2d.kZero.exp(new Twist2d(Math.PI / 2, 0.0, Math.PI / 2));
        assertPose(new Pose2d(1.0, 1.0, Rotation2d.fromDegrees(90)), end, 1e-9);
    }

    @Test
    public void interpolateEndsAndMiddle() {
        Pose2d a = new Pose2d(0, 0, Rotation2d.kZero);
        Pose2d b = new Pose2d(2, 0, Rotation2d.kZero);
        assertPose(a, a.interpolate(b, 0), EPS);
        assertPose(b, a.interpolate(b, 1), EPS);
        assertPose(new Pose2d(1, 0, Rotation2d.kZero), a.interpolate(b, 0.5), EPS);
    }

    @Test
    public void transformRoundTrip() {
        Pose2d robot = new Pose2d(1, 2, Rotation2d.fromDegrees(90));
        Transform2d robotToSensor = new Transform2d(0.1, 0.0, Rotation2d.kZero);
        Pose2d sensor = robot.plus(robotToSensor);
        // 10 cm in front of a robot facing +Y is 10 cm up the field
        assertPose(new Pose2d(1, 2.1, Rotation2d.fromDegrees(90)), sensor, EPS);
        assertPose(robot, sensor.plus(robotToSensor.inverse()), EPS);
        Transform2d t = sensor.minus(robot);
        assertEquals(0.1, t.getX(), EPS);
        assertEquals(0.0, t.getY(), EPS);
    }

    @Test
    public void units() {
        assertEquals(0.0254, Units.inchesToMeters(1), EPS);
        assertEquals(3.6576, Units.feetToMeters(12), EPS);
        assertEquals(3.6576, Units.tilesToMeters(6), EPS);
        assertEquals(1.0, Units.metersToTiles(0.6096), EPS);
        assertEquals(Math.PI, Units.degreesToRadians(180), EPS);
        assertEquals(2 * Math.PI, Units.rotationsPerMinuteToRadiansPerSecond(60), EPS);
        assertEquals(60, Units.radiansPerSecondToRotationsPerMinute(2 * Math.PI), EPS);
        assertEquals(2.5, Units.milliampsToAmps(2500), EPS);
        assertEquals(1.8288, Units.millimetersToMeters(1828.8), EPS);
        assertEquals(1.0, Units.lbsToKilograms(Units.kilogramsToLbs(1.0)), EPS);
    }

    @Test
    public void angleModulusWraps() {
        assertEquals(-Math.PI / 2, MathUtil.angleModulus(3 * Math.PI / 2), EPS);
        assertEquals(0.1, MathUtil.applyDeadband(0.1, 0.0), EPS);
        assertEquals(0.0, MathUtil.applyDeadband(0.04, 0.05), EPS);
    }
}
