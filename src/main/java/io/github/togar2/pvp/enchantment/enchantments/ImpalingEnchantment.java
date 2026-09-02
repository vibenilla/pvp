package io.github.togar2.pvp.enchantment.enchantments;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.item.enchant.Enchantment;
import org.jetbrains.annotations.Nullable;

public class ImpalingEnchantment extends CombatEnchantment {
	public ImpalingEnchantment(EquipmentSlot... slotTypes) {
		super(Enchantment.IMPALING, slotTypes);
	}

	@Override
	public float getAttackDamage(int level, @Nullable Entity target,
	                             EnchantmentFeature feature, FeatureConfiguration configuration) {
		return EntityGroup.AQUATIC.contains(target) ? (float) level * 2.5F : 0.0F;
	}
}
