package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class WindChargeTest {
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
