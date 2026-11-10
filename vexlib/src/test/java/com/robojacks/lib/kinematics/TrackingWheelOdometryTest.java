package com.robojacks.lib.kinematics;

import static org.junit.Assert.assertEquals;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.hardware.VexInertial;
import com.robojacks.lib.hardware.VexMotor;
import com.robojacks.lib.hardware.VexRotationSensor;
import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.math.Translation2d;
import org.junit.Test;

public class TrackingWheelOdometryTest extends SimTestBase {
    @Test
    public void spinningInPlaceDoesNotMoveTheRobot() {
        double fwdOffset = 0.05;   // forward wheel 5 cm left of center
        double sideOffset = -0.08; // sideways wheel 8 cm behind center
        TrackingWheelOdometry odo = new TrackingWheelOdometry(fwdOffset, sideOffset, Rotation2d.kZero, 0, 0);
        // Turn 90 degrees CCW in place, in small steps: wheels read only the turning motion.
        double forward = 0, sideways = 0;
        for (int i = 1; i <= 90; i++) {
            double dTheta = Math.toRadians(1);
            forward += -fwdOffset * dTheta;   // a wheel left of center rolls backward
            sideways += sideOffset * dTheta;  // a wheel behind center rolls right (negative)
            odo.update(Rotation2d.fromDegrees(i), forward, sideways);
        }
        Pose2d p = odo.getPoseMeters();
        assertEquals(0.0, p.getX(), 1e-9);
        assertEquals(0.0, p.getY(), 1e-9);
        assertEquals(90.0, p.getRotation().getDegrees(), 1e-9);
    }

    @Test
    public void matchesTheSimulatedRobot() {
        // A simulated tank drive with a forward and a sideways tracking wheel on Rotation Sensors.
        VexMotor left = new VexMotor(1, Cartridge.GREEN);
        VexMotor right = new VexMotor(2, Cartridge.GREEN);
        VexInertial imu = new VexInertial(3);
        VexRotationSensor fwd = new VexRotationSensor(4);
        VexRotationSensor side = new VexRotationSensor(5);
        double wheel = 0.07; // 2.75 in
        fwd.setPositionConversionFactor(Math.PI * wheel);
        side.setPositionConversionFactor(Math.PI * wheel);
        sim.simulateTankDrive(new int[] {1}, new int[] {2}, Math.PI * 0.1, 0.3);
        // Wheel locations from robot center, WPILib robot frame: forward wheel 4 cm left, sideways wheel 6 cm behind
        Translation2d fwdLoc = new Translation2d(0.0, 0.04);
        Translation2d sideLoc = new Translation2d(-0.06, 0.0);
        sim.simulateTrackingWheel(4, true, fwdLoc, wheel);
        sim.simulateTrackingWheel(5, false, sideLoc, wheel);
        run(2.1); // IMU calibration

        TrackingWheelOdometry odo = new TrackingWheelOdometry(fwdLoc, sideLoc, imu.getRotation2d(), fwd.getPosition(), side.getPosition());
        // Drive an arc, then spin
        left.setVoltage(4);
        right.setVoltage(8);
        for (int i = 0; i < 150; i++) {
            run(0.010);
            odo.update(imu.getRotation2d(), fwd.getPosition(), side.getPosition());
        }
        left.setVoltage(-6);
        right.setVoltage(6);
        for (int i = 0; i < 80; i++) {
            run(0.010);
            odo.update(imu.getRotation2d(), fwd.getPosition(), side.getPosition());
        }
        Pose2d truth = sim.getTruePose();
        Pose2d est = odo.getPoseMeters();
        assertEquals(truth.getX(), est.getX(), 0.01);
        assertEquals(truth.getY(), est.getY(), 0.01);
    }
}
