package io.github.togar2.pvp.feature.armor;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla implementation of {@link ArmorFeature}
 */
public class VanillaArmorFeature implements ArmorFeature {
	public static final DefinedFeature<VanillaArmorFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ARMOR, VanillaArmorFeature::new,
			FeatureType.ENCHANTMENT, FeatureType.VERSION
	);

	private final FeatureConfiguration configuration;
	private EnchantmentFeature enchantmentFeature;
	private CombatVersion version;

	public VanillaArmorFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public float getDamageWithProtection(LivingEntity entity, DamageType type, float amount) {
		return this.getDamageWithProtection(entity, type, amount, null);
	}

	@Override
	public float getDamageWithProtection(LivingEntity entity, DamageType type, float amount, @Nullable LivingEntity attacker) {
		var damageType = MinecraftServer.getDamageTypeRegistry().getKey(type);
		DamageTypeInfo info = DamageTypeInfo.of(damageType);
		amount = this.getDamageWithArmor(entity, info, amount, damageType, attacker);
		return this.getDamageWithEnchantments(entity, type, amount);
	}

	protected float getDamageWithArmor(LivingEntity entity, DamageTypeInfo typeInfo, float amount,
	                                   RegistryKey<DamageType> damageType, @Nullable LivingEntity attacker) {
		if (typeInfo.bypassesArmor()) return amount;

		double armorValue = entity.getAttributeValue(Attribute.ARMOR);

		if (this.version.legacy()) {
			int armorMultiplier = 25 - (int) armorValue;
			return (amount * (float) armorMultiplier) / 25;
		}

		float fraction = this.getArmorFraction(
				amount, (float) Math.floor(armorValue),
				(float) entity.getAttributeValue(Attribute.ARMOR_TOUGHNESS)
		);

		if (attacker != null && this.isWeaponDamage(damageType)) {
			fraction = Math.clamp(this.enchantmentFeature.modifyConditionalValue(
					attacker.getItemInMainHand(), EffectComponent.ARMOR_EFFECTIVENESS, fraction
			), 0.0F, 1.0F);
		}

		return amount * (1.0F - fraction);
	}

	private boolean isWeaponDamage(RegistryKey<DamageType> damageType) {
		return damageType.equals(DamageType.PLAYER_ATTACK)
				|| damageType.equals(DamageType.MOB_ATTACK)
				|| damageType.equals(DamageType.MOB_ATTACK_NO_AGGRO)
				|| damageType.equals(DamageType.MACE_SMASH)
				|| damageType.equals(DamageType.SPEAR);
	}

	protected float getDamageWithEnchantments(LivingEntity entity, DamageType damageType, float amount) {
		DamageTypeInfo damageTypeInfo = DamageTypeInfo.of(MinecraftServer.getDamageTypeRegistry().getKey(damageType));
		if (damageTypeInfo.unblockable()) return amount;

		int k;
		TimedPotion effect = entity.getEffect(PotionEffect.RESISTANCE);
		if (effect != null && !damageTypeInfo.bypassesResistance()) {
			k = (effect.potion().amplifier() + 1) * 5;
			int j = 25 - k;
			float f = amount * (float) j;
			amount = Math.max(f / 25, 0);
		}

		if (amount <= 0) {
			return 0;
		} else if (damageTypeInfo.bypassesEnchantments()) {
			return amount;
		} else {
			k = this.enchantmentFeature.getProtectionAmount(entity, damageType);
			if (this.version.modern()) {
				if (k > 0) {
					amount = this.getDamageAfterProtectionEnchantment(amount, (float) k);
				}
			} else {
				if (k > 20) {
					k = 20;
				}

				if (k > 0) {
					int j = 25 - k;
					float f = amount * (float) j;
					amount = f / 25;
				}
			}

			return amount;
		}
	}

	protected float getDamageLeft(float damage, float armor, float armorToughness) {
		return damage * (1.0F - this.getArmorFraction(damage, armor, armorToughness));
	}

	protected float getArmorFraction(float damage, float armor, float armorToughness) {
		float f = 2.0f + armorToughness / 4.0f;
		float g = Math.clamp(armor - damage / f, armor * 0.2f, 20.0f);
		return g / 25.0F;
	}

	protected float getDamageAfterProtectionEnchantment(float damageDealt, float protection) {
		float f = Math.clamp(protection, 0.0f, 20.0f);
		return damageDealt * (1.0f - f / 25.0f);
	}
}
