package com.robojacks.lib.command;

/** Runs once and finishes immediately. */
public class InstantCommand extends FunctionalCommand {
    public InstantCommand(Runnable action, Subsystem... requirements) {
        super(action, () -> {}, interrupted -> {}, () -> true, requirements);
    }

    public InstantCommand() {
        this(() -> {});
    }
}
