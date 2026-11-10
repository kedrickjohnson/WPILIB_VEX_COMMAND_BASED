package com.robojacks.lib.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs subsystems, button bindings and commands once per loop.
 * Call {@code CommandScheduler.getInstance().run()} from robotPeriodic(), exactly like FRC.
 */
public final class CommandScheduler {
    private static CommandScheduler instance;

    public static CommandScheduler getInstance() {
        if (instance == null) {
            instance = new CommandScheduler();
        }
        return instance;
    }

    /**
     * Throws away the scheduler and everything registered with it (subsystems, bindings, running
     * commands). For unit tests and simulator scripts that build a fresh robot each time.
     */
    public static void resetInstance() {
        instance = null;
    }

    private final Set<Subsystem> subsystems = new LinkedHashSet<>();
    private final Map<Subsystem, Command> defaultCommands = new LinkedHashMap<>();
    private final Map<Subsystem, Command> requirements = new HashMap<>();
    private final Set<Command> scheduled = new LinkedHashSet<>();
    private final List<Runnable> bindings = new ArrayList<>();

    private boolean inRunLoop;
    private final List<Command> toSchedule = new ArrayList<>();
    private final List<Command> toCancel = new ArrayList<>();

    private boolean robotEnabled;

    private CommandScheduler() {}

    /** Set by VexRobot when the mode changes. Disabled = cancel commands that don't run when disabled. */
    public void setRobotEnabled(boolean enabled) {
        this.robotEnabled = enabled;
    }

    public void registerSubsystem(Subsystem... subs) {
        for (Subsystem s : subs) {
            subsystems.add(s);
        }
    }

    public void unregisterSubsystem(Subsystem s) {
        subsystems.remove(s);
        defaultCommands.remove(s);
    }

    public void setDefaultCommand(Subsystem subsystem, Command command) {
        if (!command.hasRequirement(subsystem)) {
            throw new IllegalArgumentException("Default commands must require their subsystem");
        }
        CommandGroupUtil.markComposed(command); // owned by the scheduler now
        defaultCommands.put(subsystem, command);
    }

    public void removeDefaultCommand(Subsystem subsystem) {
        defaultCommands.remove(subsystem);
    }

    public Command getDefaultCommand(Subsystem subsystem) {
        return defaultCommands.get(subsystem);
    }

    /** Triggers register their polling here. */
    void addBinding(Runnable binding) {
        bindings.add(binding);
    }

    /** Removes all button bindings (e.g. when re-configuring for a test mode). */
    public void clearBindings() {
        bindings.clear();
    }

    public void schedule(Command... commands) {
        for (Command c : commands) {
            schedule(c);
        }
    }

    public void schedule(Command command) {
        if (command == null) {
            return;
        }
        if (inRunLoop) {
            toSchedule.add(command);
            return;
        }
        if (command.composed && !defaultCommands.containsValue(command)) {
            throw new IllegalArgumentException(
                    "A command that is part of a group can't be scheduled on its own: " + command.getName());
        }
        if (scheduled.contains(command)) {
            return;
        }
        if (!robotEnabled && !command.runsWhenDisabled()) {
            return;
        }

        // Requirement conflicts
        Set<Command> conflicting = new LinkedHashSet<>();
        for (Subsystem req : command.getRequirements()) {
            Command owner = requirements.get(req);
            if (owner != null) {
                if (owner.getInterruptionBehavior() == Command.InterruptionBehavior.kCancelIncoming) {
                    return; // owner refuses to be interrupted
                }
                conflicting.add(owner);
            }
        }
        for (Command c : conflicting) {
            cancel(c);
        }

        command.initialize();
        scheduled.add(command);
        for (Subsystem req : command.getRequirements()) {
            requirements.put(req, command);
        }
    }

    public void cancel(Command... commands) {
        for (Command c : commands) {
            cancel(c);
        }
    }

    public void cancel(Command command) {
        if (inRunLoop) {
            toCancel.add(command);
            return;
        }
        if (!scheduled.contains(command)) {
            return;
        }
        scheduled.remove(command);
        releaseRequirements(command);
        command.end(true);
    }

    public void cancelAll() {
        for (Command c : new ArrayList<>(scheduled)) {
            cancel(c);
        }
    }

    public boolean isScheduled(Command command) {
        return scheduled.contains(command);
    }

    /** The command currently using a subsystem, or null. */
    public Command requiring(Subsystem subsystem) {
        return requirements.get(subsystem);
    }

    private void releaseRequirements(Command command) {
        for (Subsystem req : command.getRequirements()) {
            if (requirements.get(req) == command) {
                requirements.remove(req);
            }
        }
    }

    /** One loop: subsystems -> bindings -> commands -> default commands. */
    public void run() {
        for (Subsystem s : subsystems) {
            s.periodic();
        }

        for (Runnable b : new ArrayList<>(bindings)) {
            b.run();
        }

        inRunLoop = true;
        for (Command command : new ArrayList<>(scheduled)) {
            if (!scheduled.contains(command)) {
                continue;
            }
            if (!robotEnabled && !command.runsWhenDisabled()) {
                scheduled.remove(command);
                releaseRequirements(command);
                command.end(true);
                continue;
            }
            command.execute();
            if (command.isFinished()) {
                scheduled.remove(command);
                releaseRequirements(command);
                command.end(false);
            }
        }
        inRunLoop = false;

        for (Command c : new ArrayList<>(toSchedule)) {
            schedule(c);
        }
        for (Command c : new ArrayList<>(toCancel)) {
            cancel(c);
        }
        toSchedule.clear();
        toCancel.clear();

        for (Map.Entry<Subsystem, Command> e : defaultCommands.entrySet()) {
            if (!requirements.containsKey(e.getKey())) {
                schedule(e.getValue());
            }
        }
    }
}
