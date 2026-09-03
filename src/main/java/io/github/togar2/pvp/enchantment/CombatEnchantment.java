package io.github.togar2.pvp.enchantment;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.RegistryKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public class CombatEnchantment {
    private final RegistryKey<Enchantment> enchantment;
    private final EquipmentSlot[] slotTypes;

    private final Set<FeatureType<?>> dependencies;

    public CombatEnchantment(RegistryKey<Enchantment> enchantment, EquipmentSlot... slotTypes) {
        this(enchantment, Set.of(), slotTypes);
    }

    public CombatEnchantment(RegistryKey<Enchantment> enchantment,
                             Set<FeatureType<?>> dependencies, EquipmentSlot... slotTypes) {
        this.enchantment = enchantment;
        this.dependencies = dependencies;
        this.slotTypes = slotTypes;
    }

    public RegistryKey<Enchantment> getEnchantment() {
        return this.enchantment;
    }

    public Set<FeatureType<?>> getDependencies() {
        return this.dependencies;
    }

    public Map<EquipmentSlot, ItemStack> getEquipment(LivingEntity entity) {
        var map = new HashMap<EquipmentSlot, ItemStack>();

        for (var slot : this.slotTypes) {
            var itemStack = entity.getEquipment(slot);
            if (!itemStack.isAir()) {
                map.put(slot, itemStack);
            }
        }

        return map;
    }

    public int getProtectionAmount(int level, DamageType damageType, EnchantmentFeature feature, FeatureConfiguration configuration) {
        return 0;
    }

    public float getAttackDamage(int level, @Nullable Entity target, EnchantmentFeature feature, FeatureConfiguration configuration) {
        return 0.0F;
    }

    public void onTargetDamaged(LivingEntity user, Entity target, int level, EnchantmentFeature feature, FeatureConfiguration configuration) {}
    public void onUserDamaged(LivingEntity user, LivingEntity attacker, int level, EnchantmentFeature feature, FeatureConfiguration configuration) {}

    public void onUserDamaged(LivingEntity user, LivingEntity attacker, int level, EquipmentSlot slot,
                              EnchantmentFeature feature, FeatureConfiguration configuration) {
        this.onUserDamaged(user, attacker, level, feature, configuration);
    }
}
