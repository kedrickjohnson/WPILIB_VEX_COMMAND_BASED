package com.robojacks.lib.hardware;

/**
 * Two valves driving one double-acting cylinder both ways, like WPILib's DoubleSolenoid:
 * one port pushes the cylinder out, the other pulls it back.
 */
public class DoubleSolenoid {
    public enum Value { kOff, kForward, kReverse }

    private final DigitalOutput forward;
    private final DigitalOutput reverse;
    private Value value = Value.kOff;

    /** Ports on the brain, e.g. {@code new DoubleSolenoid('C', 'D')}. */
    public DoubleSolenoid(char forwardPort, char reversePort) {
        this(0, forwardPort, reversePort);
    }

    public DoubleSolenoid(int expanderPort, char forwardPort, char reversePort) {
        forward = new DigitalOutput(expanderPort, forwardPort);
        reverse = new DigitalOutput(expanderPort, reversePort);
    }

    public void set(Value v) {
        value = v;
        forward.set(v == Value.kForward);
        reverse.set(v == Value.kReverse);
    }

    public Value get() {
        return value;
    }

    /** Forward becomes reverse and reverse becomes forward; off stays off. */
    public void toggle() {
        if (value == Value.kForward) {
            set(Value.kReverse);
        } else if (value == Value.kReverse) {
            set(Value.kForward);
        }
    }
}
