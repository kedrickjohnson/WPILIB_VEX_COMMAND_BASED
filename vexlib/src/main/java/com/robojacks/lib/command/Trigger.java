package com.robojacks.lib.command;

import com.robojacks.lib.math.filter.Debouncer;
import java.util.function.BooleanSupplier;

/**
 * Binds commands to a condition (usually a button). Same methods as WPILib's Trigger.
 * Bindings are polled by the CommandScheduler every loop.
 *
 * <pre>{@code
 * m_driverController.r1().whileTrue(m_intake.runCommand());          // hold to run
 * m_driverController.a().onTrue(m_arm.goToCommand(ArmPosition.HIGH));  // press once
 * m_driverController.b().toggleOnTrue(m_flywheel.spinCommand());       // press to start, again to stop
 *
 * // Any condition can be a trigger: a sensor, a combination, a stick past a threshold
 * new Trigger(m_intake::hasPiece).debounce(0.1).onTrue(m_controller.rumbleCommand("."));
 * m_driverController.l1().and(m_driverController.l2()).onTrue(m_climber.deployCommand());
 * }</pre>
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

    /** Each release toggles the command on/off. */
    public Trigger toggleOnFalse(Command command) {
        bind((prev, cur) -> {
            if (prev && !cur) {
                if (command.isScheduled()) {
                    command.cancel();
                } else {
                    command.schedule();
                }
            }
        });
        return this;
    }

    /** Schedules the command whenever the condition changes, either way. */
    public Trigger onChange(Command command) {
        bind((prev, cur) -> {
            if (prev != cur) {
                command.schedule();
            }
        });
        return this;
    }

    /**
     * A trigger that only changes after the condition has held for {@code seconds}: a noisy
     * sensor or a bouncing switch won't fire it twice.
     */
    public Trigger debounce(double seconds) {
        return debounce(seconds, Debouncer.DebounceType.kRising);
    }

    public Trigger debounce(double seconds, Debouncer.DebounceType type) {
        Debouncer debouncer = new Debouncer(seconds, type);
        return new Trigger(() -> debouncer.calculate(condition.getAsBoolean()));
    }

    /** True for one loop when the condition goes false to true. */
    public Trigger rising() {
        return new Trigger(new BooleanSupplier() {
            private boolean previous = condition.getAsBoolean();

            @Override
            public boolean getAsBoolean() {
                boolean present = condition.getAsBoolean();
                boolean edge = !previous && present;
                previous = present;
                return edge;
            }
        });
    }

    /** True for one loop when the condition goes true to false. */
    public Trigger falling() {
        return new Trigger(new BooleanSupplier() {
            private boolean previous = condition.getAsBoolean();

            @Override
            public boolean getAsBoolean() {
                boolean present = condition.getAsBoolean();
                boolean edge = previous && !present;
                previous = present;
                return edge;
            }
        });
    }

    public Trigger and(BooleanSupplier other) {
        return new Trigger(() -> condition.getAsBoolean() && other.getAsBoolean());
    }

    public Trigger or(BooleanSupplier other) {
        return new Trigger(() -> condition.getAsBoolean() || other.getAsBoolean());
    }

    /** True when exactly one of the two is true. */
    public Trigger xor(BooleanSupplier other) {
        return new Trigger(() -> condition.getAsBoolean() ^ other.getAsBoolean());
    }

    public Trigger negate() {
        return new Trigger(() -> !condition.getAsBoolean());
    }
}
