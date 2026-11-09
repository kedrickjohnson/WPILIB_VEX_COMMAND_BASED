package com.robojacks.lib.command;

/** Enforces WPILib's rule: a command inside a group can't also be scheduled on its own. */
final class CommandGroupUtil {
    private CommandGroupUtil() {}

    static void markComposed(Command... commands) {
        for (Command c : commands) {
            if (c.composed) {
                throw new IllegalArgumentException(
                        "Command '" + c.getName() + "' is already part of a group. "
                                + "Create a new instance instead of reusing it.");
            }
            if (c.isScheduled()) {
                throw new IllegalArgumentException(
                        "Command '" + c.getName() + "' is scheduled and can't be composed.");
            }
            c.composed = true;
        }
    }

    static boolean allRunWhenDisabled(Command... commands) {
        boolean all = true;
        for (Command c : commands) {
            all &= c.runsWhenDisabled();
        }
        return all;
    }
}
