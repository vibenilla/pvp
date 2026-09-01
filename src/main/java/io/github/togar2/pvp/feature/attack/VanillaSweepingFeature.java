package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Vanilla implementation of {@link SweepingFeature}
 */
public class VanillaSweepingFeature implements SweepingFeature {
	public static final DefinedFeature<VanillaSweepingFeature> DEFINED = new DefinedFeature<>(
			FeatureType.SWEEPING, VanillaSweepingFeature::new,
			FeatureType.ENCHANTMENT, FeatureType.KNOCKBACK
	);

	private final FeatureConfiguration configuration;

	private EnchantmentFeature enchantmentFeature;
	private KnockbackFeature knockbackFeature;

	public VanillaSweepingFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.knockbackFeature = this.configuration.get(FeatureType.KNOCKBACK);
	}

	@Override
	public boolean shouldSweep(LivingEntity attacker, AttackValues.PreSweeping values) {
		if (!values.strong() || values.critical() || values.sprint() || !attacker.isOnGround()) return false;

		var previousPosition = attacker.getPreviousPosition();
		var currentPosition = attacker.getPosition();
		var movementX = currentPosition.x() - previousPosition.x();
		var movementZ = currentPosition.z() - previousPosition.z();
		var horizontalMovementSquared = movementX * movementX + movementZ * movementZ;
		var maxMovement = attacker.getAttributeValue(Attribute.MOVEMENT_SPEED) * 2.5;
		if (horizontalMovementSquared >= maxMovement * maxMovement) return false;

		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		return tool != null && tool.isSword();
	}

	@Override
	public float getSweepingDamage(LivingEntity attacker, float damage) {
		float sweepingMultiplier = 0;
		int sweepingLevel = this.enchantmentFeature.getSweeping(attacker);
		if (sweepingLevel > 0) sweepingMultiplier = 1.0f - (1.0f / (float) (sweepingLevel + 1));
		return 1.0f + sweepingMultiplier * damage;
	}

	@Override
	public Collection<LivingEntity> applySweeping(LivingEntity attacker, LivingEntity target, float damage) {
		return this.applySweeping(attacker, target, damage, 1.0);
	}

	@Override
	public Collection<LivingEntity> applySweeping(LivingEntity attacker, LivingEntity target, float damage,
	                                              double cooldownProgress) {
		float sweepingDamage = this.getSweepingDamage(attacker, damage);

		// Loop and check for colliding entities
		List<LivingEntity> affectedEntities = new ArrayList<>();
		BoundingBox boundingBox = target.getBoundingBox().growSymmetrically(1.0, 0.25, 1.0);
		assert target.getInstance() != null;
		for (Entity nearbyEntity : target.getInstance().getNearbyEntities(target.getPosition(), 3)) {
			if (nearbyEntity instanceof Player) continue;

			var affectedEntity = this.applySweepingToEntity(attacker, target, sweepingDamage, cooldownProgress,
					boundingBox, nearbyEntity);

			if (affectedEntity != null) {
				affectedEntities.add(affectedEntity);
			}
		}
		for (var player : target.getInstance().getPlayers()) {
			var affectedEntity = this.applySweepingToEntity(attacker, target, sweepingDamage, cooldownProgress,
					boundingBox, player);

			if (affectedEntity != null) {
				affectedEntities.add(affectedEntity);
			}
		}

		// Spawn sweeping particles
		Pos pos = attacker.getPosition();
		double x = -Math.sin(Math.toRadians(pos.yaw()));
		double z = Math.cos(Math.toRadians(pos.yaw()));

		attacker.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.SWEEP_ATTACK, false,false,
				pos.x() + x, pos.y() + attacker.getBoundingBox().height() * 0.5, pos.z() + z,
				(float) x, 0, (float) z,
				0, 0
		));

		return affectedEntities;
	}

	private LivingEntity applySweepingToEntity(LivingEntity attacker, LivingEntity target, float sweepingDamage,
	                                           double cooldownProgress, BoundingBox boundingBox, Entity nearbyEntity) {
		if (nearbyEntity == target || nearbyEntity == attacker) return null;
		if (!(nearbyEntity instanceof LivingEntity living)) return null;
		if (this.isMarkerArmorStand(nearbyEntity)) return null;
		if (!boundingBox.intersectEntity(target.getPosition(), nearbyEntity)) return null;
		if (attacker.getPosition().distanceSquared(nearbyEntity.getPosition()) >= 9.0) return null;

		float currentDamage = (sweepingDamage + this.enchantmentFeature.getAttackDamage(
				attacker.getItemInMainHand(), EntityGroup.ofEntity(living))) * (float) cooldownProgress;

		var damaged = living.damage(new Damage(
				attacker instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK,
				attacker, attacker,
				null, currentDamage
		));

		if (!damaged) return null;

		this.knockbackFeature.applySweepingKnockback(attacker, living);

		return living;
	}

	private boolean isMarkerArmorStand(Entity entity) {
		return entity.getEntityType() == EntityType.ARMOR_STAND
				&& entity.getEntityMeta() instanceof ArmorStandMeta armorStandMeta
				&& armorStandMeta.isMarker();
	}
}
