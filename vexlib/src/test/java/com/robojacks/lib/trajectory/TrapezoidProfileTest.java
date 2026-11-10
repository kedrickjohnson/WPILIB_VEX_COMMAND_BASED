package com.robojacks.lib.trajectory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrapezoidProfileTest {
    private static final double DT = 0.010;

    /** Steps the profile until it stops changing; checks the limits along the way. */
    private static TrapezoidProfile.State runToGoal(TrapezoidProfile.Constraints c, TrapezoidProfile.State start,
                                                    TrapezoidProfile.State goal, double maxSeconds) {
        TrapezoidProfile profile = new TrapezoidProfile(c);
        TrapezoidProfile.State s = start;
        for (double t = 0; t < maxSeconds; t += DT) {
            TrapezoidProfile.State next = profile.calculate(DT, s, goal);
            assertTrue("speed limit", Math.abs(next.velocity) <= c.maxVelocity + 1e-9);
            assertTrue("accel limit", Math.abs(next.velocity - s.velocity) <= c.maxAcceleration * DT + 1e-9);
            s = next;
        }
        return s;
    }

    @Test
    public void reachesGoalAndStops() {
        TrapezoidProfile.State end = runToGoal(new TrapezoidProfile.Constraints(1.0, 2.0),
                new TrapezoidProfile.State(0, 0), new TrapezoidProfile.State(3.0, 0), 6.0);
        assertEquals(3.0, end.position, 1e-6);
        assertEquals(0.0, end.velocity, 1e-6);
    }

    @Test
    public void shortMoveIsATriangle() {
        TrapezoidProfile.State end = runToGoal(new TrapezoidProfile.Constraints(10.0, 2.0),
                new TrapezoidProfile.State(0, 0), new TrapezoidProfile.State(0.5, 0), 3.0);
        assertEquals(0.5, end.position, 1e-6);
    }

    @Test
    public void worksBackwards() {
        TrapezoidProfile.State end = runToGoal(new TrapezoidProfile.Constraints(1.0, 2.0),
                new TrapezoidProfile.State(2.0, 0), new TrapezoidProfile.State(-1.0, 0), 6.0);
        assertEquals(-1.0, end.position, 1e-6);
    }

    @Test
    public void totalTimeMatchesTheory() {
        // 3 m at 1 m/s, 2 m/s^2: 0.5 s up, 2.5 s cruise, 0.5 s down = 3.5 s
        TrapezoidProfile p = new TrapezoidProfile(new TrapezoidProfile.Constraints(1.0, 2.0));
        p.calculate(0, new TrapezoidProfile.State(0, 0), new TrapezoidProfile.State(3, 0));
        assertEquals(3.5, p.totalTime(), 1e-9);
    }
}
