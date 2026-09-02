package io.github.togar2.pvp.feature.config;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class CombatFeatureRegistryTest {
    private static final Pos SPAWN = new Pos(8.0, 41.0, 8.0);

    @Test
    public void instanceChangeKeepsItemCooldowns(Env env) {
        var featureSet = CombatFeatures.modernVanilla();
        var cooldownFeature = featureSet.get(FeatureType.ITEM_COOLDOWN);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var player = env.createPlayer(env.createFlatInstance(), SPAWN);
            cooldownFeature.setCooldown(player, Material.ENDER_PEARL, 100);

            player.setInstance(env.createFlatInstance(), SPAWN).join();

            assertTrue(cooldownFeature.hasCooldown(player, Material.ENDER_PEARL));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void respawnClearsImpulseContext(Env env) {
        var node = CombatFeatures.modernVanilla().createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var player = env.createPlayer(env.createFlatInstance(), SPAWN);
            player.setRespawnPoint(SPAWN);
            player.setTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y, 100.0);

            player.kill();
            player.respawn();

            assertFalse(player.hasTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
