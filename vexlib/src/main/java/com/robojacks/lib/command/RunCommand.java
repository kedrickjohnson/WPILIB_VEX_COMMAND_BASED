package com.robojacks.lib.command;

/** Runs an action every loop until interrupted. */
public class RunCommand extends FunctionalCommand {
    public RunCommand(Runnable action, Subsystem... requirements) {
        super(() -> {}, action, interrupted -> {}, () -> false, requirements);
    }
}
