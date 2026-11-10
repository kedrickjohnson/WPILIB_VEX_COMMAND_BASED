package com.robojacks.lib.hardware;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.hal.AdiPortIO;
import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.math.Pose2d;
import com.robojacks.lib.math.Rotation2d;
import com.robojacks.lib.robot.RobotController;
import org.junit.Test;

public class HardwareTest extends SimTestBase {
    @Test
    public void solenoidDrivesItsPort() {
        Solenoid clamp = new Solenoid('B');
        assertEquals(AdiPortIO.Mode.DIGITAL_OUT, sim.getAdiPort('B').mode);
        assertFalse(sim.getAdiPort('B').isOn());
        clamp.set(true);
        assertTrue(sim.getAdiPort('B').isOn());
        clamp.toggle();
        assertFalse(clamp.get());
        assertEquals(2, clamp.getActuationCount());
    }

    @Test
    public void invertedSolenoid() {
        Solenoid s = new Solenoid(0, 'C', true);
        assertTrue("off = valve energized when inverted", sim.getAdiPort('C').isOn());
        s.set(true);
        assertFalse(sim.getAdiPort('C').isOn());
        assertTrue(s.get());
    }

    @Test
    public void doubleSolenoid() {
        DoubleSolenoid d = new DoubleSolenoid('C', 'D');
        d.set(DoubleSolenoid.Value.kForward);
        assertTrue(sim.getAdiPort('C').isOn());
        assertFalse(sim.getAdiPort('D').isOn());
        d.toggle();
        assertFalse(sim.getAdiPort('C').isOn());
        assertTrue(sim.getAdiPort('D').isOn());
    }

    @Test
    public void digitalInputAndPotentiometer() {
        DigitalInput limit = new DigitalInput('A');
        assertFalse(limit.get());
        sim.getAdiPort('A').setPressed(true);
        assertTrue(limit.get());
        AnalogPotentiometer pot = new AnalogPotentiometer('E', 333.0, -100.0);
        sim.getAdiPort('E').value = 4095;
        assertEquals(233.0, pot.get(), 1e-9);
    }

    @Test(expected = IllegalStateException.class)
    public void portsCanOnlyBeUsedOnce() {
        new VexMotor(5, Cartridge.GREEN);
        new VexDistanceSensor(5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encoderPortsMustBeAPair() {
        new AdiEncoder('B', 'C', false);
    }

    @Test
    public void sensors() {
        VexDistanceSensor distance = new VexDistanceSensor(6);
        assertFalse(distance.hasTarget());
        sim.getDistanceSensor(6).distanceMeters = 0.12;
        assertTrue(distance.isWithin(0.15));

        VexOpticalSensor optical = new VexOpticalSensor(7);
        sim.getOpticalSensor(7).showColor(355);
        assertTrue("355 is close to red at 10", optical.isColor(VexOpticalSensor.kRedHue, 25));
        assertFalse(optical.isColor(VexOpticalSensor.kBlueHue, 25));

        VexRotationSensor rotation = new VexRotationSensor(8);
        rotation.setPositionConversionFactor(360.0);
        sim.getRotationSensor(8).position = 1.25;
        assertEquals(450.0, rotation.getPosition(), 1e-9);
        assertEquals(0.25, rotation.getAbsolutePosition(), 1e-9);

        assertEquals(12.8, RobotController.getBatteryVoltage(), 1e-9);
    }

    @Test
    public void gpsReadsTheTruePoseInTheLibraryFrame() {
        VexGps gps = new VexGps(9);
        Pose2d truth = new Pose2d(-1.2, 0.6, Rotation2d.fromDegrees(135));
        sim.setTruePose(truth);
        Pose2d p = gps.getPose();
        assertEquals(-1.2, p.getX(), 1e-9);
        assertEquals(0.6, p.getY(), 1e-9);
        assertEquals(135.0, p.getRotation().getDegrees(), 1e-9);
        assertTrue(gps.isReliable());
        sim.getGps(9).errorMeters = 0.5;
        assertFalse(gps.isReliable());
    }
}
