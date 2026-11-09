package com.robojacks.lib.drive;

/** V5 motor cartridges and their free speeds. */
public enum Cartridge {
    RED(100.0),
    GREEN(200.0),
    BLUE(600.0);

    public final double freeRpm;

    Cartridge(double freeRpm) {
        this.freeRpm = freeRpm;
    }
}
