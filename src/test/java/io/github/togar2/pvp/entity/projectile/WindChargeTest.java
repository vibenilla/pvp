package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import io.github.togar2.pvp.feature.fall.FallFeature;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class WindChargeTest {
    @Test
    public void explosionHappensAtTheImpactPoint(Env env) {
        var instance = env.createFlatInstance();
        for (var y = 40; y <= 44; y++) instance.setBlock(12, y, 8, Block.STONE);
        var connection = env.createConnection();
        var viewer = connection.connect(instance, new Pos(8.0, 40.0, 8.0));

        var windCharge = new WindCharge(null, FallFeature.NO_OP);
        windCharge.setInstance(instance, new Pos(9.0, 41.5, 8.0)).join();
        windCharge.setVelocity(new Vec(1.5 * ServerFlag.SERVER_TICKS_PER_SECOND, 0.0, 0.0));

        var particles = connection.trackIncoming(ParticlePacket.class);
        for (var tick = 0; tick < 5; tick++) env.tick();

        var bursts = particles.collect().stream().filter(packet -> packet.particle().equals(Particle.GUST_EMITTER_LARGE)).toList();
        assertEquals(1, bursts.size());
        assertTrue(bursts.getFirst().x() > 11.6, "explosion center x " + bursts.getFirst().x());
        assertTrue(viewer.isOnline());
    }

    @Test
    public void explosionUsesCurrentPositionAsImpactPosition(Env env) {
        var fallFeature = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .build()
                .get(FeatureType.FALL);

        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(0.0, 70.0, 0.0));
        player.setGameMode(GameMode.SURVIVAL);
        fallFeature.setIgnoreFallDamageFromCurrentImpulse(player, 64.0);

        var windCharge = new WindCharge(null, fallFeature);
        windCharge.setInstance(instance, new Pos(0.0, 70.0, 1.0)).join();
        windCharge.onHit(player);

        assertEquals(70.0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        assertEquals(40, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));
    }
}
