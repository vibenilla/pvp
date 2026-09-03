package io.github.togar2.pvp.feature.enchantment;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.component.DataComponent;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.ConditionalEffect;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.enchant.ValueEffect;
import net.minestom.server.registry.RegistryKey;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Combat feature which manages enchantments.
 * <p>
 * It contains methods which other features depend on,
 * such as methods for the protection amount of armor enchantments and the attack damage amount of weapon enchantments.
 */
public interface EnchantmentFeature extends CombatFeature {
    EnchantmentFeature NO_OP = new EnchantmentFeature() {
        @Override
        public int getEquipmentLevel(LivingEntity entity, RegistryKey<Enchantment> enchantment) {
            return 0;
        }

        @Override
        public Map.Entry<EquipmentSlot, ItemStack> pickRandom(LivingEntity entity, RegistryKey<Enchantment> enchantment) {
            return null;
        }

        @Override
        public int getProtectionAmount(LivingEntity entity, DamageType damageType) {
            return 0;
        }

        @Override
        public float getAttackDamage(ItemStack stack, @Nullable Entity target) {
            return 0;
        }

        @Override
        public double getExplosionKnockback(LivingEntity entity, double strength) {
            return strength;
        }

        @Override
        public int getFireDuration(LivingEntity entity, int duration) {
            return duration;
        }

        @Override
        public double getKnockback(LivingEntity entity) {
            return 0;
        }

        @Override
        public double getKnockback(LivingEntity entity, ItemStack weapon) {
            return 0;
        }

        @Override
        public int getSweeping(LivingEntity entity) {
            return 0;
        }

        @Override
        public int getFireAspect(LivingEntity entity) {
            return 0;
        }

        @Override
        public float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component, float base) {
            return base;
        }

        @Override
        public float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component,
                                            float base, boolean includeConditionalEffects) {
            return base;
        }

        @Override
        public float modifyValue(ItemStack stack, DataComponent<ValueEffect> component, float base) {
            return base;
        }

        @Override
        public <T> T pickHighestLevel(ItemStack stack, DataComponent<List<T>> component, T fallback) {
            return fallback;
        }

        @Override
        public int getProjectileIgniteTicks(ItemStack stack) {
            return 0;
        }

        @Override
        public boolean shouldUnbreakingPreventDamage(ItemStack stack) {
            return false;
        }

        @Override
        public void onUserDamaged(LivingEntity user, LivingEntity attacker) {}

        @Override
        public void onTargetDamaged(LivingEntity user, Entity target) {}

        @Override
        public void onTargetDamaged(LivingEntity user, Entity target, ItemStack weapon) {}
    };

    int getEquipmentLevel(LivingEntity entity, RegistryKey<Enchantment> enchantment);

    Map.Entry<EquipmentSlot, ItemStack> pickRandom(LivingEntity entity, RegistryKey<Enchantment> enchantment);

    int getProtectionAmount(LivingEntity entity, DamageType damageType);

    float getAttackDamage(ItemStack stack, @Nullable Entity target);

    double getExplosionKnockback(LivingEntity entity, double strength);

    int getFireDuration(LivingEntity entity, int duration);

    double getKnockback(LivingEntity entity);

    double getKnockback(LivingEntity entity, ItemStack weapon);

    int getSweeping(LivingEntity entity);

    int getFireAspect(LivingEntity entity);

    float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component, float base);

    float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component,
                                 float base, boolean includeConditionalEffects);

    float modifyValue(ItemStack stack, DataComponent<ValueEffect> component, float base);

    <T> T pickHighestLevel(ItemStack stack, DataComponent<List<T>> component, T fallback);

    int getProjectileIgniteTicks(ItemStack stack);

    boolean shouldUnbreakingPreventDamage(ItemStack stack);

    void onUserDamaged(LivingEntity user, LivingEntity attacker);

    void onTargetDamaged(LivingEntity user, Entity target);

    void onTargetDamaged(LivingEntity user, Entity target, ItemStack weapon);
}
