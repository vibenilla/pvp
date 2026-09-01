package io.github.togar2.pvp.feature.enchantment;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntitySetFireEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.ConditionalEffect;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.EntityEffect;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.item.enchant.ValueEffect;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Vanilla implementation of {@link EnchantmentFeature}
 * <p>
 * Utilizes the enchantment classes in the {@link io.github.togar2.pvp.enchantment} package.
 */
public class VanillaEnchantmentFeature implements EnchantmentFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEnchantmentFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ENCHANTMENT, VanillaEnchantmentFeature::new,
			CombatEnchantments.getAllFeatureDependencies()
	);
	public static final Tag<Boolean> FIRE_DURATION_ALREADY_SCALED = Tag.Transient("fireDurationAlreadyScaled");

	private final FeatureConfiguration configuration;

	public VanillaEnchantmentFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntitySetFireEvent.class, event -> {
			if (event.getEntity() instanceof LivingEntity living) {
				var fireTicks = event.getFireTicks();

				if (living.hasTag(FIRE_DURATION_ALREADY_SCALED)) {
					living.removeTag(FIRE_DURATION_ALREADY_SCALED);
				} else {
					fireTicks = this.getFireDuration(living, fireTicks);
				}

				if (living instanceof Player player && player.getGameMode().invulnerable()) {
					fireTicks = Math.min(fireTicks, 1);
				}

				event.setFireTicks(fireTicks);
			}
		});
	}

	public static void forEachEnchantment(Iterable<ItemStack> stacks, BiConsumer<CombatEnchantment, Integer> consumer) {
		for (ItemStack itemStack : stacks) {
			EnchantmentList enchantmentList = itemStack.get(DataComponents.ENCHANTMENTS);
			Set<RegistryKey<Enchantment>> enchantments = enchantmentList.enchantments().keySet();

			for (RegistryKey<Enchantment> enchantment : enchantments) {
				CombatEnchantment combatEnchantment = CombatEnchantments.get(enchantment);
				if (combatEnchantment == null) continue;

				consumer.accept(combatEnchantment, enchantmentList.level(enchantment));
			}
		}
	}

	@Override
	public int getEquipmentLevel(LivingEntity entity, RegistryKey<Enchantment> enchantment) {
		var combatEnchantment = CombatEnchantments.get(enchantment);
		if (combatEnchantment == null) return 0;

		var iterator = combatEnchantment.getEquipment(entity).values().iterator();

		int total = 0;
		while (iterator.hasNext()) {
			ItemStack itemStack = iterator.next();
			total += itemStack.get(DataComponents.ENCHANTMENTS).level(enchantment);
		}

		return total;
	}

	@Override
	public Map.Entry<EquipmentSlot, ItemStack> pickRandom(LivingEntity entity, RegistryKey<Enchantment> enchantment) {
		var combatEnchantment = CombatEnchantments.get(enchantment);
		if (combatEnchantment == null) return null;

		var equipmentMap = combatEnchantment.getEquipment(entity);
		if (equipmentMap.isEmpty()) return null;

		List<Map.Entry<EquipmentSlot, ItemStack>> possibleStacks = new ArrayList<>();

		for (Map.Entry<EquipmentSlot, ItemStack> entry : equipmentMap.entrySet()) {
			ItemStack itemStack = entry.getValue();

			if (!itemStack.isAir() && itemStack.get(DataComponents.ENCHANTMENTS).level(enchantment) > 0) {
				possibleStacks.add(entry);
			}
		}

		return possibleStacks.isEmpty() ? null :
				possibleStacks.get(ThreadLocalRandom.current().nextInt(possibleStacks.size()));
	}

	@Override
	public int getProtectionAmount(LivingEntity entity, DamageType damageType) {
		AtomicInteger result = new AtomicInteger();

		List<ItemStack> armorItems = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.armors()) {
			if (slot.isArmor() && !entity.getEquipment(slot).isAir()) {
				armorItems.add(entity.getEquipment(slot));
			}
		}

		forEachEnchantment(armorItems, (enchantment, level) ->
				result.addAndGet(enchantment.getProtectionAmount(level, damageType, this, this.configuration)));
		return result.get();
	}

	@Override
	public float getAttackDamage(ItemStack stack, EntityGroup group) {
		AtomicReference<Float> result = new AtomicReference<>((float) 0);
		stack.get(DataComponents.ENCHANTMENTS).enchantments().forEach((enchantment, level) -> {
			CombatEnchantment combatEnchantment = CombatEnchantments.get(enchantment);
			if (combatEnchantment == null) return;

			result.updateAndGet(v -> v + combatEnchantment.getAttackDamage(level, group, this, this.configuration));
		});

		return result.get();
	}

	@Override
	public double getExplosionKnockback(LivingEntity entity, double strength) {
		return strength;
	}

	@Override
	public int getFireDuration(LivingEntity entity, int duration) {
		var level = this.getEquipmentLevel(entity, Enchantment.FIRE_PROTECTION);
		var burningTime = Math.max(1.0 - level * 0.15, 0.0);

		return (int) Math.ceil(duration * burningTime);
	}

	@Override
	public double getKnockback(LivingEntity entity) {
		return this.getKnockback(entity, entity.getItemInMainHand());
	}

	@Override
	public double getKnockback(LivingEntity entity, ItemStack weapon) {
		var baseKnockback = (float) entity.getAttributeValue(Attribute.ATTACK_KNOCKBACK);

		return this.modifyConditionalValue(
				weapon, EffectComponent.KNOCKBACK, baseKnockback
		) / 2.0;
	}

	@Override
	public int getSweeping(LivingEntity entity) {
		return this.getEquipmentLevel(entity, Enchantment.SWEEPING_EDGE);
	}

	@Override
	public int getFireAspect(LivingEntity entity) {
		return this.getEquipmentLevel(entity, Enchantment.FIRE_ASPECT);
	}

	@Override
	public float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component, float base) {
		return this.modifyConditionalValue(stack, component, base, false);
	}

	@Override
	public float modifyConditionalValue(ItemStack stack, DataComponent<List<ConditionalEffect<ValueEffect>>> component,
	                                    float base, boolean includeConditionalEffects) {
		var value = base;
		var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

		for (var entry : stack.get(DataComponents.ENCHANTMENTS).enchantments().entrySet()) {
			var enchantment = enchantmentRegistry.get(entry.getKey());
			if (enchantment == null) continue;

			var effects = enchantment.effects().get(component);
			if (effects == null) continue;

			for (var effect : effects) {
				if (!includeConditionalEffects && effect.requirements() != null) continue;

				value = effect.effect().apply(value, entry.getValue());
			}
		}

		return value;
	}

	@Override
	public float modifyValue(ItemStack stack, DataComponent<ValueEffect> component, float base) {
		var value = base;
		var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

		for (var entry : stack.get(DataComponents.ENCHANTMENTS).enchantments().entrySet()) {
			var enchantment = enchantmentRegistry.get(entry.getKey());
			if (enchantment == null) continue;

			var effect = enchantment.effects().get(component);
			if (effect == null) continue;

			value = effect.apply(value, entry.getValue());
		}

		return value;
	}

	@Override
	public <T> T pickHighestLevel(ItemStack stack, DataComponent<List<T>> component, T fallback) {
		List<T> pickedEffects = null;
		var pickedLevel = 0;
		var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

		for (var entry : stack.get(DataComponents.ENCHANTMENTS).enchantments().entrySet()) {
			if (pickedEffects != null && pickedLevel >= entry.getValue()) continue;

			var enchantment = enchantmentRegistry.get(entry.getKey());
			if (enchantment == null) continue;

			var effects = enchantment.effects().get(component);
			if (effects == null) continue;

			pickedEffects = effects;
			pickedLevel = entry.getValue();
		}

		if (pickedEffects == null) return fallback;

		return pickedEffects.get(Math.min(pickedLevel, pickedEffects.size()) - 1);
	}

	@Override
	public int getProjectileIgniteTicks(ItemStack stack) {
		var ticks = 0;
		var enchantmentRegistry = MinecraftServer.getEnchantmentRegistry();

		for (var entry : stack.get(DataComponents.ENCHANTMENTS).enchantments().entrySet()) {
			var enchantment = enchantmentRegistry.get(entry.getKey());
			if (enchantment == null) continue;

			var effects = enchantment.effects().get(EffectComponent.PROJECTILE_SPAWNED);
			if (effects == null) continue;

			for (var effect : effects) {
				if (effect.requirements() != null) continue;

				ticks = Math.max(ticks, this.getProjectileIgniteTicks(effect.effect(), entry.getValue()));
			}
		}

		return ticks;
	}

	private int getProjectileIgniteTicks(EntityEffect effect, int level) {
		if (effect instanceof EntityEffect.Ignite ignite) {
			return (int) Math.floor(ignite.duration().calc(level) * ServerFlag.SERVER_TICKS_PER_SECOND);
		}

		if (effect instanceof EntityEffect.AllOf allOf) {
			var ticks = 0;

			for (var nestedEffect : allOf.effect()) {
				ticks = Math.max(ticks, this.getProjectileIgniteTicks(nestedEffect, level));
			}

			return ticks;
		}

		return 0;
	}

	@Override
	public boolean shouldUnbreakingPreventDamage(ItemStack stack) {
		int unbreakingLevel = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.UNBREAKING);
		if (unbreakingLevel <= 0) return false;

		var chance = isArmorItem(stack.material())
				? (2.0 * unbreakingLevel) / (10.0 + 5.0 * (unbreakingLevel - 1))
				: (double) unbreakingLevel / (2.0 + (unbreakingLevel - 1));

		return ThreadLocalRandom.current().nextDouble() < chance;
	}

	private static boolean isArmorItem(Material material) {
		var armorTag = MinecraftServer.process().material().getTag(Key.key("minecraft:enchantable/armor"));

		return armorTag != null && armorTag.contains(material.asKey());
	}

	@Override
	public void onUserDamaged(LivingEntity user, LivingEntity attacker) {
		forEachEnchantment(Arrays.asList(
				user.getBoots(), user.getLeggings(),
				user.getChestplate(), user.getHelmet(),
				user.getItemInMainHand(), user.getItemInOffHand()
		), (enchantment, level) -> enchantment.onUserDamaged(user, attacker, level, this, this.configuration));
	}

	@Override
	public void onTargetDamaged(LivingEntity user, Entity target) {
		this.onTargetDamaged(user, target, user.getItemInMainHand());
	}

	@Override
	public void onTargetDamaged(LivingEntity user, Entity target, ItemStack weapon) {
		forEachEnchantment(List.of(weapon), (enchantment, level) ->
				enchantment.onTargetDamaged(user, target, level, this, this.configuration));
	}
}
