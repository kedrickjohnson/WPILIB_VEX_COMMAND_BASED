package com.robojacks.docs;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.CommandScheduler;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.math.util.Units;
import com.robojacks.lib.sim.SimHardware;

/** A simulator script from the docs: checks the intake and clamp logic with simulated sensors. */
public final class DocsSimExample {
    private DocsSimExample() {}

    // [sim-script]
    public static void main(String[] args) {
        SimHardware sim = new SimHardware();
        HAL.set(sim);
        CommandScheduler.getInstance().setRobotEnabled(true);

        ExampleIntake intake = new ExampleIntake();
        ExampleClamp clamp = new ExampleClamp();

        // Intake runs until the distance sensor sees a piece
        Command intaking = intake.intakeCommand();
        intaking.schedule();
        step(sim, 0.5);
        check(intaking.isScheduled(), "intake keeps running with no piece");
        sim.getDistanceSensor(ExampleConstants.IntakeConstants.kDistancePort).distanceMeters = Units.inchesToMeters(1.0);
        step(sim, 0.1);
        check(!intaking.isScheduled(), "intake stops when the piece arrives");

        // The clamp closes when the goal switch is pressed
        clamp.clampWhenGoalArrivesCommand().schedule();
        step(sim, 0.2);
        check(!sim.getAdiPort('B').isOn(), "clamp waits for the goal");
        sim.getAdiPort('A').setPressed(true);
        step(sim, 0.1);
        check(sim.getAdiPort('B').isOn(), "clamp closes on the goal");

        System.out.println("Docs sim example: all checks passed");
    }

    private static void step(SimHardware sim, double seconds) {
        double end = sim.clock().nowSeconds() + seconds;
        while (sim.clock().nowSeconds() < end) {
            CommandScheduler.getInstance().run();
            sim.clock().sleepUntil(sim.clock().nowSeconds() + 0.010);
        }
    }

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what);
        if (!ok) {
            System.exit(1);
        }
    }
    // [/sim-script]
}
