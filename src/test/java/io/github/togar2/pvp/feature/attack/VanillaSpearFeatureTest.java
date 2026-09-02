package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.player.PlayerStabEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaSpearFeatureTest {
    @Test
    public void jabRequiresTheAttackCharge(Env env) {
        CombatEnchantments.registerAll();
        var node = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_KNOCKBACK)
                .add(CombatFeatures.VANILLA_ATTACK_COOLDOWN)
                .add(CombatFeatures.VANILLA_SPEAR)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var attacker = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            attacker.setGameMode(GameMode.SURVIVAL);
            attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR));
            env.tick();

            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(instance, new Pos(0.0, 40.0, 2.5)).join();
            var damages = env.trackEvent(EntityDamageEvent.class, EventFilter.ENTITY, target);

            EventDispatcher.call(new PlayerStabEvent(attacker));
            env.tick();
            EventDispatcher.call(new PlayerStabEvent(attacker));

            damages.assertCount(1);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void jabStopsAtTheFirstBlock(Env env) {
        CombatEnchantments.registerAll();
        var node = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_KNOCKBACK)
                .add(CombatFeatures.VANILLA_ATTACK_COOLDOWN)
                .add(CombatFeatures.VANILLA_SPEAR)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var attacker = env.createPlayer(instance, new Pos(8.5, 40.0, 8.5));
            attacker.setGameMode(GameMode.SURVIVAL);
            attacker.setItemInMainHand(ItemStack.of(Material.NETHERITE_SPEAR));
            for (var y = 40; y <= 42; y++) instance.setBlock(8, y, 10, Block.STONE);
            env.tick();

            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(instance, new Pos(8.5, 40.0, 11.5)).join();
            target.setHealth(20.0F);
            var damages = env.trackEvent(EntityDamageEvent.class, EventFilter.ENTITY, target);

            EventDispatcher.call(new PlayerStabEvent(attacker));
            assertEquals(20.0F, target.getHealth());

            for (var y = 40; y <= 42; y++) instance.setBlock(8, y, 10, Block.AIR);
            for (var tick = 0; tick < 80; tick++) env.tick();

            EventDispatcher.call(new PlayerStabEvent(attacker));
            damages.assertCount(1);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }
}
