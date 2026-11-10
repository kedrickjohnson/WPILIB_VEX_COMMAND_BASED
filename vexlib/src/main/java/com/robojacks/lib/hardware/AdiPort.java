package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.AdiPortIO;
import com.robojacks.lib.hal.HAL;

/** Shared helper for the 3-wire classes: turns 'A'-'H' into a port and checks it. */
final class AdiPort {
    private AdiPort() {}

    static int number(char letter) {
        char c = Character.toUpperCase(letter);
        if (c < 'A' || c > 'H') {
            throw new IllegalArgumentException("3-wire port must be 'A' to 'H', got '" + letter + "'");
        }
        return c - 'A' + 1;
    }

    static AdiPortIO open(int expanderPort, char letter, AdiPortIO.Mode mode) {
        AdiPortIO io = HAL.get().adiPort(expanderPort, number(letter));
        io.configure(mode);
        return io;
    }
}
