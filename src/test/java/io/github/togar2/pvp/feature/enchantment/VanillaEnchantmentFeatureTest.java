package io.github.togar2.pvp.feature.enchantment;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.component.DataComponents;
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
