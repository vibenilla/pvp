package io.github.togar2.pvp.enchantment.enchantments;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.enchant.Enchantment;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ThornsEnchantment extends CombatEnchantment {
    public ThornsEnchantment(EquipmentSlot... slotTypes) {
        super(Enchantment.THORNS, Set.of(FeatureType.ITEM_DAMAGE), slotTypes);
    }

    @Override
    public void onUserDamaged(LivingEntity user, LivingEntity attacker, int level, EquipmentSlot slot,
                              EnchantmentFeature feature, FeatureConfiguration configuration) {
        var random = ThreadLocalRandom.current();
        if (!shouldDamageAttacker(level, random)) return;

        if (attacker != null) {
            attacker.damage(new Damage(DamageType.THORNS, user, user, null, getDamageAmount(random)));
        }

        configuration.get(FeatureType.ITEM_DAMAGE).damageEquipment(user, slot, 2);
    }

    private static boolean shouldDamageAttacker(int level, ThreadLocalRandom random) {
        if (level <= 0) return false;
        return random.nextFloat() < 0.15F * level;
    }

    private static float getDamageAmount(ThreadLocalRandom random) {
        return 1.0F + random.nextFloat() * 4.0F;
    }
}
