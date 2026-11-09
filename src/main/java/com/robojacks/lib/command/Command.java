package com.robojacks.lib.command;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Base class for commands. Lifecycle and decorators match WPILib:
 * initialize -> execute (every loop) -> isFinished? -> end(interrupted).
 */
public abstract class Command {
    public enum InterruptionBehavior {
        /** Default: a new command needing the same subsystem cancels this one. */
        kCancelSelf,
        /** This command keeps running and the new command is refused. */
        kCancelIncoming
    }

    private final Set<Subsystem> requirements = new LinkedHashSet<>();
    private String name;
    boolean composed;

    public void initialize() {}

    public void execute() {}

    public void end(boolean interrupted) {}

    public boolean isFinished() {
        return false;
    }

    /** Whether it keeps running while the robot is disabled. */
    public boolean runsWhenDisabled() {
        return false;
    }

    public InterruptionBehavior getInterruptionBehavior() {
        return InterruptionBehavior.kCancelSelf;
    }

    public final void addRequirements(Subsystem... subsystems) {
        for (Subsystem s : subsystems) {
            if (s != null) {
                requirements.add(s);
            }
        }
    }

    public final void addRequirements(Set<Subsystem> subsystems) {
        requirements.addAll(subsystems);
    }

    public Set<Subsystem> getRequirements() {
        return Collections.unmodifiableSet(requirements);
    }

    public boolean hasRequirement(Subsystem s) {
        return requirements.contains(s);
    }

    public String getName() {
        return name != null ? name : "Command";
    }

    public void setName(String name) {
        this.name = name;
    }

    public Command withName(String name) {
        setName(name);
        return this;
    }

    // ----- Scheduling -----

    public void schedule() {
        CommandScheduler.getInstance().schedule(this);
    }

    public void cancel() {
        CommandScheduler.getInstance().cancel(this);
    }

    public boolean isScheduled() {
        return CommandScheduler.getInstance().isScheduled(this);
    }

    // ----- Decorators (each returns a new composed command) -----

    public Command withTimeout(double seconds) {
        return raceWith(new WaitCommand(seconds));
    }

    public Command until(BooleanSupplier condition) {
        return raceWith(new WaitUntilCommand(condition));
    }

    public Command onlyWhile(BooleanSupplier condition) {
        return until(() -> !condition.getAsBoolean());
    }

    public Command andThen(Command... next) {
        Command[] all = new Command[next.length + 1];
        all[0] = this;
        System.arraycopy(next, 0, all, 1, next.length);
        return new SequentialCommandGroup(all);
    }

    public Command andThen(Runnable action, Subsystem... requirements) {
        return andThen(new InstantCommand(action, requirements));
    }

    public Command beforeStarting(Command before) {
        return new SequentialCommandGroup(before, this);
    }

    public Command beforeStarting(Runnable action, Subsystem... requirements) {
        return beforeStarting(new InstantCommand(action, requirements));
    }

    public Command alongWith(Command... parallel) {
        Command[] all = new Command[parallel.length + 1];
        all[0] = this;
        System.arraycopy(parallel, 0, all, 1, parallel.length);
        return new ParallelCommandGroup(all);
    }

    public Command raceWith(Command... parallel) {
        Command[] all = new Command[parallel.length + 1];
        all[0] = this;
        System.arraycopy(parallel, 0, all, 1, parallel.length);
        return new ParallelRaceGroup(all);
    }

    /** Runs {@code parallel} alongside this; ends when THIS ends. */
    public Command deadlineFor(Command... parallel) {
        return new ParallelDeadlineGroup(this, parallel);
    }

    public Command finallyDo(Runnable onEnd) {
        Command self = this;
        CommandGroupUtil.markComposed(self);
        Command wrapped = new Command() {
            @Override public void initialize() { self.initialize(); }
            @Override public void execute() { self.execute(); }
            @Override public boolean isFinished() { return self.isFinished(); }
            @Override public void end(boolean interrupted) {
                self.end(interrupted);
                onEnd.run();
            }
            @Override public boolean runsWhenDisabled() { return self.runsWhenDisabled(); }
        };
        wrapped.addRequirements(getRequirements());
        wrapped.setName(getName());
        return wrapped;
    }

    public Command ignoringDisable(boolean run) {
        Command self = this;
        CommandGroupUtil.markComposed(self);
        Command wrapped = new Command() {
            @Override public void initialize() { self.initialize(); }
            @Override public void execute() { self.execute(); }
            @Override public boolean isFinished() { return self.isFinished(); }
            @Override public void end(boolean interrupted) { self.end(interrupted); }
            @Override public boolean runsWhenDisabled() { return run; }
        };
        wrapped.addRequirements(getRequirements());
        wrapped.setName(getName());
        return wrapped;
    }

    @Override
    public String toString() {
        return getName();
    }
}
