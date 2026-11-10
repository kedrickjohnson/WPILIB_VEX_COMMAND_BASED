package com.robojacks.docs;

import com.robojacks.lib.command.Command;
import com.robojacks.lib.math.MathUtil;
import com.robojacks.lib.math.filter.SlewRateLimiter;
import java.util.function.DoubleSupplier;

// [slew]
/** Arcade drive with acceleration limits, so a slammed stick doesn't tip the robot or spin the wheels. */
public class ExampleDriverAssist extends Command {
    private final ExampleTrackingDrive m_drive;
    private final DoubleSupplier m_forward;
    private final DoubleSupplier m_rotation;
    // Full speed in no less than 0.3 s forward, 0.15 s for turning
    private final SlewRateLimiter m_forwardLimiter = new SlewRateLimiter(1.0 / 0.3);
    private final SlewRateLimiter m_rotationLimiter = new SlewRateLimiter(1.0 / 0.15);

    public ExampleDriverAssist(ExampleTrackingDrive drive, DoubleSupplier forward, DoubleSupplier rotation) {
        m_drive = drive;
        m_forward = forward;
        m_rotation = rotation;
        addRequirements(drive);
    }

    @Override
    public void initialize() {
        m_forwardLimiter.reset(0.0);
        m_rotationLimiter.reset(0.0);
    }

    @Override
    public void execute() {
        double forward = m_forwardLimiter.calculate(MathUtil.applyDeadband(m_forward.getAsDouble(), 0.05));
        double rotation = m_rotationLimiter.calculate(MathUtil.applyDeadband(m_rotation.getAsDouble(), 0.05));
        m_drive.arcadeDrive(forward, rotation);
    }
}
// [/slew]
