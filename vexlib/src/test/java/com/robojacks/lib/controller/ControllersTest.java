package com.robojacks.lib.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.robojacks.lib.SimTestBase;
import com.robojacks.lib.math.filter.Debouncer;
import com.robojacks.lib.math.filter.LinearFilter;
import com.robojacks.lib.math.filter.MedianFilter;
import com.robojacks.lib.math.filter.SlewRateLimiter;
import com.robojacks.lib.trajectory.TrapezoidProfile;
import org.junit.Test;

public class ControllersTest extends SimTestBase {
    @Test
    public void pidContinuousTakesShortWay() {
        PIDController pid = new PIDController(1.0, 0, 0);
        pid.enableContinuousInput(-Math.PI, Math.PI);
        // From 170 deg to -170 deg is +20 deg, not -340
        double out = pid.calculate(Math.toRadians(170), Math.toRadians(-170));
        assertEquals(Math.toRadians(20), out, 1e-9);
    }

    @Test
    public void profiledPidDrivesASimpleMechanismToGoal() {
        ProfiledPIDController c = new ProfiledPIDController(10.0, 0, 0,
                new TrapezoidProfile.Constraints(2.0, 4.0));
        c.setTolerance(0.01);
        double position = 0.0;
        c.reset(position);
        for (int i = 0; i < 400; i++) {
            double velocity = c.calculate(position, 1.0) + c.getSetpoint().velocity; // P + velocity feedforward
            position += velocity * 0.010;
        }
        assertEquals(1.0, position, 0.01);
        assertTrue(c.atGoal());
    }

    @Test
    public void armFeedforwardHoldsLevel() {
        ArmFeedforward ff = new ArmFeedforward(0.2, 1.1, 1.5);
        assertEquals(1.1, ff.calculate(0.0, 0.0), 1e-9);              // level: all gravity
        assertEquals(0.0, ff.calculate(Math.PI / 2, 0.0), 1e-9);      // straight up: none
        assertEquals(0.2 + 1.5, ff.calculate(Math.PI / 2, 1.0), 1e-9); // moving
        ElevatorFeedforward ef = new ElevatorFeedforward(0.1, 0.8, 2.0);
        assertEquals(0.8, ef.calculate(0.0), 1e-9);
    }

    @Test
    public void slewRateLimiterLimitsChange() {
        SlewRateLimiter limiter = new SlewRateLimiter(2.0); // 2 units per second
        run(0.25);
        assertEquals(0.5, limiter.calculate(1.0), 1e-9);
        run(0.5);
        assertEquals(1.0, limiter.calculate(1.0), 1e-9);
    }

    @Test
    public void debouncerIgnoresShortBlips() {
        Debouncer d = new Debouncer(0.1);
        assertEquals(false, d.calculate(true));
        run(0.05);
        assertEquals(false, d.calculate(false)); // blip ended early
        run(0.05);
        d.calculate(true);
        run(0.11);
        assertEquals(true, d.calculate(true));
    }

    @Test
    public void filters() {
        LinearFilter avg = LinearFilter.movingAverage(4);
        avg.calculate(4);
        avg.calculate(4);
        avg.calculate(4);
        assertEquals(4.0, avg.calculate(4), 1e-9);
        MedianFilter median = new MedianFilter(3);
        median.calculate(1);
        median.calculate(100); // glitch
        assertEquals(2.0, median.calculate(2), 1e-9);
    }
}
