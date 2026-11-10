package com.robojacks.lib.math.filter;

/**
 * Smooths a noisy signal, like WPILib's LinearFilter. Use the factory methods; call
 * {@link #calculate} once per loop.
 *
 * <pre>{@code
 * private final LinearFilter m_currentFilter = LinearFilter.movingAverage(10);
 * double smoothAmps = m_currentFilter.calculate(m_motor.getOutputCurrent());
 * }</pre>
 */
public class LinearFilter {
    private final double[] ffGains;
    private final double[] fbGains;
    private final double[] inputs;
    private final double[] outputs;

    /** General form: y[n] = sum(ff[i] * x[n-i]) - sum(fb[i] * y[n-1-i]). */
    public LinearFilter(double[] ffGains, double[] fbGains) {
        this.ffGains = ffGains.clone();
        this.fbGains = fbGains.clone();
        inputs = new double[ffGains.length];
        outputs = new double[fbGains.length];
    }

    /**
     * Exponential smoothing (single-pole IIR).
     *
     * @param timeConstant seconds; bigger is smoother but slower
     * @param period       loop period in seconds (0.010 for VexRobot)
     */
    public static LinearFilter singlePoleIIR(double timeConstant, double period) {
        double gain = Math.exp(-period / timeConstant);
        return new LinearFilter(new double[] {1.0 - gain}, new double[] {-gain});
    }

    /** Average of the last {@code taps} inputs. */
    public static LinearFilter movingAverage(int taps) {
        if (taps <= 0) {
            throw new IllegalArgumentException("taps must be positive");
        }
        double[] gains = new double[taps];
        java.util.Arrays.fill(gains, 1.0 / taps);
        return new LinearFilter(gains, new double[0]);
    }

    /** First-order high-pass: passes quick changes, removes steady offsets. */
    public static LinearFilter highPass(double timeConstant, double period) {
        double gain = Math.exp(-period / timeConstant);
        return new LinearFilter(new double[] {gain, -gain}, new double[] {-gain});
    }

    public double calculate(double input) {
        System.arraycopy(inputs, 0, inputs, 1, inputs.length - 1);
        if (inputs.length > 0) {
            inputs[0] = input;
        }
        double out = 0.0;
        for (int i = 0; i < ffGains.length; i++) {
            out += ffGains[i] * inputs[i];
        }
        for (int i = 0; i < fbGains.length; i++) {
            out -= fbGains[i] * outputs[i];
        }
        if (outputs.length > 0) {
            System.arraycopy(outputs, 0, outputs, 1, outputs.length - 1);
            outputs[0] = out;
        }
        return out;
    }

    public void reset() {
        java.util.Arrays.fill(inputs, 0.0);
        java.util.Arrays.fill(outputs, 0.0);
    }
}
