package io.github.togar2.pvp.potion.effect;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.food.FoodFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.color.AlphaColor;
import net.minestom.server.color.Color;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class CombatPotionEffect {
	private final Map<Attribute, AttributeModifier> attributeModifiers = new HashMap<>();
	private Map<Attribute, AttributeModifier> legacyAttributeModifiers;
	private final PotionEffect potionEffect;
	private final Function<Potion, Particle> particleSupplier;
	private @Nullable SoundEvent soundOnAdded;

	public CombatPotionEffect(PotionEffect potionEffect) {
		this.potionEffect = potionEffect;
		this.particleSupplier = potion -> {
			int alpha = potion.isAmbient() ? 38 : 255;
			return Particle.ENTITY_EFFECT.withColor(new AlphaColor(alpha, new Color(potion.effect().color())));
		};
	}

	public CombatPotionEffect(PotionEffect potionEffect, Function<Potion, Particle> particleSupplier) {
		this.potionEffect = potionEffect;
		this.particleSupplier = particleSupplier;
	}

	public PotionEffect getPotionEffect() {
		return this.potionEffect;
	}

	public Particle getParticle(Potion potion) {
		return this.particleSupplier.apply(potion);
	}

	public CombatPotionEffect addAttributeModifier(Attribute attribute, Key id,
	                                               double amount, AttributeOperation operation) {
        this.attributeModifiers.put(attribute, new AttributeModifier(id, amount, operation));
		return this;
	}

	public CombatPotionEffect addLegacyAttributeModifier(Attribute attribute, Key id,
	                                                     double amount, AttributeOperation operation) {
		if (this.legacyAttributeModifiers == null)
            this.legacyAttributeModifiers = new HashMap<>();
        this.legacyAttributeModifiers.put(attribute, new AttributeModifier(id, amount, operation));
		return this;
	}

	public CombatPotionEffect withSoundOnAdded(SoundEvent soundOnAdded) {
		this.soundOnAdded = soundOnAdded;
		return this;
	}

	public void applyUpdateEffect(LivingEntity entity, int amplifier,
	                              ExhaustionFeature exhaustionFeature, FoodFeature foodFeature) {
		if (this.potionEffect == PotionEffect.REGENERATION) {
			if (entity.getHealth() < entity.getAttributeValue(Attribute.MAX_HEALTH)) {
				entity.setHealth(entity.getHealth() + 1);
			}
			return;
		} else if (this.potionEffect == PotionEffect.POISON) {
			if (entity.getHealth() > 1.0F) {
				entity.damage(DamageType.MAGIC, 1.0F);
			}
			return;
		} else if (this.potionEffect == PotionEffect.WITHER) {
			entity.damage(DamageType.WITHER, 1.0F);
			return;
		}

		if (entity instanceof Player player) {
			if (this.potionEffect == PotionEffect.HUNGER) {
				exhaustionFeature.applyHungerEffect(player, amplifier);
				return;
			} else if (this.potionEffect == PotionEffect.SATURATION) {
				foodFeature.applySaturationEffect(player, amplifier);
				return;
			}
		}

		if (this.potionEffect == PotionEffect.INSTANT_DAMAGE || this.potionEffect == PotionEffect.INSTANT_HEALTH) {
			EntityGroup entityGroup = EntityGroup.ofEntity(entity);

			if (this.shouldHeal(entityGroup)) {
				entity.setHealth(entity.getHealth() + (float) Math.max(4 << amplifier, 0));
			} else {
				entity.damage(DamageType.MAGIC, (float) (6 << amplifier));
			}
		}
	}

	public void applyInstantEffect(@Nullable Entity source, @Nullable Entity attacker, LivingEntity target,
	                               int amplifier, double proximity, ExhaustionFeature exhaustionFeature, FoodFeature foodFeature) {
		EntityGroup targetGroup = EntityGroup.ofEntity(target);

		if (this.potionEffect != PotionEffect.INSTANT_DAMAGE && this.potionEffect != PotionEffect.INSTANT_HEALTH) {
            this.applyUpdateEffect(target, amplifier, exhaustionFeature, foodFeature);
			return;
		}

		if (this.shouldHeal(targetGroup)) {
			int amount = (int) (proximity * (double) (4 << amplifier) + 0.5D);
			target.setHealth(target.getHealth() + (float) amount);
		} else {
			int amount = (int) (proximity * (double) (6 << amplifier) + 0.5D);
			if (source == null) {
				target.damage(DamageType.MAGIC, (float) amount);
			} else {
				target.damage(new Damage(DamageType.INDIRECT_MAGIC, source, attacker, null, (float) amount));
			}
		}
	}

	private boolean shouldHeal(EntityGroup group) {
		return (group.isUndead() && this.potionEffect == PotionEffect.INSTANT_DAMAGE)
				|| (!group.isUndead() && this.potionEffect == PotionEffect.INSTANT_HEALTH);
	}

	public boolean canApplyUpdateEffect(int duration, int amplifier) {
		if (this.isInstant() || this.potionEffect == PotionEffect.SATURATION) return duration >= 1;

		int applyInterval;
		if (this.potionEffect == PotionEffect.REGENERATION) {
			applyInterval = 50 >> amplifier;
		} else if (this.potionEffect == PotionEffect.POISON) {
			applyInterval = 25 >> amplifier;
		} else if (this.potionEffect == PotionEffect.WITHER) {
			applyInterval = 40 >> amplifier;
		} else {
			return this.potionEffect == PotionEffect.HUNGER;
		}

		if (applyInterval > 0) {
			return duration % applyInterval == 0;
		} else {
			return true;
		}
	}

	public boolean isInstant() {
		return this.potionEffect.instantaneous();
	}

	public void onStarted(LivingEntity entity, int amplifier) {}

	public void onApplied(LivingEntity entity, int amplifier, CombatVersion version) {
		Map<Attribute, AttributeModifier> modifiers;
		if (version.legacy() && this.legacyAttributeModifiers != null) {
			modifiers = this.legacyAttributeModifiers;
		} else {
			modifiers = this.attributeModifiers;
		}

		modifiers.forEach((attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);
			instance.removeModifier(modifier);
			instance.addModifier(new AttributeModifier(modifier.id(), this.adjustModifierAmount(amplifier, modifier), modifier.operation()));
		});

		if (this.soundOnAdded != null) {
			ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
					this.soundOnAdded,
					entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F,
					1.0F
			), entity);
		}
	}

	public void onRemoved(LivingEntity entity, int amplifier, CombatVersion version) {
		Map<Attribute, AttributeModifier> modifiers;
		if (version.legacy() && this.legacyAttributeModifiers != null) {
			modifiers = this.legacyAttributeModifiers;
		} else {
			modifiers = this.attributeModifiers;
		}

		modifiers.forEach((attribute, modifier) ->
				entity.getAttribute(attribute).removeModifier(modifier));
	}

	private double adjustModifierAmount(int amplifier, AttributeModifier modifier) {
		return modifier.amount() * (amplifier + 1);
	}
}
