package com.robojacks.lib.hardware;

/**
 * Settings for a {@link VexMotor}, built and applied the way REVLib's SparkMaxConfig is:
 * <pre>{@code
 * VexMotorConfig config = new VexMotorConfig();
 * config.inverted(true)
 *       .idleMode(IdleMode.kBrake)
 *       .smartCurrentLimit(2.5);
 * config.encoder
 *       .positionConversionFactor(kMetersPerMotorRotation)
 *       .velocityConversionFactor(kMetersPerMotorRotation / 60.0);
 * motor.configure(config);
 * }</pre>
 * Only settings you actually call are applied, so a shared base config can be layered with
 * {@link #apply(VexMotorConfig)}:
 * <pre>{@code
 * followerConfig.apply(globalConfig).follow(m_leftLeader);
 * }</pre>
 */
public class VexMotorConfig {
    /** What the motor does when stopped. */
    public enum IdleMode {
        /** Spin freely. */
        kCoast,
        /** Short the windings: stops quickly. */
        kBrake,
        /** Actively hold position (arms, lifts, claws). */
        kHold
    }

    Boolean inverted;
    IdleMode idleMode;
    Double currentLimitAmps;
    VexMotor followLeader;
    boolean followInverted;
    boolean disableFollow;

    /** Encoder settings: conversion factors. */
    public final EncoderConfig encoder = new EncoderConfig();

    /** Reverse this motor's direction. */
    public VexMotorConfig inverted(boolean inverted) {
        this.inverted = inverted;
        return this;
    }

    public VexMotorConfig idleMode(IdleMode mode) {
        this.idleMode = mode;
        return this;
    }

    /**
     * Current limit in amps, 0-2.5 (the V5 11 W motor's maximum and default). Lower limits
     * protect mechanisms that stall on purpose (claws) and keep motors cooler. With more than
     * 8 motors, VEXos lowers every motor's limit on its own so the battery isn't overloaded;
     * capping motors that don't need full current leaves more for the ones that do.
     */
    public VexMotorConfig smartCurrentLimit(double amps) {
        this.currentLimitAmps = amps;
        return this;
    }

    /**
     * Copy every voltage command the leader gets. The follower's own {@link #inverted} still
     * applies, so set each motor's direction for how it's mounted.
     */
    public VexMotorConfig follow(VexMotor leader) {
        return follow(leader, false);
    }

    /** Follow the leader, additionally flipping its commands. */
    public VexMotorConfig follow(VexMotor leader, boolean invert) {
        this.followLeader = leader;
        this.followInverted = invert;
        this.disableFollow = false;
        return this;
    }

    /** Stop following a leader. */
    public VexMotorConfig disableFollowerMode() {
        this.followLeader = null;
        this.disableFollow = true;
        return this;
    }

    /** Copies every setting that was set on {@code other} into this config. */
    public VexMotorConfig apply(VexMotorConfig other) {
        if (other.inverted != null) inverted = other.inverted;
        if (other.idleMode != null) idleMode = other.idleMode;
        if (other.currentLimitAmps != null) currentLimitAmps = other.currentLimitAmps;
        if (other.followLeader != null || other.disableFollow) {
            followLeader = other.followLeader;
            followInverted = other.followInverted;
            disableFollow = other.disableFollow;
        }
        encoder.apply(other.encoder);
        return this;
    }

    /** Conversion factors so the encoder reads in your units (meters, degrees...). */
    public static final class EncoderConfig {
        Double positionConversionFactor;
        Double velocityConversionFactor;

        /** Encoder position = motor rotations x this factor. */
        public EncoderConfig positionConversionFactor(double factor) {
            positionConversionFactor = factor;
            return this;
        }

        /** Encoder velocity = motor RPM x this factor. */
        public EncoderConfig velocityConversionFactor(double factor) {
            velocityConversionFactor = factor;
            return this;
        }

        void apply(EncoderConfig other) {
            if (other.positionConversionFactor != null) positionConversionFactor = other.positionConversionFactor;
            if (other.velocityConversionFactor != null) velocityConversionFactor = other.velocityConversionFactor;
        }
    }
}
