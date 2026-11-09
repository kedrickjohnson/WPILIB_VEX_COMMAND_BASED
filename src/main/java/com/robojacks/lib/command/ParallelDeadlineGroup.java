package com.robojacks.lib.command;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runs commands at the same time; ends when the DEADLINE command finishes. */
public class ParallelDeadlineGroup extends Command {
    private final Map<Command, Boolean> commands = new LinkedHashMap<>();
    private final Command deadline;
    private boolean finished = true;
    private boolean runWhenDisabled = true;

    public ParallelDeadlineGroup(Command deadline, Command... others) {
        this.deadline = deadline;
        addOne(deadline);
        for (Command c : others) {
            addOne(c);
        }
    }

    private void addOne(Command c) {
        CommandGroupUtil.markComposed(c);
        for (Subsystem s : c.getRequirements()) {
            if (getRequirements().contains(s)) {
                throw new IllegalArgumentException(
                        "Commands in a deadline group can't share subsystem " + s.getName());
            }
        }
        commands.put(c, false);
        addRequirements(c.getRequirements());
        runWhenDisabled &= c.runsWhenDisabled();
    }

    @Override
    public void initialize() {
        finished = false;
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
                if (c == deadline) {
                    finished = true;
                }
            }
        }
    }

    @Override
    public void end(boolean interrupted) {
        for (Map.Entry<Command, Boolean> e : commands.entrySet()) {
            if (e.getValue()) {
                e.getKey().end(true);
                e.setValue(false);
            }
        }
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean runsWhenDisabled() {
        return runWhenDisabled;
    }
}
