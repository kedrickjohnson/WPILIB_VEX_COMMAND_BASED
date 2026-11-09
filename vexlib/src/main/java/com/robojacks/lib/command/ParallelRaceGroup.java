package com.robojacks.lib.command;

import java.util.LinkedHashSet;
import java.util.Set;

/** Runs commands at the same time; ends when ANY one finishes (the rest are interrupted). */
public class ParallelRaceGroup extends Command {
    private final Set<Command> commands = new LinkedHashSet<>();
    private boolean finished = true;
    private boolean runWhenDisabled = true;

    public ParallelRaceGroup(Command... commands) {
        addCommands(commands);
    }

    public final void addCommands(Command... toAdd) {
        CommandGroupUtil.markComposed(toAdd);
        for (Command c : toAdd) {
            for (Subsystem s : c.getRequirements()) {
                if (getRequirements().contains(s)) {
                    throw new IllegalArgumentException(
                            "Commands in a race group can't share subsystem " + s.getName());
                }
            }
            commands.add(c);
            addRequirements(c.getRequirements());
            runWhenDisabled &= c.runsWhenDisabled();
        }
    }

    @Override
    public void initialize() {
        finished = false;
        for (Command c : commands) {
            c.initialize();
        }
    }

    @Override
    public void execute() {
        for (Command c : commands) {
            c.execute();
            if (c.isFinished()) {
                finished = true;
            }
        }
    }

    @Override
    public void end(boolean interrupted) {
        for (Command c : commands) {
            c.end(!c.isFinished() || interrupted);
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
