package io.github.togar2.pvp.feature.enchantment;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaEnchantmentFeatureTest {
    @Test
    public void unbreakingChancesFollowTheEnchantmentData(Env env) {
        CombatEnchantments.registerAll();
        var enchantmentFeature = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .build()
                .get(FeatureType.ENCHANTMENT);

        var sword = ItemStack.of(Material.DIAMOND_SWORD).with(
                DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.UNBREAKING, 3)
        );
        var chestplate = ItemStack.of(Material.DIAMOND_CHESTPLATE).with(
                DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.UNBREAKING, 3)
        );

        assertEquals(0.75, this.preventedFraction(enchantmentFeature, sword), 0.03);
        assertEquals(0.30, this.preventedFraction(enchantmentFeature, chestplate), 0.03);
    }

    @Test
    public void fireProtectionShortensBurningThroughTheAttribute(Env env) {
        var featureSet = CombatFeatures.modernVanilla();
        var enchantmentFeature = featureSet.get(FeatureType.ENCHANTMENT);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var player = env.createPlayer(env.createFlatInstance(), new Pos(8.0, 41.0, 8.0));
            assertEquals(100, enchantmentFeature.getFireDuration(player, 100));

            player.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(Material.DIAMOND_BOOTS).with(
                    DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.FIRE_PROTECTION, 4)
            ));

            assertEquals(40, enchantmentFeature.getFireDuration(player, 100));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void sweepingEdgeRaisesTheDamageRatioAttribute(Env env) {
        var featureSet = CombatFeatures.modernVanilla();
        var sweepingFeature = featureSet.get(FeatureType.SWEEPING);
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var player = env.createPlayer(env.createFlatInstance(), new Pos(8.0, 41.0, 8.0));
            assertEquals(1.0F, sweepingFeature.getSweepingDamage(player, 8.0F));

            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD).with(
                    DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.SWEEPING_EDGE, 3)
            ));

            assertEquals(7.0F, sweepingFeature.getSweepingDamage(player, 8.0F), 1.0E-4F);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private double preventedFraction(EnchantmentFeature enchantmentFeature, ItemStack stack) {
        var rolls = 20000;
        var prevented = 0;
        for (var roll = 0; roll < rolls; roll++) {
            if (enchantmentFeature.shouldUnbreakingPreventDamage(stack)) prevented++;
        }
        return prevented / (double) rolls;
    }

    @Test
    public void knockbackEnchantmentCountsHalfPowerPerLevel(Env env) {
        CombatEnchantments.registerAll();
        var enchantmentFeature = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .build()
                .get(FeatureType.ENCHANTMENT);

        var instance = env.createFlatInstance();
        var attacker = new LivingEntity(EntityType.ZOMBIE);
        attacker.setInstance(instance, new Pos(0.0, 40.0, 0.0)).join();

        var plainSword = ItemStack.of(Material.DIAMOND_SWORD);
        var knockbackSword = plainSword.with(
                DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.KNOCKBACK, 2)
        );

        assertEquals(0.0, enchantmentFeature.getKnockback(attacker, plainSword));
        assertEquals(1.0, enchantmentFeature.getKnockback(attacker, knockbackSword));
    }
}
