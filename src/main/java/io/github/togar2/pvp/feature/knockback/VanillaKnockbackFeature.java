package io.github.togar2.pvp.feature.knockback;

import io.github.togar2.pvp.events.EntityKnockbackEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.network.packet.server.play.HitAnimationPacket;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link KnockbackFeature}
 */
public class VanillaKnockbackFeature implements KnockbackFeature {
	public static final DefinedFeature<VanillaKnockbackFeature> DEFINED = new DefinedFeature<>(
			FeatureType.KNOCKBACK, VanillaKnockbackFeature::new,
			FeatureType.VERSION
	);

	private final FeatureConfiguration configuration;

	private CombatVersion version;

	public VanillaKnockbackFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public boolean applyDamageKnockback(Damage damage, LivingEntity target) {
		return this.applyDamageKnockback(damage, target, false);
	}

	@Override
	public boolean applyDamageKnockback(Damage damage, LivingEntity target, boolean blocked) {
		Entity attacker = damage.getAttacker();
		Entity source = damage.getSource();

		double dx = attacker.getPosition().x() - target.getPosition().x();
		double dz = attacker.getPosition().z() - target.getPosition().z();

		// Randomize direction
		ThreadLocalRandom random = ThreadLocalRandom.current();
		while (dx * dx + dz * dz < 1.0E-5) {
			dx = random.nextDouble(-1, 1) * 0.01;
			dz = random.nextDouble(-1, 1) * 0.01;
		}

		// Set the velocity
		return this.applyKnockback(
				target, attacker, source,
				blocked ? EntityKnockbackEvent.KnockbackType.BLOCKED_DAMAGE : EntityKnockbackEvent.KnockbackType.DAMAGE, 0,
				dx, dz, this.version.legacy()
		);
	}

	@Override
	public boolean applyAttackKnockback(LivingEntity attacker, LivingEntity target, double knockback) {
		if (knockback <= 0.0) return false;

		// If legacy, attacker velocity is reduced before the knockback
		if (this.version.legacy() && attacker instanceof CombatPlayer custom)
			custom.afterSprintAttack();

		double dx = Math.sin(Math.toRadians(attacker.getPosition().yaw()));
		double dz = -Math.cos(Math.toRadians(attacker.getPosition().yaw()));

		if (!this.applyKnockback(
				target, attacker, attacker,
				EntityKnockbackEvent.KnockbackType.ATTACK, knockback,
				dx, dz, this.version.legacy()
		)) return false;

		// If not legacy, attacker velocity is reduced after the knockback
		if (this.version.modern() && attacker instanceof CombatPlayer custom)
			custom.afterSprintAttack();

		attacker.setSprinting(false);
		return true;
	}

	@Override
	public boolean applySweepingKnockback(LivingEntity attacker, LivingEntity target) {
		double dx = Math.sin(Math.toRadians(attacker.getPosition().yaw()));
		double dz = -Math.cos(Math.toRadians(attacker.getPosition().yaw()));

		return this.applyKnockback(
				target, attacker, null,
				EntityKnockbackEvent.KnockbackType.SWEEPING, 0,
				dx, dz, this.version.legacy()
		);
	}

	public record KnockbackValues(
			Vec horizontalModifier,
			double vertical, double verticalLimit,
			EntityKnockbackEvent.AnimationType animationType
	) {}

	protected @Nullable KnockbackValues prepareKnockback(LivingEntity target, Entity attacker, @Nullable Entity source,
	                                                     EntityKnockbackEvent.KnockbackType type, double extraKnockback,
	                                                     double dx, double dz, boolean legacy) {
		var animationType = legacy
				? EntityKnockbackEvent.AnimationType.FIXED
				: type == EntityKnockbackEvent.KnockbackType.DAMAGE
						? EntityKnockbackEvent.AnimationType.DIRECTIONAL
						: EntityKnockbackEvent.AnimationType.FIXED;
		EntityKnockbackEvent knockbackEvent = new EntityKnockbackEvent(target, source == null ? attacker : source, type, animationType);
		EventDispatcher.call(knockbackEvent);
		if (knockbackEvent.isCancelled()) return null;

		KnockbackSettings settings = knockbackEvent.getSettings();

		double kbResistance = target.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
		double horizontal, vertical;
		if (extraKnockback <= 0.0) {
			// Default knockback
			horizontal = settings.horizontal();
			vertical = settings.vertical();
		} else if (legacy) {
			// Legacy 1.8 extra knockback: motion += direction * (sprint(1) + Knockback) * 0.5, motion.y += 0.1
			horizontal = settings.extraHorizontal() * extraKnockback;
			vertical = settings.extraVertical() * extraKnockback;
		} else {
			// Modern extra knockback: vanilla LivingEntity#knockback uses power directly with no scale
			horizontal = extraKnockback * ServerFlag.SERVER_TICKS_PER_SECOND;
			vertical = extraKnockback * ServerFlag.SERVER_TICKS_PER_SECOND;
		}

		horizontal *= (1 - kbResistance);
		vertical *= (1 - kbResistance);
		if (horizontal <= 0 && vertical <= 0) return null;

		Vec horizontalModifier = new Vec(dx, dz).normalize().mul(horizontal);
		return new KnockbackValues(horizontalModifier, vertical, settings.verticalLimit(), knockbackEvent.getAnimationType());
	}

	protected boolean applyKnockback(LivingEntity target, Entity attacker, @Nullable Entity source,
	                                 EntityKnockbackEvent.KnockbackType type, double extraKnockback,
	                                 double dx, double dz, boolean legacy) {
		KnockbackValues values = this.prepareKnockback(target, attacker, source, type, extraKnockback, dx, dz, legacy);
		if (values == null) return false;

		Vec velocity = target.getVelocity();
		if (legacy && type == EntityKnockbackEvent.KnockbackType.ATTACK) {
			// For legacy versions, extra knockback is added directly on top of the original velocity
			target.setVelocity(velocity.add(
					-values.horizontalModifier().x(),
					values.vertical(),
					-values.horizontalModifier().z()
			));
		} else {
			// For modern versions and legacy non-attack knockback, the velocity is first divided by 2
			target.setVelocity(new Vec(
					velocity.x() / 2d - values.horizontalModifier().x(),
					target.isOnGround() ? Math.min(values.verticalLimit(), velocity.y() / 2d + values.vertical()) : velocity.y(),
					velocity.z() / 2d - values.horizontalModifier().z()
			));
		}

		if (values.animationType() == EntityKnockbackEvent.AnimationType.DIRECTIONAL) {
			// Send player a packet with its hurt direction
			if (target instanceof Player player) {
                this.sendDirectionalEvent(player, dx, dz);
			}
		}

		return true;
	}

	protected void sendDirectionalEvent(Player player, double dx, double dz) {
		float hurtDir = (float) (Math.toDegrees(Math.atan2(dz, dx)) - player.getPosition().yaw());
		player.sendPacket(new HitAnimationPacket(player.getEntityId(), hurtDir));
	}
}
