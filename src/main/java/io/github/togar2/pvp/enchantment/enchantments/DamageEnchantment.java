package io.github.togar2.pvp.enchantment.enchantments;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.utils.PotionFlags;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.RegistryKey;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.Nullable;

public class DamageEnchantment extends CombatEnchantment {
    private final Type type;

    public DamageEnchantment(RegistryKey<Enchantment> enchantment, Type type, EquipmentSlot... slotTypes) {
        super(enchantment, Set.of(FeatureType.VERSION), slotTypes);
        this.type = type;
    }

    @Override
    public float getAttackDamage(int level, @Nullable Entity target,
                                 EnchantmentFeature feature, FeatureConfiguration configuration) {
        if (this.type == Type.ALL) {
            if (configuration.get(FeatureType.VERSION).legacy()) return level * 1.25F;
            return 1.0F + (float) Math.max(0, level - 1) * 0.5F;
        } else if (this.type == Type.UNDEAD && EntityGroup.UNDEAD.contains(target)) {
            return (float) level * 2.5F;
        } else {
            return this.type == Type.ARTHROPODS && EntityGroup.ARTHROPOD.contains(target) ? (float) level * 2.5F : 0.0F;
        }
    }

    @Override
    public void onTargetDamaged(LivingEntity user, Entity target, int level,
                                EnchantmentFeature feature, FeatureConfiguration configuration) {
        if (target instanceof LivingEntity livingEntity) {
            if (this.type == Type.ARTHROPODS && EntityGroup.ARTHROPOD.contains(livingEntity)) {
                var maxDurationSeconds = 1.5F + 0.5F * (level - 1);
                var durationSeconds = 1.5F + ThreadLocalRandom.current().nextFloat() * (maxDurationSeconds - 1.5F);
                var durationTicks = Math.round(durationSeconds * 20.0F);
                livingEntity.addEffect(new Potion(PotionEffect.SLOWNESS, (byte) 3, durationTicks, PotionFlags.defaultFlags()));
            }
        }
    }

    public enum Type {
        ALL, UNDEAD, ARTHROPODS
    }
}
