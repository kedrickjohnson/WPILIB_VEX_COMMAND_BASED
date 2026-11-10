package com.robojacks.lib.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.estimator.DifferentialDrivePoseEstimator;
import com.robojacks.lib.hardware.VexGps;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Vector3;
import java.util.Optional;
import org.junit.Test;

public class GpsPoseEstimatorTest extends SimTestBase {
    @Test
    public void filtersAndRateLimits() {
        GpsPoseEstimator est = new GpsPoseEstimator(new VexGps(12));
        sim.setTruePose(new Pose2d(0.5, -0.4, Rotation2d.fromDegrees(30)));
        Optional<EstimatedRobotPose> first = est.update();
        assertTrue(first.isPresent());
        assertEquals(0.5, first.get().estimatedPose.getX(), 1e-9);
        assertEquals(sim.clock().nowSeconds() - 0.020, first.get().timestampSeconds, 1e-9);
        assertFalse("rate-limited", est.update().isPresent());
        run(0.06);
        assertTrue(est.update().isPresent());

        run(0.06);
        sim.getGps(12).errorMeters = 0.3; // can't see the strip well
        assertFalse(est.update().isPresent());

        sim.getGps(12).errorMeters = 0.03;
        Vector3 std = est.update().isPresent() ? est.getEstimationStdDevs() : null;
        assertEquals(0.06, std.get(0), 1e-9);     // 2 x reported error
        assertTrue(std.get(2) > 1000);            // heading ignored by default
    }

    @Test
    public void correctsDriftingOdometry() {
        // Odometry thinks the robot drives 1.0 m but wheels slipped: the real robot went 0.8 m.
        DifferentialDrivePoseEstimator drive = new DifferentialDrivePoseEstimator(Rotation2d.kZero, 0, 0, Pose2d.kZero);
        GpsPoseEstimator vision = new GpsPoseEstimator(new VexGps(13));
        EstimateConsumer consumer = drive::addVisionMeasurement;
        for (int i = 1; i <= 300; i++) {
            run(0.010);
            double odom = Math.min(1.0, i * 0.01);
            sim.setTruePose(new Pose2d(odom * 0.8, 0, Rotation2d.kZero));
            drive.update(Rotation2d.kZero, odom, odom);
            vision.update().ifPresent(e -> consumer.accept(e.estimatedPose, e.timestampSeconds, vision.getEstimationStdDevs()));
        }
        assertEquals(0.8, drive.getEstimatedPosition().getX(), 0.02);
        assertEquals(1.0, drive.getOdometryPose().getX(), 1e-9);
    }
}
