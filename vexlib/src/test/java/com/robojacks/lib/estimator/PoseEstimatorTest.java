package com.robojacks.lib.estimator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import org.junit.Test;

public class PoseEstimatorTest extends SimTestBase {
    @Test
    public void followsOdometryWithoutMeasurements() {
        DifferentialDrivePoseEstimator est = new DifferentialDrivePoseEstimator(Rotation2d.kZero, 0, 0, Pose2d.kZero);
        for (int i = 1; i <= 100; i++) {
            run(0.010);
            est.update(Rotation2d.kZero, i * 0.01, i * 0.01);
        }
        assertEquals(1.0, est.getEstimatedPosition().getX(), 1e-9);
    }

    @Test
    public void measurementsPullTheEstimateToTheTruth() {
        // Odometry thinks the robot is still at the origin; the GPS keeps saying (0.5, 0).
        DifferentialDrivePoseEstimator est = new DifferentialDrivePoseEstimator(Rotation2d.kZero, 0, 0, Pose2d.kZero);
        Pose2d gps = new Pose2d(0.5, 0.0, Rotation2d.kZero);
        double firstStep = 0;
        for (int i = 0; i < 200; i++) {
            run(0.010);
            est.update(Rotation2d.kZero, 0, 0);
            est.addVisionMeasurement(gps, sim.clock().nowSeconds(), 0.05, 0.05, 1000);
            if (i == 0) {
                firstStep = est.getEstimatedPosition().getX();
            }
        }
        assertTrue("one reading moves it only part way", firstStep > 0 && firstStep < 0.5);
        assertEquals(0.5, est.getEstimatedPosition().getX(), 0.01);
        assertEquals(0.0, est.getEstimatedPosition().getRotation().getDegrees(), 1e-6); // heading ignored
    }

    @Test
    public void lateMeasurementIsAppliedAtItsTime() {
        DifferentialDrivePoseEstimator est = new DifferentialDrivePoseEstimator(Rotation2d.kZero, 0, 0, Pose2d.kZero,
                new double[] {0.02, 0.02, 0.01}, new double[] {1e-6, 1e-6, 1e6}); // trust the measurement fully
        double measuredAt = 0;
        for (int i = 1; i <= 50; i++) {
            run(0.010);
            est.update(Rotation2d.kZero, i * 0.01, i * 0.01); // drives to x = 0.5
            if (i == 20) {
                measuredAt = sim.clock().nowSeconds(); // odometry said 0.2 here
            }
        }
        // The sensor, 0.3 s late, reports the robot was really at 0.25 then: so it's at 0.55 now.
        est.addVisionMeasurement(new Pose2d(0.25, 0, Rotation2d.kZero), measuredAt);
        assertEquals(0.55, est.getEstimatedPosition().getX(), 1e-3);
    }

    @Test
    public void resetTeleports() {
        TrackingWheelPoseEstimator est = new TrackingWheelPoseEstimator(0, 0, Rotation2d.kZero, 0, 0, Pose2d.kZero);
        est.resetPosition(Rotation2d.kZero, 0, 0, new Pose2d(1, 1, Rotation2d.fromDegrees(90)));
        assertEquals(1.0, est.getEstimatedPosition().getX(), 1e-9);
        assertEquals(90.0, est.getEstimatedPosition().getRotation().getDegrees(), 1e-9);
    }
}
