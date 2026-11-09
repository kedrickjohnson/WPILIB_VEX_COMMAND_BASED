package com.robojacks.lib.command;

import java.util.function.BooleanSupplier;

/** Finishes when a condition becomes true. */
public class WaitUntilCommand extends Command {
    private final BooleanSupplier condition;

    public WaitUntilCommand(BooleanSupplier condition) {
        this.condition = condition;
    }

    @Override
    public boolean isFinished() {
        return condition.getAsBoolean();
    }

    @Override
    public boolean runsWhenDisabled() {
        return true;
    }
}
