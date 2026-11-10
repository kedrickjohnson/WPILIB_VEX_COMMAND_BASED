package com.robojacks.lib.math;

/**
 * Three numbers, used for standard deviations (x, y, heading) the way WPILib uses
 * {@code Matrix<N3, N1>}. Make one with {@link VecBuilder#fill}.
 */
public final class Vector3 {
    private final double a;
    private final double b;
    private final double c;

    public Vector3(double a, double b, double c) {
        this.a = a;
        this.b = b;
        this.c = c;
    }

    /** @param row 0, 1 or 2 */
    public double get(int row) {
        switch (row) {
            case 0: return a;
            case 1: return b;
            case 2: return c;
            default: throw new IndexOutOfBoundsException("row " + row);
        }
    }

    /** WPILib's {@code Matrix.get(row, col)} for a column vector. */
    public double get(int row, int col) {
        if (col != 0) {
            throw new IndexOutOfBoundsException("col " + col);
        }
        return get(row);
    }

    public Vector3 times(double scalar) {
        return new Vector3(a * scalar, b * scalar, c * scalar);
    }

    @Override
    public String toString() {
        return "[" + a + ", " + b + ", " + c + "]";
    }
}
