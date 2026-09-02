package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.network.packet.server.play.WorldEventPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;

/**
 * Vanilla implementation of {@link SmashAttackFeature}
 */
public class VanillaSmashAttackFeature implements SmashAttackFeature {
	public static final DefinedFeature<VanillaSmashAttackFeature> DEFINED = new DefinedFeature<>(
			FeatureType.SMASH_ATTACK, VanillaSmashAttackFeature::new,
			FeatureType.FALL, FeatureType.ENCHANTMENT
	);

	private static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
	private static final float WIND_BURST_FALL_THRESHOLD = 1.5F;
	private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
	private static final double SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5;
	private static final double SMASH_ATTACK_KNOCKBACK_POWER = 0.7;
	private static final double SMASH_ATTACK_VERTICAL_KNOCKBACK = 0.7;
	private static final float[] WIND_BURST_POWER_BY_LEVEL = {1.2F, 1.75F, 2.2F};
	private static final float WIND_BURST_FALLBACK_BASE_POWER = 1.5F;
	private static final float WIND_BURST_FALLBACK_POWER_PER_LEVEL = 0.35F;
	private static final double WIND_BURST_RADIUS = 3.5;
	private static final int SMASH_ATTACK_LEVEL_EVENT = 2013;
	private static final int SMASH_ATTACK_PARTICLE_COUNT = 750;

	private final FeatureConfiguration configuration;

	private FallFeature fallFeature;
	private EnchantmentFeature enchantmentFeature;

	public VanillaSmashAttackFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.fallFeature = this.configuration.get(FeatureType.FALL);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
	}

	@Override
	public boolean canSmashAttack(LivingEntity attacker) {
		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		if (tool == null || !tool.isMace()) return false;

		double fallDistance = this.fallFeature.getFallDistance(attacker);
		return fallDistance > SMASH_ATTACK_FALL_THRESHOLD && !attacker.isFlyingWithElytra();
	}

	@Override
	public float getDamageBonus(LivingEntity attacker, LivingEntity target) {
		if (!this.canSmashAttack(attacker)) return 0.0F;

		double fallDistance = this.fallFeature.getFallDistance(attacker);
		double damage;

		if (fallDistance <= 3.0) {
			damage = 4.0 * fallDistance;
		} else if (fallDistance <= 8.0) {
			damage = 12.0 + 2.0 * (fallDistance - 3.0);
		} else {
			damage = 22.0 + (fallDistance - 8.0);
		}

		var densityDamage = this.enchantmentFeature.modifyConditionalValue(
				attacker.getItemInMainHand(), EffectComponent.SMASH_DAMAGE_PER_FALLEN_BLOCK, 0.0F
		);
		damage += densityDamage * fallDistance;

		return (float) damage;
	}

	@Override
	public void applySmashAttack(LivingEntity attacker, LivingEntity target) {
		if (!this.canSmashAttack(attacker)) return;

		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		double fallDistance = this.fallFeature.getFallDistance(attacker);

		Vec velocity = attacker.getVelocity();
		attacker.setVelocity(new Vec(velocity.x(), 0.01 * tps, velocity.z()));
		this.fallFeature.setIgnoreFallDamageFromCurrentImpulse(attacker);

		if (attacker instanceof CombatPlayer combatPlayer) {
			combatPlayer.sendImmediateVelocityUpdate();
		}

		boolean heavySmash = fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD;
		if (target.isOnGround()) {
			if (attacker instanceof Player) {
				this.fallFeature.setExtraFallParticles(attacker, true);
			}

			SoundEvent sound = heavySmash ? SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvent.ITEM_MACE_SMASH_GROUND;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					sound, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F, 1.0F
			), attacker);
		} else {
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ITEM_MACE_SMASH_AIR, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F, 1.0F
			), attacker);
		}

		this.applySmashKnockback(attacker, target, heavySmash);
		this.applyWindBurst(attacker);
		this.fallFeature.resetFallDistance(attacker);
	}

	@Override
	public void applyWindBurst(LivingEntity attacker) {
		int windBurstLevel = this.enchantmentFeature.getEquipmentLevel(attacker, Enchantment.WIND_BURST);
		if (windBurstLevel <= 0) return;
		if (attacker instanceof Player player && player.isFlying()) return;
		if (this.fallFeature.getFallDistance(attacker) < WIND_BURST_FALL_THRESHOLD) return;

		float power = this.getWindBurstPower(windBurstLevel);

		Pos attackerPosition = attacker.getPosition();
		attacker.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_SMALL, false, false,
				attackerPosition.x(), attackerPosition.y() + 0.5, attackerPosition.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));
		attacker.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_LARGE, false, false,
				attackerPosition.x(), attackerPosition.y() + 0.5, attackerPosition.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));

		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0F, 1.0F
		), attacker);

		assert attacker.getInstance() != null;
		var radius = WIND_BURST_RADIUS * 2.0;

		this.applyWindBurstKnockback(attackerPosition, power, attacker);

		for (Entity nearbyEntity : attacker.getInstance().getNearbyEntities(attackerPosition, radius)) {
			if (nearbyEntity == attacker) continue;

			this.applyWindBurstKnockback(attackerPosition, power, nearbyEntity);
		}
	}

	private float getWindBurstPower(int windBurstLevel) {
		if (windBurstLevel <= WIND_BURST_POWER_BY_LEVEL.length) {
			return WIND_BURST_POWER_BY_LEVEL[windBurstLevel - 1];
		}

		return WIND_BURST_FALLBACK_BASE_POWER + WIND_BURST_FALLBACK_POWER_PER_LEVEL * (windBurstLevel - 1);
	}

	private void applyWindBurstKnockback(Pos attackerPosition, float power, Entity nearbyEntity) {
		if (this.isMarkerArmorStand(nearbyEntity)) return;
		if (nearbyEntity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return;
		if (nearbyEntity instanceof Player player && player.getGameMode() == GameMode.CREATIVE && player.isFlying()) return;

		var originY = nearbyEntity.getPosition().y() + nearbyEntity.getEyeHeight();
		var origin = new Vec(nearbyEntity.getPosition().x(), originY, nearbyEntity.getPosition().z());
		var direction = origin.sub(attackerPosition.asVec());
		var directionLength = direction.length();
		var radius = WIND_BURST_RADIUS * 2.0;
		if (directionLength <= 0 || directionLength > radius) return;

		var exposure = VanillaExplosionSupplier.getExposure(attackerPosition, nearbyEntity);
		var knockbackFactor = (1.0 - directionLength / radius) * exposure * power;
		if (nearbyEntity instanceof LivingEntity nearbyLiving) {
			knockbackFactor *= 1.0 - nearbyLiving.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
		}
		var knockbackVector = direction.normalize().mul(knockbackFactor);
		nearbyEntity.setVelocity(nearbyEntity.getVelocity().add(knockbackVector.mul(ServerFlag.SERVER_TICKS_PER_SECOND)));

		if (nearbyEntity instanceof LivingEntity nearbyLiving) this.fallFeature.resetPostImpulseGraceTime(nearbyLiving);
	}

	private void applySmashKnockback(LivingEntity attacker, LivingEntity target, boolean heavySmash) {
		Point onPosition = target.getPosition().sub(0.0, 0.2, 0.0).asBlockVec();
		target.sendPacketToViewersAndSelf(new WorldEventPacket(
				SMASH_ATTACK_LEVEL_EVENT, onPosition, SMASH_ATTACK_PARTICLE_COUNT, false
		));

		assert target.getInstance() != null;
		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;

		for (Entity nearbyEntity : target.getInstance().getNearbyEntities(target.getPosition(), SMASH_ATTACK_KNOCKBACK_RADIUS)) {
			this.applySmashKnockbackToEntity(attacker, target, heavySmash, tps, nearbyEntity);
		}
	}

	private void applySmashKnockbackToEntity(LivingEntity attacker, LivingEntity target, boolean heavySmash, int tps, Entity nearbyEntity) {
		if (nearbyEntity == attacker || nearbyEntity == target) return;
		if (!(nearbyEntity instanceof LivingEntity nearbyLiving)) return;
		if (this.isMarkerArmorStand(nearbyEntity)) return;
		if (nearbyEntity instanceof Player nearbyPlayer && nearbyPlayer.getGameMode() == GameMode.SPECTATOR) return;
		if (nearbyEntity instanceof Player nearbyPlayer && nearbyPlayer.getGameMode() == GameMode.CREATIVE && nearbyPlayer.isFlying()) return;
		if (target.getPosition().distanceSquared(nearbyEntity.getPosition()) > SMASH_ATTACK_KNOCKBACK_RADIUS * SMASH_ATTACK_KNOCKBACK_RADIUS) return;

		Vec direction = nearbyEntity.getPosition().asVec().sub(target.getPosition().asVec());
		double directionLength = direction.length();
		if (directionLength <= 0) return;

		double knockbackResistance = nearbyLiving.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
		double knockbackPower = (SMASH_ATTACK_KNOCKBACK_RADIUS - directionLength)
				* SMASH_ATTACK_KNOCKBACK_POWER
				* (heavySmash ? 2 : 1)
				* (1.0 - knockbackResistance);

		if (knockbackPower <= 0) return;

		Vec knockbackVector = direction.normalize().mul(knockbackPower);
		Vec nearbyVelocity = nearbyLiving.getVelocity();
		nearbyLiving.setVelocity(new Vec(
				nearbyVelocity.x() + knockbackVector.x() * tps,
				nearbyVelocity.y() + SMASH_ATTACK_VERTICAL_KNOCKBACK * tps,
				nearbyVelocity.z() + knockbackVector.z() * tps
		));

		if (nearbyLiving instanceof CombatPlayer combatPlayer) {
			combatPlayer.sendImmediateVelocityUpdate();
		}
	}

	private boolean isMarkerArmorStand(Entity entity) {
		return entity.getEntityType() == EntityType.ARMOR_STAND
				&& entity.getEntityMeta() instanceof ArmorStandMeta armorStandMeta
				&& armorStandMeta.isMarker();
	}
}
