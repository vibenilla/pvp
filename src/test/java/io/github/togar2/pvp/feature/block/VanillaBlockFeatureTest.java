package io.github.togar2.pvp.feature.block;

import io.github.togar2.pvp.feature.CombatFeatures;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaBlockFeatureTest {
    @Test
    public void blockedMeleeHitKnocksTheBlockerBack(Env env) {
        var node = CombatFeatures.modernVanilla().createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            player.setItemInOffHand(ItemStack.of(Material.SHIELD));
            player.refreshItemUse(PlayerHand.OFF, 72000L);
            player.refreshActiveHand(true, true, false);
            player.refreshOnGround(true);
            for (var tick = 0; tick < 6; tick++) env.tick();

            var attacker = new LivingEntity(EntityType.ZOMBIE);
            attacker.setInstance(instance, new Pos(0.0, 40.0, 2.0)).join();

            player.damage(new Damage(DamageType.MOB_ATTACK, attacker, attacker, null, 5.0F));

            var tps = ServerFlag.SERVER_TICKS_PER_SECOND;
            assertEquals(20.0F, player.getHealth());
            assertEquals(-0.15 * tps, player.getVelocity().z(), 1.0E-6);
            assertEquals(0.4 * tps, player.getVelocity().y(), 1.0E-6);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
