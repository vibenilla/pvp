package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaFoodFeatureTest {
    @Test
    public void chorusFruitTeleportsUpToEightBlocksAway(Env env) {
        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));
        player.setGameMode(GameMode.SURVIVAL);

        var farthest = 0.0;
        for (var attempt = 0; attempt < 100; attempt++) {
            player.teleport(new Pos(8.0, 40.0, 8.0)).join();
            ChorusFruitUtil.tryChorusTeleport(player, 16.0F);
            farthest = Math.max(farthest, Math.abs(player.getPosition().x() - 8.0));
        }

        assertTrue(farthest > 4.5, "farthest teleport " + farthest);
    }

    @Test
    public void chorusFruitAppliesItsUseCooldown(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ITEM_COOLDOWN)
                .add(CombatFeatures.VANILLA_FOOD)
                .build();
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));
            player.setGameMode(GameMode.SURVIVAL);
            var fruit = ItemStack.of(Material.CHORUS_FRUIT);

            EventDispatcher.call(new PlayerFinishItemUseEvent(player, PlayerHand.MAIN, fruit, 32L));

            assertTrue(featureSet.get(FeatureType.ITEM_COOLDOWN).hasCooldown(player, fruit));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
