package com.robojacks.lib.vision;

import static org.junit.Assert.assertEquals;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.hardware.VexGps;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Transform2d;
import com.robojacks.lib.math.Translation2d;
import org.junit.Test;

/** The GPS mounting transform follows WPILib's robot frame: +X forward, +Y left, CCW rotation. */
public class GpsMountingTest extends SimTestBase {
    private static void assertPose(Pose2d expected, Pose2d actual) {
        assertEquals("x", expected.getX(), actual.getX(), 1e-9);
        assertEquals("y", expected.getY(), actual.getY(), 1e-9);
        assertEquals("heading", 0.0, expected.getRotation().minus(actual.getRotation()).getRadians(), 1e-9);
    }

    @Test
    public void backwardFacingGpsBehindCenter() {
        Transform2d robotToGps = new Transform2d(new Translation2d(-0.10, 0.0), Rotation2d.fromDegrees(180));
        VexGps gps = new VexGps(1, robotToGps);
        Pose2d robot = new Pose2d(0.5, 0.2, Rotation2d.fromDegrees(0)); // facing +X
        sim.setTruePose(robot);

        // The sensor itself is 10 cm behind the robot (toward -X), facing -X
        assertPose(new Pose2d(0.4, 0.2, Rotation2d.fromDegrees(180)), gps.getSensorPose());
        // ...and the library gives back the robot's center
        assertPose(robot, gps.getPose());
    }

    @Test
    public void sideFacingGpsOffCenterOnATurnedRobot() {
        // 5 cm forward, 12 cm to the RIGHT (negative Y), camera facing the robot's right side
        Transform2d robotToGps = new Transform2d(new Translation2d(0.05, -0.12), Rotation2d.fromDegrees(-90));
        VexGps gps = new VexGps(2, robotToGps);
        Pose2d robot = new Pose2d(-1.0, 0.7, Rotation2d.fromDegrees(90)); // facing +Y
        sim.setTruePose(robot);

        // Robot faces +Y, so its front is +Y and its right side is +X:
        // sensor at (-1.0 + 0.12, 0.7 + 0.05), camera facing +X (heading 0)
        assertPose(new Pose2d(-0.88, 0.75, Rotation2d.fromDegrees(0)), gps.getSensorPose());
        assertPose(robot, gps.getPose());
    }

    @Test
    public void estimatorTakesTheTransformLikePhotonPoseEstimator() {
        Transform2d robotToGps = new Transform2d(new Translation2d(0.0, 0.08), Rotation2d.fromDegrees(90));
        VexGps gps = new VexGps(3);
        GpsPoseEstimator estimator = new GpsPoseEstimator(gps, robotToGps);
        assertEquals(robotToGps, estimator.getRobotToCameraTransform());
        Pose2d robot = new Pose2d(1.1, -0.3, Rotation2d.fromDegrees(-30));
        sim.setTruePose(robot);
        assertPose(robot, estimator.update().get().estimatedPose);
    }

    @Test
    public void wrongTransformInCodeShowsUpAsAnOffset() {
        // The real sensor is 20 cm behind center, but the code says it's at center.
        VexGps gps = new VexGps(4);
        sim.getGps(4).setMounting(new Transform2d(new Translation2d(-0.20, 0.0), Rotation2d.kZero));
        sim.setTruePose(new Pose2d(0.0, 0.0, Rotation2d.kZero));
        assertEquals(-0.20, gps.getPose().getX(), 1e-9);
    }
}
