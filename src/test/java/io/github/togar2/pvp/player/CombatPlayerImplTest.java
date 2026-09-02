package io.github.togar2.pvp.player;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class CombatPlayerImplTest {
    @Test
    public void glidingBuildsForwardSpeedFromTheLookDirection(Env env) {
        MinecraftServer.getConnectionManager().setPlayerProvider(CombatPlayerImpl::new);
        var instance = env.createFlatInstance();
        var player = assertInstanceOf(CombatPlayerImpl.class,
                env.createPlayer(instance, new Pos(8.0, 60.0, 8.0, 0.0F, 30.0F)));
        player.setFlyingWithElytra(true);

        for (var tick = 0; tick < 20; tick++) env.tick();

        assertTrue(player.getVelocity().z() > 0.0);
        assertTrue(player.getVelocity().y() < 0.0);
    }
}
