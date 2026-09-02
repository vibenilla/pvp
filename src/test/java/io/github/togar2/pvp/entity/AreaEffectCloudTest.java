package io.github.togar2.pvp.entity;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class AreaEffectCloudTest {
    @Test
    public void cloudOnlyAffectsEntitiesOverlappingItsBox(Env env) {
        var featureSet = CombatFeatures.modernVanilla();
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var inside = new LivingEntity(EntityType.ZOMBIE);
            inside.setHealth(20.0F);
            inside.setInstance(instance, new Pos(9.0, 41.0, 9.0)).join();
            var above = new LivingEntity(EntityType.ZOMBIE);
            above.setHealth(20.0F);
            above.setNoGravity(true);
            above.setInstance(instance, new Pos(8.0, 43.5, 8.0)).join();

            var cloud = new AreaEffectCloud(new PotionContents(PotionType.SLOWNESS), null, featureSet.get(FeatureType.EFFECT));
            cloud.setInstance(instance, new Pos(8.0, 41.0, 8.0)).join();

            for (var tick = 0; tick < 12; tick++) env.tick();

            assertTrue(inside.hasEffect(PotionEffect.SLOWNESS));
            assertFalse(above.hasEffect(PotionEffect.SLOWNESS));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
