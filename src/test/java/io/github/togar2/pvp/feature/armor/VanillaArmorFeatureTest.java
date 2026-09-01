package io.github.togar2.pvp.feature.armor;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaArmorFeatureTest {
    @Test
    public void breachReducesTheAbsorbedFraction(Env env) {
        CombatEnchantments.registerAll();
        var armorFeature = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_ARMOR)
                .build()
                .get(FeatureType.ARMOR);

        var instance = env.createFlatInstance();
        var victim = new LivingEntity(EntityType.ZOMBIE);
        victim.setInstance(instance, new Pos(0.0, 40.0, 0.0)).join();
        victim.getAttribute(Attribute.ARMOR).setBaseValue(20.0);
        victim.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(12.0);

        var attacker = new LivingEntity(EntityType.ZOMBIE);
        attacker.setInstance(instance, new Pos(0.0, 40.0, 1.0)).join();
        var damageType = MinecraftServer.getDamageTypeRegistry().get(DamageType.PLAYER_ATTACK);

        attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
        assertEquals(2.8F, armorFeature.getDamageWithProtection(victim, damageType, 10.0F, attacker), 1.0E-3);

        attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD).with(
                DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.BREACH, 4)
        ));
        assertEquals(8.8F, armorFeature.getDamageWithProtection(victim, damageType, 10.0F, attacker), 1.0E-3);
    }
}
