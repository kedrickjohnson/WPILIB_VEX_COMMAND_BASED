package com.robojacks.lib.sim;

import com.robojacks.lib.telemetry.Telemetry;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

/**
 * Writes the simulator's telemetry to a file you can open in the dashboard page (Open log),
 * which replays it: graphs and the robot driving on the field.
 *
 * <pre>{@code
 * SimLog.start("sim-telemetry.log", 0.05); // 20 lines per second
 * ... run the simulation ...
 * SimLog.stop();
 * }</pre>
 */
public final class SimLog {
    private static PrintWriter out;

    private SimLog() {}

    /** Starts logging telemetry lines to {@code path}, one every {@code periodSeconds}. */
    public static void start(String path, double periodSeconds) {
        stop();
        try {
            out = new PrintWriter(path);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Can't write " + path + ": " + e.getMessage(), e);
        }
        PrintWriter w = out;
        Telemetry.setSink(w::println);
        Telemetry.setPeriod(periodSeconds);
        Telemetry.setEnabled(true);
    }

    /** Closes the file and sends telemetry back to the console. */
    public static void stop() {
        if (out != null) {
            out.close();
            out = null;
        }
        Telemetry.setSink(System.out::println);
    }
}
