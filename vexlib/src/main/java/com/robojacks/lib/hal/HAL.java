package com.robojacks.lib.hal;

/** Global hardware holder. Set once in main() before constructing the robot. */
public final class HAL {
    private static Hardware hardware;

    private HAL() {}

    public static void set(Hardware hw) {
        hardware = hw;
    }

    public static Hardware get() {
        if (hardware == null) {
            throw new IllegalStateException("HAL.set(...) must be called before creating the robot");
        }
        return hardware;
    }

    public static double now() {
        return get().clock().nowSeconds();
    }
}
