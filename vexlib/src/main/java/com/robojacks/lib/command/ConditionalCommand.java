package com.robojacks.lib.command;

import java.util.function.BooleanSupplier;

/** Chooses between two commands when it starts. */
public class ConditionalCommand extends Command {
    private final Command onTrue;
    private final Command onFalse;
    private final BooleanSupplier condition;
    private Command selected;

    public ConditionalCommand(Command onTrue, Command onFalse, BooleanSupplier condition) {
        CommandGroupUtil.markComposed(onTrue, onFalse);
        this.onTrue = onTrue;
        this.onFalse = onFalse;
        this.condition = condition;
        addRequirements(onTrue.getRequirements());
        addRequirements(onFalse.getRequirements());
    }

    @Override
    public void initialize() {
        selected = condition.getAsBoolean() ? onTrue : onFalse;
        selected.initialize();
    }

    @Override
    public void execute() {
        selected.execute();
    }

    @Override
    public void end(boolean interrupted) {
        selected.end(interrupted);
    }

    @Override
    public boolean isFinished() {
        return selected.isFinished();
    }

    @Override
    public boolean runsWhenDisabled() {
        return onTrue.runsWhenDisabled() && onFalse.runsWhenDisabled();
    }
}
