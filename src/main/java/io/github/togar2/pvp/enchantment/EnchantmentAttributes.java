package io.github.togar2.pvp.enchantment;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.EffectComponent;

import java.util.function.BiConsumer;

public final class EnchantmentAttributes {
	private EnchantmentAttributes() {
	}

	public static void forEachModifier(ItemStack stack, EquipmentSlot slot, BiConsumer<Attribute, AttributeModifier> consumer) {
		var enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null) return;

		var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

		enchantments.enchantments().forEach((key, level) -> {
			var enchantment = enchantmentRegistry.get(key);
			if (enchantment == null) return;
			if (enchantment.slots().stream().noneMatch(group -> group.contains(slot))) return;

			var effects = enchantment.effects().get(EffectComponent.ATTRIBUTES);
			if (effects == null) return;

			for (var effect : effects) {
				var id = Key.key(effect.id().namespace(), effect.id().value() + "/" + slot.nbtName());
				consumer.accept(effect.attribute(), new AttributeModifier(id, effect.amount().calc(level), effect.operation()));
			}
		});
	}

	public static void updateEquipmentAttributes(LivingEntity entity, ItemStack oldStack, ItemStack newStack, EquipmentSlot slot) {
		forEachModifier(oldStack, slot, (attribute, modifier) -> entity.getAttribute(attribute).removeModifier(modifier.id()));
		forEachModifier(newStack, slot, (attribute, modifier) -> entity.getAttribute(attribute).addModifier(modifier));
	}
}
