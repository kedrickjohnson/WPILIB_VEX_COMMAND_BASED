package com.robojacks.lib.command;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runs commands at the same time; ends when ALL have finished. */
public class ParallelCommandGroup extends Command {
    private final Map<Command, Boolean> commands = new LinkedHashMap<>(); // value = still running
    private boolean runWhenDisabled = true;

    public ParallelCommandGroup(Command... commands) {
        addCommands(commands);
    }

    public final void addCommands(Command... toAdd) {
        CommandGroupUtil.markComposed(toAdd);
        for (Command c : toAdd) {
            for (Subsystem s : c.getRequirements()) {
                if (getRequirements().contains(s)) {
                    throw new IllegalArgumentException(
                            "Commands in a parallel group can't share subsystem " + s.getName());
                }
            }
            commands.put(c, false);
            addRequirements(c.getRequirements());
            runWhenDisabled &= c.runsWhenDisabled();
        }
    }

    @Override
    public void initialize() {
        for (Map.Entry<Command, Boolean> e : commands.entrySet()) {
            e.getKey().initialize();
            e.setValue(true);
        }
    }

    @Override
    public void execute() {
        for (Map.Entry<Command, Boolean> e : commands.entrySet()) {
            if (!e.getValue()) {
                continue;
            }
            Command c = e.getKey();
            c.execute();
            if (c.isFinished()) {
                c.end(false);
                e.setValue(false);
            }
        }
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted) {
            for (Map.Entry<Command, Boolean> e : commands.entrySet()) {
                if (e.getValue()) {
                    e.getKey().end(true);
                }
            }
        }
        for (Map.Entry<Command, Boolean> e : commands.entrySet()) {
            e.setValue(false);
        }
    }

    @Override
    public boolean isFinished() {
        return !commands.containsValue(true);
    }

    @Override
    public boolean runsWhenDisabled() {
        return runWhenDisabled;
    }
}
