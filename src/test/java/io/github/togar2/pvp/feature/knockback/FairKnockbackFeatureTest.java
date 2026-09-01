package io.github.togar2.pvp.feature.knockback;

import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class FairKnockbackFeatureTest {
    @Test
    public void fallCompensationUsesPerSecondGravity() {
        var aerodynamics = new Aerodynamics(0.08, 0.91, 0.98);
        var gravity = 0.08 * ServerFlag.SERVER_TICKS_PER_SECOND;
        var expected = 0.0;
        for (var tick = 0; tick < 3; tick++) expected = (expected - gravity) * 0.98;

        assertEquals(expected, FairKnockbackFeature.getCompensatedVerticalVelocity(aerodynamics, 0.0, 3), 1.0E-9);
    }
}
