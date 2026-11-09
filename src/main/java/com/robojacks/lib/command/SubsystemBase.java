package com.robojacks.lib.command;

/** Extend this for your subsystems. Auto-registers with the scheduler. */
public abstract class SubsystemBase implements Subsystem {
    private String name;

    public SubsystemBase(String name) {
        this.name = name;
        register();
    }

    public SubsystemBase() {
        this("Subsystem");
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
