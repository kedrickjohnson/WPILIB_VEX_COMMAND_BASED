package com.robojacks.lib.hal;

/** Time source. Lets the same code run on the brain and in a faster-than-real-time sim. */
public interface Clock {
    /** Seconds since program start. */
    double nowSeconds();

    /** Blocks (or, in sim, jumps) until {@code timeSeconds}. */
    void sleepUntil(double timeSeconds);
}
