package io.github.togar2.pvp.feature.cooldown;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventNode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaAttackCooldownFeatureTest {
    @Test
    public void mainHandItemTypeChangeResetsCooldown(Env env) {
        var featureSet = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ATTACK_COOLDOWN)
                .build();
        var cooldownFeature = featureSet.get(FeatureType.ATTACK_COOLDOWN);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
            env.tick();

            cooldownFeature.resetCooldownProgress(player);
            for (var tick = 0; tick < 10; tick++) env.tick();
            var progressBefore = cooldownFeature.getAttackCooldownProgress(player);
            assertTrue(progressBefore > 0.5, "progress " + progressBefore);

            player.setItemInMainHand(ItemStack.of(Material.IRON_SWORD));
            env.tick();

            assertTrue(cooldownFeature.getAttackCooldownProgress(player) < 0.2);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void sameItemTypeWithDifferentComponentsKeepsCooldown(Env env) {
        var featureSet = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ATTACK_COOLDOWN)
                .build();
        var cooldownFeature = featureSet.get(FeatureType.ATTACK_COOLDOWN);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
            env.tick();

            cooldownFeature.resetCooldownProgress(player);
            for (var tick = 0; tick < 10; tick++) env.tick();
            var progressBefore = cooldownFeature.getAttackCooldownProgress(player);

            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.DAMAGE, 10));
            env.tick();

            assertTrue(cooldownFeature.getAttackCooldownProgress(player) > progressBefore);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

}
