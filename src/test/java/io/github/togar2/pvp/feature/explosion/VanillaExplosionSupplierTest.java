package io.github.togar2.pvp.feature.explosion;

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
public final class VanillaExplosionSupplierTest {
    @Test
    public void explosionEndsImpulseGraceTimeButKeepsImpactPosition(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();
        var fallFeature = featureSet.get(FeatureType.FALL);

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
        player.setGameMode(GameMode.SURVIVAL);
        fallFeature.setIgnoreFallDamageFromCurrentImpulse(player, 64.0);

        instance.explode(2.0F, 40.0F, 0.0F, 4.0F);

        assertEquals(64.0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        assertEquals(0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));
    }
}
