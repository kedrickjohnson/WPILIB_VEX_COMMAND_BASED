package com.robojacks.lib.command;

import java.util.function.BooleanSupplier;

/**
 * Binds commands to a condition (usually a button). Same methods as WPILib's Trigger.
 * Bindings are polled by the CommandScheduler every loop.
 */
public class Trigger implements BooleanSupplier {
    private final BooleanSupplier condition;

    public Trigger(BooleanSupplier condition) {
        this.condition = condition;
    }

    @Override
    public boolean getAsBoolean() {
        return condition.getAsBoolean();
    }

    private interface Edge {
        void run(boolean previous, boolean current);
    }

    private void bind(Edge edge) {
        CommandScheduler.getInstance().addBinding(new Runnable() {
            private boolean previous = condition.getAsBoolean();

            @Override
            public void run() {
                boolean current = condition.getAsBoolean();
                edge.run(previous, current);
                previous = current;
            }
        });
    }

    /** Schedule when the condition goes false -> true. */
    public Trigger onTrue(Command command) {
        bind((prev, cur) -> {
            if (!prev && cur) {
                command.schedule();
            }
        });
        return this;
    }

    /** Schedule when the condition goes true -> false. */
    public Trigger onFalse(Command command) {
        bind((prev, cur) -> {
            if (prev && !cur) {
                command.schedule();
            }
        });
        return this;
    }

    /** Run while true; cancel when it goes false. */
    public Trigger whileTrue(Command command) {
        bind((prev, cur) -> {
            if (!prev && cur) {
                command.schedule();
            } else if (prev && !cur) {
                command.cancel();
            }
        });
        return this;
    }

    public Trigger whileFalse(Command command) {
        bind((prev, cur) -> {
            if (prev && !cur) {
                command.schedule();
            } else if (!prev && cur) {
                command.cancel();
            }
        });
        return this;
    }

    /** Each press toggles the command on/off. */
    public Trigger toggleOnTrue(Command command) {
        bind((prev, cur) -> {
            if (!prev && cur) {
                if (command.isScheduled()) {
                    command.cancel();
                } else {
                    command.schedule();
                }
            }
        });
        return this;
    }

    public Trigger and(BooleanSupplier other) {
        return new Trigger(() -> condition.getAsBoolean() && other.getAsBoolean());
    }

    public Trigger or(BooleanSupplier other) {
        return new Trigger(() -> condition.getAsBoolean() || other.getAsBoolean());
    }

    public Trigger negate() {
        return new Trigger(() -> !condition.getAsBoolean());
    }
}
