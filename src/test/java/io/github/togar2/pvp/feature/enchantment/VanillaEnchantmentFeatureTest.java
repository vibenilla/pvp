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
