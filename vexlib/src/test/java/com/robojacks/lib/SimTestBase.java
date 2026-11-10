package com.robojacks.lib;

import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.sim.SimHardware;
import org.junit.Before;

/** Fresh simulated brain and command scheduler for every test. */
public abstract class SimTestBase {
    protected SimHardware sim;

    @Before
    public void setUpSim() {
        sim = new SimHardware();
        HAL.set(sim);
        CommandScheduler.resetInstance();
        CommandScheduler.getInstance().setRobotEnabled(true);
    }

    /** Runs the scheduler and advances simulated time, 10 ms per loop. */
    protected void run(double seconds) {
        int loops = (int) Math.round(seconds / 0.010);
        for (int i = 0; i < loops; i++) {
            CommandScheduler.getInstance().run();
            sim.periodic();
            sim.clock().sleepUntil(sim.clock().nowSeconds() + 0.010);
        }
    }

    protected void loop() {
        run(0.010);
    }
}
