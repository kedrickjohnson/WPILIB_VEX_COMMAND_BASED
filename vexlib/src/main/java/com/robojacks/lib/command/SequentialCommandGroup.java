package com.robojacks.lib.command;

import java.util.ArrayList;
import java.util.List;

/** Runs commands one after another. */
public class SequentialCommandGroup extends Command {
    private final List<Command> commands = new ArrayList<>();
    private int index = -1;
    private boolean runWhenDisabled = true;

    public SequentialCommandGroup(Command... commands) {
        addCommands(commands);
    }

    public final void addCommands(Command... toAdd) {
        if (index != -1) {
            throw new IllegalStateException("Can't add commands to a running group");
        }
        CommandGroupUtil.markComposed(toAdd);
        for (Command c : toAdd) {
            commands.add(c);
            addRequirements(c.getRequirements());
            runWhenDisabled &= c.runsWhenDisabled();
        }
    }

    @Override
    public void initialize() {
        index = 0;
        if (!commands.isEmpty()) {
            commands.get(0).initialize();
        }
    }

    @Override
    public void execute() {
        if (commands.isEmpty() || index >= commands.size()) {
            return;
        }
        Command current = commands.get(index);
        current.execute();
        if (current.isFinished()) {
            current.end(false);
            index++;
            if (index < commands.size()) {
                commands.get(index).initialize();
            }
        }
    }

    @Override
    public void end(boolean interrupted) {
        if (interrupted && !commands.isEmpty() && index > -1 && index < commands.size()) {
            commands.get(index).end(true);
        }
        index = -1;
    }

    @Override
    public boolean isFinished() {
        return index >= commands.size();
    }

    @Override
    public boolean runsWhenDisabled() {
        return runWhenDisabled;
    }
}
