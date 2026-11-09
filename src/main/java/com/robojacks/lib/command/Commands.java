package com.robojacks.lib.command;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Static command factories, same names as WPILib's Commands class. */
public final class Commands {
    private Commands() {}

    public static Command none() {
        return new InstantCommand();
    }

    public static Command idle(Subsystem... requirements) {
        return run(() -> {}, requirements);
    }

    public static Command runOnce(Runnable action, Subsystem... requirements) {
        return new InstantCommand(action, requirements);
    }

    public static Command run(Runnable action, Subsystem... requirements) {
        return new RunCommand(action, requirements);
    }

    /** Runs start once, then end when interrupted. */
    public static Command startEnd(Runnable start, Runnable end, Subsystem... requirements) {
        return new FunctionalCommand(start, () -> {}, interrupted -> end.run(), () -> false, requirements);
    }

    /** Runs {@code run} every loop, then end when interrupted. */
    public static Command runEnd(Runnable run, Runnable end, Subsystem... requirements) {
        return new FunctionalCommand(() -> {}, run, interrupted -> end.run(), () -> false, requirements);
    }

    public static Command print(String message) {
        return new InstantCommand(() -> System.out.println(message)).ignoringDisable(true);
    }

    public static Command waitSeconds(double seconds) {
        return new WaitCommand(seconds);
    }

    public static Command waitUntil(BooleanSupplier condition) {
        return new WaitUntilCommand(condition);
    }

    /** Picks a command when scheduled (both must declare their requirements up front). */
    public static Command either(Command onTrue, Command onFalse, BooleanSupplier selector) {
        return new ConditionalCommand(onTrue, onFalse, selector);
    }

    /** Builds the command at schedule time, e.g. to use the robot's pose at that moment. */
    public static Command defer(Supplier<Command> supplier, Subsystem... requirements) {
        return new DeferredCommand(supplier, requirements);
    }

    public static Command sequence(Command... commands) {
        return new SequentialCommandGroup(commands);
    }

    public static Command parallel(Command... commands) {
        return new ParallelCommandGroup(commands);
    }

    public static Command race(Command... commands) {
        return new ParallelRaceGroup(commands);
    }

    public static Command deadline(Command deadline, Command... others) {
        return new ParallelDeadlineGroup(deadline, others);
    }
}
