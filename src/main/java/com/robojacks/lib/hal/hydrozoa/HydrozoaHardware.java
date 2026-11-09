package com.robojacks.lib.hal.hydrozoa;

import com.robojacks.lib.hal.Clock;
import com.robojacks.lib.hal.CompetitionIO;
import com.robojacks.lib.hal.GamepadIO;
import com.robojacks.lib.hal.Hardware;
import com.robojacks.lib.hal.ImuIO;
import com.robojacks.lib.hal.MotorIO;

/**
 * The ONLY file that should touch Hydrozoa's API.
 *
 * <p>TODO: fill each method in using the Hydrozoa SDK (package {@code dev.vexide.hydrozoa}).
 * Look at the SDK's clawbot example and the Hydrozoa-Java-Template for the exact class
 * and method names, then replace each UnsupportedOperationException. Everything above
 * this layer (commands, odometry, subsystems) is already hardware-independent.
 *
 * <p>Mapping notes:
 * <ul>
 *   <li>Motor: set voltage in volts; read position (degrees -> divide by 360) and velocity (rpm).
 *       Apply {@code reversed} here by negating voltage in and position/velocity out.</li>
 *   <li>IMU: use the unwrapped "rotation" value (degrees, CW+), not the 0-360 "heading".</li>
 *   <li>Controller: VEX axes are -127..127 -> divide by 127.0.</li>
 *   <li>Clock: use the SDK's system time in microseconds/milliseconds; sleepUntil should
 *       sleep/yield so sensors keep updating (Hydrozoa needs vexTasksRun to be called
 *       regularly; the SDK's sleep likely handles that).</li>
 *   <li>Competition: read the competition status bits (enabled / autonomous / field connected).</li>
 * </ul>
 */
public class HydrozoaHardware implements Hardware {

    private final Clock clock = new Clock() {
        @Override
        public double nowSeconds() {
            throw todo("system time");
        }

        @Override
        public void sleepUntil(double timeSeconds) {
            throw todo("sleep");
        }
    };

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    public MotorIO motor(int port, boolean reversed) {
        return new MotorIO() {
            @Override
            public void setVoltage(double volts) {
                throw todo("motor setVoltage, port " + port);
            }

            @Override
            public double getPositionRotations() {
                throw todo("motor position");
            }

            @Override
            public double getVelocityRpm() {
                throw todo("motor velocity");
            }

            @Override
            public void resetPosition() {
                throw todo("motor reset position");
            }

            @Override
            public void setBrakeMode(boolean brake) {
                throw todo("motor brake mode");
            }
        };
    }

    @Override
    public ImuIO imu(int port) {
        return new ImuIO() {
            @Override
            public boolean isCalibrating() {
                throw todo("imu isCalibrating");
            }

            @Override
            public void calibrate() {
                throw todo("imu calibrate");
            }

            @Override
            public double getRotationDegreesCW() {
                throw todo("imu rotation");
            }

            @Override
            public void resetRotation() {
                throw todo("imu reset");
            }
        };
    }

    @Override
    public GamepadIO primaryController() {
        return controller(true);
    }

    @Override
    public GamepadIO partnerController() {
        return controller(false);
    }

    private GamepadIO controller(boolean primary) {
        return new GamepadIO() {
            @Override
            public double getAxis(int axis) {
                throw todo("controller axis");
            }

            @Override
            public boolean getButton(int button) {
                throw todo("controller button");
            }
        };
    }

    @Override
    public CompetitionIO competition() {
        return new CompetitionIO() {
            @Override
            public boolean isEnabled() {
                throw todo("competition enabled");
            }

            @Override
            public boolean isAutonomous() {
                throw todo("competition autonomous");
            }
        };
    }

    private static UnsupportedOperationException todo(String what) {
        return new UnsupportedOperationException("HydrozoaHardware TODO: " + what);
    }
}
