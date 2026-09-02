package io.github.togar2.pvp.feature.cooldown;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaItemCooldownFeatureTest {
    @Test
    public void cooldownsCountServerTicks(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ITEM_COOLDOWN)
                .build();
        var cooldownFeature = featureSet.get(FeatureType.ITEM_COOLDOWN);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));

            cooldownFeature.setCooldown(player, Material.ENDER_PEARL, 20);
            for (var tick = 0; tick < 19; tick++) env.tick();
            assertTrue(cooldownFeature.hasCooldown(player, Material.ENDER_PEARL));

            env.tick();
            assertFalse(cooldownFeature.hasCooldown(player, Material.ENDER_PEARL));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
