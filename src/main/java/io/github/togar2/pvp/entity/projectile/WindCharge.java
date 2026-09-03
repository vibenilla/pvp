package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.WeightedList;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class WindCharge extends CustomEntityProjectile {
	private static final double RADIUS = 1.2;
	private static final double KNOCKBACK_MULTIPLIER = 1.22;
	private static final float DIRECT_DAMAGE = 1.0F;
	private static final int NO_DEFLECT_TICKS = 5;

	private final FallFeature fallFeature;
	private int noDeflectTicks = NO_DEFLECT_TICKS;

	public WindCharge(@Nullable Entity shooter, FallFeature fallFeature) {
		super(shooter, EntityType.WIND_CHARGE);
		this.fallFeature = fallFeature;

		this.setNoGravity(true);
		this.setAerodynamics(new Aerodynamics(0.0, 1.0, 1.0));
	}

	@Override
	public void tick(long time) {
		super.tick(time);

		if (this.noDeflectTicks > 0) {
			this.noDeflectTicks--;
		}
	}

	public boolean deflect(Entity entity) {
		if (this.noDeflectTicks > 0) return false;

		var direction = entity.getPosition().direction();
		this.setShooter(entity);
		this.setVelocity(direction.mul(ServerFlag.SERVER_TICKS_PER_SECOND));
		this.setView(
				(float) Math.toDegrees(Math.atan2(direction.x(), direction.z())),
				(float) Math.toDegrees(Math.atan2(direction.y(), Math.sqrt(direction.x() * direction.x() + direction.z() * direction.z())))
		);

		return true;
	}

	@Override
	public boolean onHit(Entity entity) {
		if (entity instanceof LivingEntity livingEntity) {
			var damage = new Damage(DamageType.WIND_CHARGE, this, this.getShooter(), null, DIRECT_DAMAGE);
			livingEntity.damage(damage);
		}

		this.explode(this.getPosition());

		return true;
	}

	@Override
	public boolean onStuck() {
		var position = this.getPosition();

		if (this.collisionDirection != null) {
			position = position.sub(this.collisionDirection.mul(0.25));
		}

		this.explode(position);

		return true;
	}

	@Override
	protected boolean canHit(Entity entity) {
		return !(entity instanceof WindCharge)
				&& entity.getEntityType() != EntityType.END_CRYSTAL
				&& super.canHit(entity);
	}

	private void explode(Point center) {
		var instance = this.getInstance();

		if (instance == null) return;

		var doubleRadius = RADIUS * 2.0;
		var centerVector = center.asVec();
		var ticksPerSecond = ServerFlag.SERVER_TICKS_PER_SECOND;

		for (var entity : instance.getNearbyEntities(center, doubleRadius)) {
			if (entity instanceof Player) continue;

			this.applyExplosionKnockback(center, centerVector, doubleRadius, ticksPerSecond, entity);
		}
		Set<Player> receivedKb = new HashSet<>();
		for (var player : instance.getPlayers()) {
			if (this.applyExplosionKnockback(center, centerVector, doubleRadius, ticksPerSecond, player)) receivedKb.add(player);
		}

		for (Player viewer : getViewers()) {
			if (receivedKb.contains(viewer)) continue;
			viewer.sendPacket(new ExplosionPacket(center, 1.2f, 0, Vec.ZERO, Particle.GUST_EMITTER_LARGE,
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, WeightedList.of()));
		}
	}

	/**
	 * @return whether or not knockback was actually applied
	 */
	private boolean applyExplosionKnockback(Point center, Vec centerVector, double doubleRadius, int ticksPerSecond, Entity entity) {
		if (entity == this) return false;

		var distanceStrength = entity.getPosition().distance(center) / doubleRadius;

		if (distanceStrength > 1.0) return false;

		var originY = entity.getPosition().y() + entity.getEyeHeight();
		var direction = new Vec(
				entity.getPosition().x() - center.x(),
				originY - center.y(),
				entity.getPosition().z() - center.z()
		);
		var directionLength = direction.length();

		if (directionLength == 0.0) return false;

		var exposure = this.hasLineOfSight(centerVector, entity) ? 1.0 : 0.0;
		var knockback = (1.0 - distanceStrength) * exposure * KNOCKBACK_MULTIPLIER;

		if (entity instanceof LivingEntity livingEntity) {
			knockback *= 1.0 - livingEntity.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
		}

		if (knockback <= 0.0) return false;

		var knockbackVelocity = direction.normalize().mul(knockback);

		if (entity instanceof Player player) {
			if (player.getGameMode() == GameMode.SPECTATOR) return false;
			if (player.getGameMode() == GameMode.CREATIVE && player.isFlying()) return false;
			this.fallFeature.setIgnoreFallDamageFromCurrentImpulse(player);
			player.sendPacket(new ExplosionPacket(center, 1.2f, 0, knockbackVelocity, Particle.GUST_EMITTER_LARGE,
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, WeightedList.of()));
			if (player instanceof CombatPlayer combatPlayer) combatPlayer.setVelocityNoUpdate(vel -> vel.add(knockbackVelocity.mul(ticksPerSecond)));
		} else {
			entity.setVelocity(entity.getVelocity().add(knockbackVelocity.mul(ticksPerSecond)));
		}
		return true;
	}

	private boolean hasLineOfSight(Vec center, Entity entity) {
		var basePosition = entity.getPosition();

		for (var testStep = 0; testStep < 2; testStep++) {
			var targetPosition = basePosition.add(0.0, entity.getBoundingBox().height() * 0.5 * testStep, 0.0);

			if (VanillaExplosionSupplier.noBlocking(entity.getInstance(), center, targetPosition)) {
				return true;
			}
		}

		return false;
	}
}
