package com.robojacks.lib.command;

import java.util.function.Supplier;

/**
 * Builds a fresh command every time it's scheduled. The supplied command must not
 * require anything beyond the requirements passed here.
 */
public class DeferredCommand extends Command {
    private final Supplier<Command> supplier;
    private Command current;

    public DeferredCommand(Supplier<Command> supplier, Subsystem... requirements) {
        this.supplier = supplier;
        addRequirements(requirements);
    }

    @Override
    public void initialize() {
        Command built = supplier.get();
        CommandGroupUtil.markComposed(built);
        current = built;
        current.initialize();
    }

    @Override
    public void execute() {
        current.execute();
    }

    @Override
    public void end(boolean interrupted) {
        current.end(interrupted);
        current = null;
    }

    @Override
    public boolean isFinished() {
        return current.isFinished();
    }
}
