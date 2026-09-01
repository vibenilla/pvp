package io.github.togar2.pvp.feature.knockback;

import io.github.togar2.pvp.events.EntityKnockbackEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.BlockUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@link VanillaKnockbackFeature} which tries to make the playing field more even for players with high latency.
 * <p>
 * Rising knockback is applied usually when a player is on the ground.
 * This feature determines if the player would be on the ground <i>client side</i> instead of just server side.
 * To have just this change, use {@link FairKnockbackFeature#ONLY_RISING}.
 * <p>
 * The other option is {@link FairKnockbackFeature#RISING_AND_FALLING}, which, along with rising knockback,
 * also compensates for latency with falling knockback. It will use the (estimated) velocity of when the packet will arrive at the client,
 * possibly making falling knockback feel more natural.
 * <p>
 * The changes made by this feature only apply to players with more than 25 ms ping.
 */
public class FairKnockbackFeature extends VanillaKnockbackFeature {
	/**
	 * @see FairKnockbackFeature
	 */
	public static final DefinedFeature<FairKnockbackFeature> ONLY_RISING = new DefinedFeature<>(
			FeatureType.KNOCKBACK, configuration -> new FairKnockbackFeature(configuration, false),
			FeatureType.VERSION
	);
	/**
	 * @see FairKnockbackFeature
	 */
	public static final DefinedFeature<FairKnockbackFeature> RISING_AND_FALLING = new DefinedFeature<>(
			FeatureType.KNOCKBACK, configuration -> new FairKnockbackFeature(configuration, true),
			FeatureType.VERSION
	);

	private static final int PING_OFFSET = 25;

	protected final boolean compensateFallKnockback;

	public FairKnockbackFeature(FeatureConfiguration configuration, boolean compensateFallKnockback) {
		super(configuration);
		this.compensateFallKnockback = compensateFallKnockback;
	}

	@Override
	protected boolean applyKnockback(LivingEntity target, Entity attacker, @Nullable Entity source,
	                                 EntityKnockbackEvent.KnockbackType type, double extraKnockback,
	                                 double dx, double dz, boolean legacy) {
		if (!(target instanceof Player player) || player.getLatency() < PING_OFFSET)
			return super.applyKnockback(target, attacker, source, type, extraKnockback, dx, dz, legacy);

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

			int latencyTicks = getLatencyTicks(player.getLatency());
			double vertical;
			if (this.isOnGroundClientSide(player, latencyTicks)) {
				vertical = Math.min(values.verticalLimit(), velocity.y() / 2d + values.vertical());
			} else if (this.compensateFallKnockback) {
				vertical = getCompensatedVerticalVelocity(player.getAerodynamics(), velocity.y(), latencyTicks);
			} else {
				vertical = velocity.y();
			}

			target.setVelocity(new Vec(
					velocity.x() / 2d - values.horizontalModifier().x(),
					vertical,
					velocity.z() / 2d - values.horizontalModifier().z()
			));
		}

		if (values.animationType() == EntityKnockbackEvent.AnimationType.DIRECTIONAL) {
			// Send player a packet with its hurt direction
            this.sendDirectionalEvent(player, dx, dz);
		}

		return true;
	}

	protected boolean isOnGroundClientSide(Player player, int latencyTicks) {
		if (player.isOnGround() || !(player instanceof CombatPlayer combatPlayer)) return true;
		if (player.getGravityTickCount() > 30) return false; // Very uncertain, default to false

		// These are all cases in which isOnGroundAfterTicks() will not be accurate
		if (player.isFlyingWithElytra() || this.hasUnpredictableMovement(player)) return false;

		return combatPlayer.isOnGroundAfterTicks(latencyTicks);
	}

	protected boolean hasUnpredictableMovement(Player player) {
		var instance = player.getInstance();

		if (instance == null) return true;

		var position = player.getPosition();
		var boundingBox = player.getBoundingBox();
		var minimumX = (int) Math.floor(position.x() + boundingBox.minX() + 0.001);
		var minimumY = (int) Math.floor(position.y() + boundingBox.minY() + 0.001);
		var minimumZ = (int) Math.floor(position.z() + boundingBox.minZ() + 0.001);
		var maximumX = (int) Math.ceil(position.x() + boundingBox.maxX() - 0.001) - 1;
		var maximumY = (int) Math.ceil(position.y() + boundingBox.maxY() - 0.001) - 1;
		var maximumZ = (int) Math.ceil(position.z() + boundingBox.maxZ() - 0.001) - 1;

		for (var blockX = minimumX; blockX <= maximumX; blockX++) {
			for (var blockY = minimumY; blockY <= maximumY; blockY++) {
				for (var blockZ = minimumZ; blockZ <= maximumZ; blockZ++) {
					if (this.isUnpredictableBlock(instance, blockX, blockY, blockZ)) return true;
				}
			}
		}

		var below = position.sub(0.0, 0.5000001, 0.0);

		return this.isUnpredictableBlock(instance, below.blockX(), below.blockY(), below.blockZ());
	}

	protected boolean isUnpredictableBlock(Instance instance, int blockX, int blockY, int blockZ) {
		var block = instance.getBlock(blockX, blockY, blockZ);

		if (FluidUtil.isWater(block) || FluidUtil.isLava(block)
				|| block.compare(Block.COBWEB)
				|| block.compare(Block.POWDER_SNOW)
				|| block.compare(Block.SWEET_BERRY_BUSH)
				|| block.compare(Block.HONEY_BLOCK)
				|| block.compare(Block.SLIME_BLOCK))
			return true;

		return BlockUtil.isClimbable(instance, blockX, blockY, blockZ, block);
	}

	/**
	 * Compensates the given vertical velocity for gravity calculations for a given amount of ticks.
	 * This means for every tick, it will be affected by gravity and vertical air resistance.
	 *
	 * @param aerodynamics the aerodynamics of the player
	 * @param velocity the velocity to compensate
	 * @param ticks the amount of ticks to compensate for
	 * @return the compensated vertical velocity
	 */
	protected static double getCompensatedVerticalVelocity(Aerodynamics aerodynamics, double velocity, int ticks) {
		double gravity = aerodynamics.gravity() * ServerFlag.SERVER_TICKS_PER_SECOND;

		for (int i = 0; i < ticks; i++) {
			velocity -= gravity;
			velocity *= aerodynamics.verticalAirResistance();
		}

		return velocity;
	}

	private static int getLatencyTicks(int latencyMillis) {
		return Math.ceilDiv(latencyMillis * ServerFlag.SERVER_TICKS_PER_SECOND, 1000) + 2;
	}
}
