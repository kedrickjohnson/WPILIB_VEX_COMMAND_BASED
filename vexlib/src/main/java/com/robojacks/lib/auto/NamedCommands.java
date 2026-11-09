package com.robojacks.lib.auto;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.command.Commands;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Commands that autos refer to by name, like PathPlanner's NamedCommands. The path planner's
 * exported code uses these for its markers ("closeClaw", "intake", ...).
 *
 * <p>Register a <em>factory</em>, not a command, so every use gets a fresh instance (a command
 * can only be in one group):
 * <pre>{@code
 * NamedCommands.register("closeClaw", m_claw::closeCommand);
 * NamedCommands.register("armCarry", m_arm::carryCommand);
 * }</pre>
 * Register everything in RobotContainer's constructor, before building any autos.
 */
public final class NamedCommands {
    private static final Map<String, Supplier<Command>> factories = new LinkedHashMap<>();

    private NamedCommands() {}

    public static void register(String name, Supplier<Command> factory) {
        factories.put(name, factory);
    }

    public static boolean has(String name) {
        return factories.containsKey(name);
    }

    /**
     * A new instance of the named command. An unknown name prints a warning and returns a
     * command that does nothing, so a typo in an auto doesn't crash the robot.
     */
    public static Command get(String name) {
        Supplier<Command> factory = factories.get(name);
        if (factory == null) {
            System.out.println("[NamedCommands] no command registered as '" + name + "'");
            return Commands.none().withName("Missing:" + name);
        }
        return factory.get();
    }

    /** Every registered name, in registration order. */
    public static Set<String> names() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    /** Removes everything (used by tests and the simulator). */
    public static void clear() {
        factories.clear();
    }
}
