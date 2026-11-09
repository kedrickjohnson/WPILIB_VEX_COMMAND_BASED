package com.robojacks.lib.hardware;

import com.robojacks.lib.hal.Cartridge;
import com.robojacks.lib.hal.HAL;
import com.robojacks.lib.hal.MotorIO;
import com.robojacks.lib.hal.MotorIO.NeutralMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A V5 Smart Motor, used like a REV SPARK MAX:
 * <pre>{@code
 * private final VexMotor m_armMotor = new VexMotor(8, Cartridge.GREEN);
 * private final VexEncoder m_armEncoder = m_armMotor.getEncoder();
 *
 * VexMotorConfig config = new VexMotorConfig();
 * config.idleMode(IdleMode.kHold).smartCurrentLimit(2.5);
 * config.encoder.positionConversionFactor(360.0 * kArmGearRatio); // degrees
 * m_armMotor.configure(config);
 * }</pre>
 * The cartridge goes in the constructor because it's hardware, like a SPARK's motor type.
 */
public class VexMotor {
    private final MotorIO io;
    private final int port;
    private final Cartridge cartridge;
    private final VexEncoder encoder = new VexEncoder(this);

    private double positionFactor = 1.0;
    private double velocityFactor = 1.0;
    private VexMotor leader;
    private final List<VexMotor> followers = new ArrayList<>();
    private final List<Boolean> followerInverts = new ArrayList<>();
    private double lastVolts;

    public VexMotor(int port, Cartridge cartridge) {
        this.port = port;
        this.cartridge = cartridge;
        this.io = HAL.get().motor(port, false, cartridge);
    }

    /** Applies the settings that were set on {@code config}; anything not set is left alone. */
    public void configure(VexMotorConfig config) {
        if (config.inverted != null) {
            io.setInverted(config.inverted);
        }
        if (config.idleMode != null) {
            switch (config.idleMode) {
                case kBrake: io.setNeutralMode(NeutralMode.BRAKE); break;
                case kHold: io.setNeutralMode(NeutralMode.HOLD); break;
                default: io.setNeutralMode(NeutralMode.COAST); break;
            }
        }
        if (config.currentLimitAmps != null) {
            io.setCurrentLimit(config.currentLimitAmps);
        }
        if (config.encoder.positionConversionFactor != null) {
            positionFactor = config.encoder.positionConversionFactor;
        }
        if (config.encoder.velocityConversionFactor != null) {
            velocityFactor = config.encoder.velocityConversionFactor;
        }
        if (config.disableFollow && leader != null) {
            leader.removeFollower(this);
            leader = null;
        }
        if (config.followLeader != null) {
            if (config.followLeader == this) {
                throw new IllegalArgumentException("A motor can't follow itself (port " + port + ")");
            }
            if (leader != null) {
                leader.removeFollower(this);
            }
            leader = config.followLeader;
            leader.addFollower(this, config.followInverted);
        }
    }

    private void addFollower(VexMotor follower, boolean invert) {
        followers.add(follower);
        followerInverts.add(invert);
    }

    private void removeFollower(VexMotor follower) {
        int i = followers.indexOf(follower);
        if (i >= 0) {
            followers.remove(i);
            followerInverts.remove(i);
        }
    }

    public boolean isFollower() {
        return leader != null;
    }

    // ----- Output -----

    /** Percent output, -1..1. Ignored on a follower (it copies its leader). */
    public void set(double speed) {
        setVoltage(12.0 * speed);
    }

    /** -12..12 volts. 0 applies the idle mode. Ignored on a follower. */
    public void setVoltage(double volts) {
        if (leader != null) {
            return;
        }
        drive(Math.max(-12.0, Math.min(12.0, volts)));
    }

    private void drive(double volts) {
        lastVolts = volts;
        io.setVoltage(volts);
        for (int i = 0; i < followers.size(); i++) {
            followers.get(i).drive(followerInverts.get(i) ? -volts : volts);
        }
    }

    public void stopMotor() {
        setVoltage(0.0);
    }

    /**
     * Move to a position with the motor's built-in PID, in the encoder's converted units
     * (e.g. degrees if the position factor converts to degrees). Followers do the same move.
     *
     * @param maxRpm speed limit for the move, in motor RPM
     */
    public void setPositionTarget(double position, double maxRpm) {
        if (leader != null) {
            return;
        }
        // Undo the encoder's setPosition() offset and conversion factor to get motor rotations.
        moveTo((position - encoder.offset()) / positionFactor, maxRpm);
    }

    private void moveTo(double rotations, double maxRpm) {
        io.setPositionTarget(rotations, maxRpm);
        for (int i = 0; i < followers.size(); i++) {
            followers.get(i).moveTo(followerInverts.get(i) ? -rotations : rotations, maxRpm);
        }
    }

    // ----- Feedback -----

    /** The built-in encoder, reading in the configured units. */
    public VexEncoder getEncoder() {
        return encoder;
    }

    /** Last commanded voltage. */
    public double getAppliedVoltage() {
        return lastVolts;
    }

    public double getOutputCurrent() {
        return io.getCurrentAmps();
    }

    public double getMotorTemperature() {
        return io.getTemperatureCelsius();
    }

    public boolean isConnected() {
        return io.isConnected();
    }

    public int getPort() {
        return port;
    }

    public Cartridge getCartridge() {
        return cartridge;
    }

    // ----- For VexEncoder -----

    MotorIO io() {
        return io;
    }

    double positionFactor() {
        return positionFactor;
    }

    double velocityFactor() {
        return velocityFactor;
    }
}
