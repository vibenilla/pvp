package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class SnowballTest {
    @Test
    public void hitboxMarginGrowsWithFlightTime(Env env) {
        var instance = env.createFlatInstance();
        var target = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));

        var earlySnowball = new Snowball(null);
        earlySnowball.setInstance(instance, new Pos(8.55, 41.0, 7.0)).join();
        earlySnowball.setVelocity(new Vec(0.0, 0.0, 1.0 * ServerFlag.SERVER_TICKS_PER_SECOND));
        for (var tick = 0; tick < 3; tick++) env.tick();
        assertFalse(earlySnowball.isRemoved());

        var lateSnowball = new Snowball(null);
        lateSnowball.setInstance(instance, new Pos(8.55, 41.0, 0.0)).join();
        lateSnowball.setVelocity(new Vec(0.0, 0.0, 0.5 * ServerFlag.SERVER_TICKS_PER_SECOND));
        for (var tick = 0; tick < 25; tick++) env.tick();
        assertTrue(lateSnowball.isRemoved());
        assertTrue(target.isOnline());
    }
}
