package io.github.togar2.pvp.feature.fall;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.config.PlayerInitReason;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.FluidUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerStartFlyingWithElytraEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.Registries;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.block.BlockIterator;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link FallFeature}
 */
public class VanillaFallFeature implements FallFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaFallFeature> DEFINED = new DefinedFeature<>(
			FeatureType.FALL, VanillaFallFeature::new,
			VanillaFallFeature::initPlayer,
			FeatureType.PLAYER_STATE, FeatureType.ITEM_DAMAGE
	);

	public static final Tag<Double> FALL_DISTANCE = Tag.Transient("fallDistance");
	public static final Tag<Boolean> EXTRA_FALL_PARTICLES = Tag.Transient("extraFallParticles");
	public static final Tag<Integer> FALL_FLYING_TICKS = Tag.Integer("fallFlyingTicks");
	public static final Tag<Double> CURRENT_IMPULSE_IMPACT_Y = Tag.Transient("currentImpulseImpactY");
	public static final Tag<Integer> CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME = Tag.Transient("currentImpulseContextResetGraceTime");

	private static final int CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TICKS = 40;

	private final FeatureConfiguration configuration;

	private PlayerStateFeature playerStateFeature;
	private ItemDamageFeature itemDamageFeature;

	public VanillaFallFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
	}

	public static void initPlayer(Player player, PlayerInitReason reason) {
		player.setTag(FALL_DISTANCE, 0.0);
		if (reason == PlayerInitReason.INSTANCE_CHANGE) return;

		player.setTag(FALL_FLYING_TICKS, 0);
		player.setTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME, 0);
		player.removeTag(CURRENT_IMPULSE_IMPACT_Y);
		player.removeTag(EXTRA_FALL_PARTICLES);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerStartFlyingWithElytraEvent.class, event -> {
			var player = event.getPlayer();

			if (!this.canGlide(player)) {
				player.setFlyingWithElytra(false);
			}
		});

		node.addListener(PlayerTickEvent.class, event -> {
			this.updateFallFlying(event.getPlayer());
			this.tickCurrentImpulseContext(event.getPlayer());
		});

		// For living non-player entities, handle fall damage every tick
		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
			if (livingEntity instanceof Player) return;

			Pos previousPosition = livingEntity.getPreviousPosition();
            this.handleFallDamage(livingEntity, previousPosition, livingEntity.getPosition(), livingEntity.isOnGround());
			this.tickCurrentImpulseContext(livingEntity);
		});

		// For players, handle fall damage on move event
		node.addListener(PlayerMoveEvent.class, event -> {
			Player player = event.getPlayer();
			if (this.playerStateFeature.isClimbing(player)) player.setTag(FALL_DISTANCE, 0.0);

            this.handleFallDamage(
					player, player.getPosition(),
					event.getNewPosition(), event.isOnGround()
			);
		});
	}

	private boolean canGlide(Player player) {
		if (player.isFlying()
				|| player.isOnGround()
				|| player.getVehicle() != null
				|| player.hasEffect(PotionEffect.LEVITATION)
				|| FluidUtil.isTouchingWater(player)) {
			return false;
		}

		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				return true;
			}
		}

		return false;
	}

	private boolean canContinueGliding(Player player) {
		if (player.isFlying()
				|| player.isOnGround()
				|| player.getVehicle() != null
				|| player.hasEffect(PotionEffect.LEVITATION)) {
			return false;
		}

		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				return true;
			}
		}

		return false;
	}

	private boolean canGlideUsing(ItemStack stack, EquipmentSlot slot) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);

		return stack.has(DataComponents.GLIDER)
				&& equippable != null
				&& slot == equippable.slot()
				&& !this.nextDamageWillBreak(stack);
	}

	private void updateFallFlying(Player player) {
		if (!player.isFlyingWithElytra()) {
			player.setTag(FALL_FLYING_TICKS, 0);
			return;
		}

		if (!this.canContinueGliding(player)) {
			player.setFlyingWithElytra(false);
			player.setTag(FALL_FLYING_TICKS, 0);
			return;
		}

		var fallFlyingTicks = player.getTag(FALL_FLYING_TICKS) + 1;
		player.setTag(FALL_FLYING_TICKS, fallFlyingTicks);

		if (fallFlyingTicks % 20 != 0) {
			return;
		}

		var gliderSlots = new ArrayList<EquipmentSlot>();
		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				gliderSlots.add(slot);
			}
		}

		if (gliderSlots.isEmpty()) {
			return;
		}

		var slot = gliderSlots.get(ThreadLocalRandom.current().nextInt(gliderSlots.size()));
		this.itemDamageFeature.damageEquipment(player, slot, 1);
	}

	private boolean nextDamageWillBreak(ItemStack stack) {
		if (stack.has(DataComponents.UNBREAKABLE)) return false;

		var maxDamage = stack.get(DataComponents.MAX_DAMAGE, 0);

		if (maxDamage <= 0) return false;

		return stack.get(DataComponents.DAMAGE, 0) + 1 >= maxDamage;
	}

	public void handleFallDamage(LivingEntity entity, Pos currPos, Pos newPos, boolean onGround) {
		double dy = newPos.y() - currPos.y();
		double fallDistance = this.getFallDistance(entity);

		if (this.shouldResetFallDistanceAlongMovement(entity, currPos, newPos, fallDistance)) {
			fallDistance = 0.0;
			entity.setTag(FALL_DISTANCE, fallDistance);
		}

		if (FluidUtil.isTouchingWater(entity, newPos) || this.isTouchingSweetBerryBush(entity, newPos)) {
			entity.setTag(FALL_DISTANCE, 0.0);
			return;
		}

		if (this.isTouchingLava(entity, newPos)) {
			fallDistance *= 0.5;
			entity.setTag(FALL_DISTANCE, fallDistance);
		}

		if ((entity instanceof Player player && player.isFlying())
				|| entity.hasEffect(PotionEffect.LEVITATION)
				|| entity.hasEffect(PotionEffect.SLOW_FALLING)) {
			entity.setTag(FALL_DISTANCE, 0.0);
			return;
		}

        if (entity.isFlyingWithElytra()
                && entity.getVelocity().y() > -0.5 * ServerFlag.SERVER_TICKS_PER_SECOND
                && fallDistance > 1.0) {
            fallDistance = 1.0;
            entity.setTag(FALL_DISTANCE, fallDistance);
        }

        if (dy < 0.0) {
            fallDistance -= dy;
            entity.setTag(FALL_DISTANCE, fallDistance);
        }

        if (!onGround) {
            return;
        }

		if (fallDistance <= 0.0) {
			entity.setTag(FALL_DISTANCE, 0.0);
			return;
		}

		Point landingPos = this.getLandingPos(entity, newPos);
		Block block = entity.getInstance().getBlock(landingPos);
		var adjustedFallDistance = this.adjustFallDistance(block, fallDistance);
		var damageModifier = this.getDamageModifier(entity, block);
		var damageType = this.getDamageType(block);
		var effectiveFallDistance = this.getEffectiveFallDistance(entity, adjustedFallDistance, newPos);

		if (entity.hasTag(EXTRA_FALL_PARTICLES) && entity.getTag(EXTRA_FALL_PARTICLES) && fallDistance > 0.0) {
			Vec position = landingPos.asVec().apply(Vec.Operator.FLOOR).add(0.5, 1, 0.5);
			int particleCount = (int) Math.max(0, Math.min(200, 50 * fallDistance));

			entity.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.BLOCK.withBlock(block),
					position.x(), position.y(), position.z(),
					0.3f, 0.3f, 0.3f,
					0.15f, particleCount
			));

			entity.removeTag(EXTRA_FALL_PARTICLES);
		}

		if (block.compare(Block.POWDER_SNOW) && fallDistance >= 4.0) {
			this.playPowderSnowFallSound(entity, fallDistance);
		}

		if (block.compare(Block.HONEY_BLOCK)) {
			this.playHoneyBlockSlideEffects(entity);
		}

		double safeFallDistance = entity.getAttributeValue(Attribute.SAFE_FALL_DISTANCE);
		if (effectiveFallDistance > safeFallDistance) {
			if (!block.air()) {
				double damageDistance = Math.floor(effectiveFallDistance + 1.0E-6 - safeFallDistance);
				double particleMultiplier = Math.min(0.2 + damageDistance / 15.0, 2.5);
				int particleCount = (int) (150 * particleMultiplier);

				entity.sendPacketToViewersAndSelf(new ParticlePacket(
						Particle.BLOCK.withBlock(block), false,
						false,
						newPos.x(), newPos.y(), newPos.z(),
						0, 0, 0,
						0.15f, particleCount
				));
			}
		}

		entity.setTag(FALL_DISTANCE, 0.0);

		if (entity instanceof Player player && player.getGameMode().invulnerable()) return;
		if (this.isFallDamageImmune(entity)) return;

		this.propagateFallDamageToPassengers(entity, effectiveFallDistance, damageModifier, damageType);

		int damage = this.getFallDamage(entity, effectiveFallDistance, damageModifier);
		if (damage > 0) {
			this.resetCurrentImpulseContext(entity);
            this.playFallSound(entity, damage);
			var damaged = entity.damage(damageType, damage);

			if (damaged) {
				this.playBlockFallSound(entity, block);

				if (block.compare(Block.HONEY_BLOCK)) this.playHoneyBlockFallSound(entity);
			}
		}
	}

	private boolean shouldResetFallDistanceAlongMovement(LivingEntity entity, Pos currentPosition, Pos newPosition, double fallDistance) {
		if (fallDistance == 0.0) return false;

		var instance = entity.getInstance();

		if (instance == null) return false;

		var movement = newPosition.asVec().sub(currentPosition.asVec());
		var movementLength = movement.length();

		if (movementLength < 1.0) return false;

		var maxDistance = Math.min(movementLength, 8.0);
		var iterator = new BlockIterator(currentPosition.asVec(), movement, 0.0, maxDistance);

		while (iterator.hasNext()) {
			var blockPosition = iterator.next();
			var block = instance.getBlock(blockPosition);

			if (this.isFallDamageResetting(block) || this.isWaterFluidBlock(block)) {
				return true;
			}
		}

		return false;
	}

	private void propagateFallDamageToPassengers(LivingEntity entity, double fallDistance, double damageModifier, RegistryKey<DamageType> damageType) {
		for (var passenger : entity.getPassengers()) {
			if (passenger instanceof Player player && player.getGameMode().invulnerable()) {
				continue;
			}

			if (!(passenger instanceof LivingEntity livingPassenger)) {
				continue;
			}

			this.propagateFallDamageToPassengers(livingPassenger, fallDistance, damageModifier, damageType);

			var damage = this.getFallDamage(livingPassenger, fallDistance, damageModifier);

			if (damage <= 0) {
				continue;
			}

			this.playFallSound(livingPassenger, damage);
			livingPassenger.damage(damageType, damage);
		}
	}

	public void playFallSound(LivingEntity entity, int damage) {
		boolean bigFall = damage > 4;

		entity.getViewersAsAudience().playSound(Sound.sound(
				bigFall ?
						SoundEvent.ENTITY_PLAYER_BIG_FALL :
						SoundEvent.ENTITY_PLAYER_SMALL_FALL,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0f, 1.0f
		), entity);
	}

	private void playPowderSnowFallSound(LivingEntity entity, double fallDistance) {
		boolean bigFall = fallDistance >= 7.0;

		entity.getViewersAsAudience().playSound(Sound.sound(
				bigFall ?
						SoundEvent.ENTITY_PLAYER_BIG_FALL :
						SoundEvent.ENTITY_PLAYER_SMALL_FALL,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0f, 1.0f
		), entity);
	}

	private void playHoneyBlockSlideEffects(LivingEntity entity) {
		entity.getViewersAsAudience().playSound(Sound.sound(
				SoundEvent.BLOCK_HONEY_BLOCK_SLIDE,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0f, 1.0f
		), entity);
		entity.triggerStatus((byte) 54);
	}

	private void playBlockFallSound(LivingEntity entity, Block block) {
		if (block.air()) return;

		var soundType = block.blockSoundType();
		if (soundType == null) return;

		entity.getViewersAsAudience().playSound(Sound.sound(
				soundType.fallSound(),
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				soundType.volume() * 0.5f, soundType.pitch() * 0.75f
		), entity);
	}

	private void playHoneyBlockFallSound(LivingEntity entity) {
		entity.getViewersAsAudience().playSound(Sound.sound(
				SoundEvent.BLOCK_HONEY_BLOCK_FALL,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				0.5f, 0.75f
		), entity);
	}

	@Override
	public int getFallDamage(LivingEntity entity, double fallDistance) {
		return this.getFallDamage(entity, fallDistance, 1.0);
	}

	protected int getFallDamage(LivingEntity entity, double fallDistance, double damageModifier) {
		if (this.isFallDamageImmune(entity)) return 0;
		if (this.isLlama(entity) && fallDistance < 6.0) return 0;

		double safeFallDistance = entity.getAttributeValue(Attribute.SAFE_FALL_DISTANCE);
		var damage = (int) Math.floor((fallDistance + 1.0E-6 - safeFallDistance)
				* damageModifier
				* entity.getAttributeValue(Attribute.FALL_DAMAGE_MULTIPLIER));

		if (entity.getEntityType() == EntityType.GOAT) return damage - 10;
		if (entity.getEntityType() == EntityType.FROG) return damage - 5;

		return damage;
	}

	private boolean isLlama(LivingEntity entity) {
		return entity.getEntityType() == EntityType.LLAMA
				|| entity.getEntityType() == EntityType.TRADER_LLAMA;
	}

	private boolean isFallDamageImmune(LivingEntity entity) {
		var entityTypeTag = MinecraftServer.process().entityType().getTag(Key.key("minecraft:fall_damage_immune"));

		if (entityTypeTag == null) return false;

		var key = entity.getEntityType().asKey();

		return key != null && entityTypeTag.contains(key);
	}

	@Override
	public double getFallDistance(LivingEntity entity) {
		return entity.hasTag(FALL_DISTANCE) ? entity.getTag(FALL_DISTANCE) : 0.0;
	}

	@Override
	public void resetFallDistance(LivingEntity entity) {
		entity.setTag(FALL_DISTANCE, 0.0);
	}

	@Override
	public void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles) {
		if (extraFallParticles) entity.setTag(EXTRA_FALL_PARTICLES, true);
		else entity.removeTag(EXTRA_FALL_PARTICLES);
	}

	@Override
	public void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity) {
		this.setIgnoreFallDamageFromCurrentImpulse(entity, this.calculateCurrentImpulseImpactY(entity));
	}

	@Override
	public void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity, double impactY) {
		entity.setTag(CURRENT_IMPULSE_IMPACT_Y, impactY);
		this.applyPostImpulseGraceTime(entity, CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TICKS);
	}

	@Override
	public void clearCurrentImpulseContext(LivingEntity entity) {
		this.resetCurrentImpulseContext(entity);
	}

	@Override
	public void resetPostImpulseGraceTime(LivingEntity entity) {
		entity.setTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME, 0);
	}

	@Override
	public void applyPostImpulseGraceTime(LivingEntity entity, int ticks) {
		entity.setTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME, Math.max(
				entity.hasTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME) ? entity.getTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME) : 0,
				ticks
		));
	}

	private double calculateCurrentImpulseImpactY(LivingEntity entity) {
		if (entity.hasTag(CURRENT_IMPULSE_IMPACT_Y) && entity.getTag(CURRENT_IMPULSE_IMPACT_Y) <= entity.getPosition().y()) {
			return entity.getTag(CURRENT_IMPULSE_IMPACT_Y);
		}

		return entity.getPosition().y();
	}

	private void tickCurrentImpulseContext(LivingEntity entity) {
		if (!entity.hasTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME)) return;

		var graceTime = entity.getTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME);
		if (graceTime <= 0) return;

		entity.setTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME, graceTime - 1);
	}

	private double getEffectiveFallDistance(LivingEntity entity, double fallDistance, Pos position) {
		if (!entity.hasTag(CURRENT_IMPULSE_IMPACT_Y)) return fallDistance;

		var effectiveFallDistance = Math.min(fallDistance, entity.getTag(CURRENT_IMPULSE_IMPACT_Y) - position.y());

		if (effectiveFallDistance <= 0.0) {
			this.resetCurrentImpulseContext(entity);
			return effectiveFallDistance;
		}

		var graceTime = entity.hasTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME) ? entity.getTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME) : 0;
		if (graceTime <= 0) {
			this.resetCurrentImpulseContext(entity);
		}

		return effectiveFallDistance;
	}

	private void resetCurrentImpulseContext(LivingEntity entity) {
		entity.removeTag(CURRENT_IMPULSE_IMPACT_Y);
		entity.setTag(CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME, 0);
	}

	protected Point getLandingPos(LivingEntity livingEntity, Pos position) {
		Point offset = position.add(0, -0.2, 0);
		Instance instance = livingEntity.getInstance();

		if (instance == null) return offset;
		if (!instance.getBlock(offset).air()) return offset;

		Point offsetDown = offset.add(0, -1, 0);
		Block block = instance.getBlock(offsetDown);

		Registries registries = MinecraftServer.process();
		var fences = registries.blocks().getTag(Key.key("minecraft:fences"));
		var walls = registries.blocks().getTag(Key.key("minecraft:walls"));
		var fenceGates = registries.blocks().getTag(Key.key("minecraft:fence_gates"));

		var key = block.asKey();

		assert fences != null;
		assert walls != null;
		assert fenceGates != null;
		assert key != null;

		if (fences.contains(key)
				|| walls.contains(key)
				|| fenceGates.contains(key)) {
			return offsetDown;
		}

		return offset;
	}

	private double adjustFallDistance(Block block, double fallDistance) {
		if (this.isBed(block)) return fallDistance * 0.5;
		if (this.isPointedDripstoneStalagmiteTip(block)) return fallDistance + 2.5;

		return fallDistance;
	}

	private double getDamageModifier(LivingEntity entity, Block block) {
		if (this.isPointedDripstoneStalagmiteTip(block)) return 2.0;
		if (block.compare(Block.POWDER_SNOW)) return 0.0;
		if (block.compare(Block.SLIME_BLOCK) && !this.isSuppressingBounce(entity)) return 0.0;
		if (block.compare(Block.HAY_BLOCK) || block.compare(Block.HONEY_BLOCK)) return 0.2;

		return 1.0;
	}

	private boolean isSuppressingBounce(LivingEntity entity) {
		if (entity instanceof Player player) {
			return player.isSneaking() || player.inputs().shift();
		}

		return entity.isSneaking();
	}

	private RegistryKey<DamageType> getDamageType(Block block) {
		if (this.isPointedDripstoneStalagmiteTip(block)) return DamageType.STALAGMITE;

		return DamageType.FALL;
	}

	private boolean isBed(Block block) {
		return block.key().value().endsWith("_bed");
	}

	private boolean isPointedDripstoneStalagmiteTip(Block block) {
		return block.compare(Block.POINTED_DRIPSTONE)
				&& "up".equals(block.getProperty("vertical_direction"))
				&& "tip".equals(block.getProperty("thickness"));
	}

	private boolean isTouchingLava(LivingEntity entity, Pos position) {
		return this.isTouchingBlock(entity, position, Block.LAVA);
	}

	private boolean isTouchingSweetBerryBush(LivingEntity entity, Pos position) {
		return this.isTouchingBlock(entity, position, Block.SWEET_BERRY_BUSH);
	}

	private boolean isFallDamageResetting(Block block) {
		var tag = MinecraftServer.process().blocks().getTag(Key.key("minecraft:fall_damage_resetting"));

		if (tag == null) return false;

		var key = block.asKey();

		return key != null && tag.contains(key);
	}

	private boolean isWaterFluidBlock(Block block) {
		return block.compare(Block.WATER)
				|| block.compare(Block.BUBBLE_COLUMN)
				|| "true".equals(block.getProperty("waterlogged"));
	}

	private boolean isTouchingBlock(LivingEntity entity, Pos position, Block block) {
		var instance = entity.getInstance();

		if (instance == null) return false;

		return instance.getBlock(position).compare(block);
	}
}
