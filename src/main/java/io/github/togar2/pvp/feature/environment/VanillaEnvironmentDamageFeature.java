package io.github.togar2.pvp.feature.environment;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.config.PlayerInitReason;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.enchantment.VanillaEnchantmentFeature;
import io.github.togar2.pvp.feature.provider.DifficultyProvider;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.PotionFlags;
import io.github.togar2.pvp.utils.RegistryTags;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.component.DataComponents;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.Difficulty;

import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/**
 * Vanilla implementation of {@link EnvironmentDamageFeature}
 */
public final class VanillaEnvironmentDamageFeature implements EnvironmentDamageFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEnvironmentDamageFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ENVIRONMENT_DAMAGE, VanillaEnvironmentDamageFeature::new,
			VanillaEnvironmentDamageFeature::initPlayer,
			FeatureType.ENCHANTMENT,
			FeatureType.DIFFICULTY
	);

	private static final int MAX_AIR_SUPPLY = 300;
	private static final int DROWN_THRESHOLD = -20;
	private static final int DOLPHIN_MOISTNESS_MAX = 2400;
	private static final int FREEZE_MAX_TICKS = 140;
	private static final int FREEZE_DAMAGE_INTERVAL = 40;
	private static final int FIRE_DAMAGE_INTERVAL = 20;
	private static final int FIRE_IGNITE_TICKS = 8 * 20;
	private static final int LAVA_IGNITE_TICKS = 15 * 20;
	private static final int TURTLE_HELMET_WATER_BREATHING_TICKS = 200;
	private static final byte TURTLE_HELMET_WATER_BREATHING_FLAGS = PotionFlags.create(false, false, true);
	private static final int DEFAULT_MAX_ENTITY_CRAMMING = 24;
	private static final double WORLD_BORDER_SAFE_ZONE = 5.0;
	private static final double WORLD_BORDER_DAMAGE_PER_BLOCK = 0.2;

	private static final Tag<Integer> AIR_SUPPLY = Tag.Integer("environmentAirSupply");
	private static final Tag<Integer> DOLPHIN_MOISTNESS = Tag.Integer("environmentDolphinMoistness");
	private static final Tag<Integer> FREEZE_TICKS = Tag.Integer("environmentFreezeTicks");

	private static final Set<EntityType> WATER_ANIMAL_DROWN_TYPES = Set.of(
			EntityType.COD, EntityType.GLOW_SQUID, EntityType.PUFFERFISH, EntityType.SALMON,
			EntityType.SQUID, EntityType.TADPOLE, EntityType.TROPICAL_FISH
	);
	private static final Set<EntityType> DRY_OUT_AIR_SUPPLY_TYPES = Set.of(
			EntityType.AXOLOTL, EntityType.NAUTILUS
	);
	private static final Set<EntityType> WATER_SENSITIVE_TYPES = Set.of(
			EntityType.BLAZE, EntityType.ENDERMAN, EntityType.SNOW_GOLEM, EntityType.STRIDER
	);

	@SuppressWarnings("unused")
	public VanillaEnvironmentDamageFeature(FeatureConfiguration configuration) {
		this.enchantmentFeature = configuration.get(FeatureType.ENCHANTMENT);
		this.difficultyProvider = configuration.get(FeatureType.DIFFICULTY);
	}

	private final EnchantmentFeature enchantmentFeature;
	private final DifficultyProvider difficultyProvider;

	public static void initPlayer(Player player, PlayerInitReason reason) {
		if (reason == PlayerInitReason.INSTANCE_CHANGE) return;

		player.setTag(AIR_SUPPLY, MAX_AIR_SUPPLY);
		player.setTag(FREEZE_TICKS, 0);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerTickEvent.class, event -> this.handlePlayerTick(event.getPlayer()));

		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
			if (livingEntity instanceof Player) return;

			this.handleEntityTick(livingEntity);
		});
	}

	private void handlePlayerTick(Player player) {
		this.handleExtinguishing(player);
		this.handleFireDamage(player);
		this.handleLavaDamage(player);
		this.handleVoidDamage(player);
		this.handleSuffocation(player);
		this.handleWorldBorderDamage(player);
		this.handleCrammingDamage(player);
		this.handleTurtleHelmetBreathing(player);
		this.handleDrowning(player);
		this.handleBlockContactDamage(player);
		this.handleFreezeDamage(player);
		this.handleWaterSensitiveDamage(player);
	}

	private void handleEntityTick(LivingEntity entity) {
		this.handleExtinguishing(entity);
		this.handleFireDamage(entity);
		this.handleLavaDamage(entity);
		this.handleVoidDamage(entity);
		this.handleSuffocation(entity);
		this.handleCrammingDamage(entity);
		this.handleDrowning(entity);
		this.handleBlockContactDamage(entity);
		this.handleFreezeDamage(entity);
		this.handleWaterSensitiveDamage(entity);
	}

	private void handleExtinguishing(LivingEntity entity) {
		if (!entity.isOnFire()) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		if (this.isInPowderSnow(instance, entity) || this.isTouchingWater(entity)
				|| this.isInRain(instance, entity) || this.isInWaterCauldron(instance, entity)) {
			this.extinguish(entity);
		}
	}

	private void handleFireDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;
		if (this.isFireImmune(entity)) {
			entity.setFireTicks(0);
			return;
		}

		var fireBlock = this.findBlockInside(entity, block -> block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE));

		if (fireBlock != null) {
			this.setFireTicks(entity, FIRE_IGNITE_TICKS);
			var fireDamage = fireBlock.compare(Block.SOUL_FIRE) ? 2.0F : 1.0F;
			entity.damage(DamageType.IN_FIRE, fireDamage);
			return;
		}

		if (!entity.isOnFire()) return;

		if ((entity.getFireTicks() + 1) % FIRE_DAMAGE_INTERVAL == 0 && !this.isInLava(entity)) {
			entity.damage(DamageType.ON_FIRE, 1.0F);
		}
	}

	private void handleLavaDamage(LivingEntity entity) {
		if (this.isFireImmune(entity)) return;
		if (!this.isInLava(entity)) return;

		this.setFireTicks(entity, LAVA_IGNITE_TICKS);
		var damaged = entity.damage(DamageType.LAVA, 4.0F);

		if (damaged && !entity.isSilent()) {
			ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
					SoundEvent.ENTITY_GENERIC_BURN,
					entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					0.4F, 2.0F + ThreadLocalRandom.current().nextFloat() * 0.4F
			), entity);
		}
	}

	private void handleVoidDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		if (!instance.isInVoid(entity.getPosition())) return;

		entity.damage(DamageType.OUT_OF_WORLD, 4.0F);
	}

	private void handleSuffocation(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;
		if (!entity.hasPhysics()) return;
		if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return;

		var position = entity.getPosition();
		var checkWidth = entity.getBoundingBox().width() * 0.8;
		var checkBox = new BoundingBox(
				checkWidth, 1.0E-6, checkWidth,
				new Vec(-checkWidth / 2.0, -5.0E-7, -checkWidth / 2.0)
		);
		var eyePosition = position.add(0.0, entity.getEyeHeight(), 0.0);

		var minX = (int) Math.floor(eyePosition.x() + checkBox.minX());
		var maxX = (int) Math.floor(eyePosition.x() + checkBox.maxX());
		var minY = (int) Math.floor(eyePosition.y() + checkBox.minY());
		var maxY = (int) Math.floor(eyePosition.y() + checkBox.maxY());
		var minZ = (int) Math.floor(eyePosition.z() + checkBox.minZ());
		var maxZ = (int) Math.floor(eyePosition.z() + checkBox.maxZ());

		for (var blockX = minX; blockX <= maxX; blockX++) {
			for (var blockY = minY; blockY <= maxY; blockY++) {
				for (var blockZ = minZ; blockZ <= maxZ; blockZ++) {
					var block = instance.getBlock(blockX, blockY, blockZ);

					if (!this.isSuffocating(block)) continue;

					var blockPosition = new Vec(blockX, blockY, blockZ);
					if (block.collisionShape().intersectBox(eyePosition.sub(blockPosition), checkBox)) {
						entity.damage(DamageType.IN_WALL, 1.0F);
						return;
					}
				}
			}
		}
	}

	private boolean isSuffocating(Block block) {
		if (block.air() || !block.blocksMotion()) return false;

		var shape = block.collisionShape();
		for (var face : BlockFace.values()) {
			if (!shape.isFaceFull(face)) return false;
		}

		if (block.compare(Block.MANGROVE_ROOTS) || block.compare(Block.MOVING_PISTON)) return false;
		if ((block.compare(Block.PISTON) || block.compare(Block.STICKY_PISTON)) && "true".equals(block.getProperty("extended"))) return false;

		var key = block.key().value();
		if (key.endsWith("glass") || key.endsWith("copper_grate")) return false;

		return !RegistryTags.contains(RegistryTags.LEAVES, block);
	}

	private void handleCrammingDamage(LivingEntity entity) {
		if (ThreadLocalRandom.current().nextInt(4) != 0) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var position = entity.getPosition();
		var boundingBox = entity.getBoundingBox();
		var pushableEntities = instance.getEntities()
				.stream()
				.filter(nearbyEntity -> nearbyEntity != entity)
				.filter(nearbyEntity -> boundingBox.intersectEntity(position, nearbyEntity))
				.filter(this::isCrammingPushable)
				.limit(DEFAULT_MAX_ENTITY_CRAMMING)
				.count();

		if (pushableEntities > DEFAULT_MAX_ENTITY_CRAMMING - 1) {
			entity.damage(DamageType.CRAMMING, 6.0F);
		}
	}

	private boolean isCrammingPushable(Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) return false;
		if (livingEntity.isDead() || livingEntity.isRemoved()) return false;
		if (livingEntity.getVehicle() != null) return false;
		if (livingEntity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;

		return true;
	}

	private void handleWorldBorderDamage(Player player) {
		var instance = player.getInstance();

		if (instance == null) return;

		var worldBorder = instance.getWorldBorder();

		if (this.isWithinWorldBorder(player, worldBorder)) return;

		var distancePastSafeZone = -(this.getDistanceToWorldBorder(player, worldBorder) + WORLD_BORDER_SAFE_ZONE);

		if (distancePastSafeZone <= 0.0) return;

		var damage = (float) Math.max(1.0, Math.floor(distancePastSafeZone * WORLD_BORDER_DAMAGE_PER_BLOCK));
		player.damage(DamageType.OUTSIDE_BORDER, damage);
	}

	private boolean isWithinWorldBorder(Player player, WorldBorder worldBorder) {
		var position = player.getPosition();
		var boundingBox = player.getBoundingBox();
		var minX = position.x() + boundingBox.minX();
		var maxX = position.x() + boundingBox.maxX() - 1.0E-5F;
		var minZ = position.z() + boundingBox.minZ();
		var maxZ = position.z() + boundingBox.maxZ() - 1.0E-5F;

		return this.isWithinWorldBorder(minX, minZ, worldBorder)
				&& this.isWithinWorldBorder(maxX, maxZ, worldBorder);
	}

	private boolean isWithinWorldBorder(double blockX, double blockZ, WorldBorder worldBorder) {
		var radius = worldBorder.diameter() / 2.0;
		var minX = worldBorder.centerX() - radius;
		var maxX = worldBorder.centerX() + radius;
		var minZ = worldBorder.centerZ() - radius;
		var maxZ = worldBorder.centerZ() + radius;

		return blockX >= minX && blockX < maxX && blockZ >= minZ && blockZ < maxZ;
	}

	private double getDistanceToWorldBorder(Player player, WorldBorder worldBorder) {
		var position = player.getPosition();
		var radius = worldBorder.diameter() / 2.0;
		var minX = worldBorder.centerX() - radius;
		var maxX = worldBorder.centerX() + radius;
		var minZ = worldBorder.centerZ() - radius;
		var maxZ = worldBorder.centerZ() + radius;
		var distanceFromWest = position.x() - minX;
		var distanceFromEast = maxX - position.x();
		var distanceFromNorth = position.z() - minZ;
		var distanceFromSouth = maxZ - position.z();
		var distance = Math.min(distanceFromWest, distanceFromEast);
		distance = Math.min(distance, distanceFromNorth);

		return Math.min(distance, distanceFromSouth);
	}

	private void handleDrowning(LivingEntity entity) {
		if (entity instanceof Player player && player.getGameMode().invulnerable()) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		if (this.handleAquaticOutOfWaterDamage(instance, entity)) return;

		int airSupply = entity.hasTag(AIR_SUPPLY) ? entity.getTag(AIR_SUPPLY) : MAX_AIR_SUPPLY;

		if (this.isEyeInWater(entity)) {
			var eyeBlock = instance.getBlock(
					entity.getPosition().blockX(),
					(int) (entity.getPosition().y() + entity.getEyeHeight()),
					entity.getPosition().blockZ()
			);

			if (eyeBlock.compare(Block.BUBBLE_COLUMN)) {
				airSupply = this.increaseAirSupply(airSupply);
			} else if (this.canBreatheUnderwater(entity) || this.hasWaterBreathing(entity)) {

				if (airSupply < MAX_AIR_SUPPLY && this.shouldEffectsRefillAirSupply(entity)) {
					airSupply = this.increaseAirSupply(airSupply);
				}
			} else {
				airSupply = this.decreaseAirSupply(entity, airSupply);

				if (airSupply <= DROWN_THRESHOLD) {
					airSupply = 0;
					entity.triggerStatus((byte) 67);
					entity.damage(DamageType.DROWN, 2.0F);
				}
			}
		} else if (airSupply < MAX_AIR_SUPPLY) {
			airSupply = this.increaseAirSupply(airSupply);
		}

		entity.setTag(AIR_SUPPLY, airSupply);
		entity.getEntityMeta().setAirTicks(Math.max(airSupply, 0));
	}

	private void handleTurtleHelmetBreathing(Player player) {
		if (this.isEyeInWater(player)) return;

		if (!this.isEquipped(player, Material.TURTLE_HELMET)) return;

		player.addEffect(new Potion(
				PotionEffect.WATER_BREATHING,
				0,
				TURTLE_HELMET_WATER_BREATHING_TICKS,
				TURTLE_HELMET_WATER_BREATHING_FLAGS
		));
	}

	private boolean isEquipped(LivingEntity entity, Material material) {
		for (var slot : EquipmentSlot.values()) {
			var itemStack = entity.getEquipment(slot);

			if (itemStack.material() != material) continue;

			var equippable = itemStack.get(DataComponents.EQUIPPABLE);
			if (equippable != null && equippable.slot() == slot) {
				return true;
			}
		}

		return false;
	}

	private boolean handleAquaticOutOfWaterDamage(Instance instance, LivingEntity entity) {
		var entityType = entity.getEntityType();

		if (entityType == EntityType.DOLPHIN) {
			this.handleDolphinMoistness(instance, entity);
			return true;
		}

		if (WATER_ANIMAL_DROWN_TYPES.contains(entityType)) {
			this.handleAirSupplyOutOfWater(entity, !this.isTouchingWater(entity), DamageType.DROWN, 2.0F);
			return true;
		}

		if (DRY_OUT_AIR_SUPPLY_TYPES.contains(entityType)) {
			var shouldDecreaseAir = !this.isTouchingWater(entity) && !this.isInRain(instance, entity);
			this.handleAirSupplyOutOfWater(entity, shouldDecreaseAir, DamageType.DRY_OUT, 2.0F);
			return true;
		}

		return false;
	}

	private void handleDolphinMoistness(Instance instance, LivingEntity entity) {
		if (this.isTouchingWater(entity) || this.isInRain(instance, entity)) {
			entity.setTag(DOLPHIN_MOISTNESS, DOLPHIN_MOISTNESS_MAX);
			return;
		}

		int moistness = entity.hasTag(DOLPHIN_MOISTNESS) ? entity.getTag(DOLPHIN_MOISTNESS) : DOLPHIN_MOISTNESS_MAX;
		moistness--;
		entity.setTag(DOLPHIN_MOISTNESS, moistness);

		if (moistness <= 0) {
			entity.damage(DamageType.DRY_OUT, 1.0F);
		}
	}

	private void handleAirSupplyOutOfWater(LivingEntity entity, boolean shouldDecreaseAir,
	                                       RegistryKey<DamageType> damageType, float damageAmount) {
		int airSupply = entity.hasTag(AIR_SUPPLY) ? entity.getTag(AIR_SUPPLY) : MAX_AIR_SUPPLY;

		if (entity.isDead()) return;

		if (shouldDecreaseAir) {
			airSupply--;

			if (airSupply <= DROWN_THRESHOLD) {
				airSupply = 0;
				entity.damage(damageType, damageAmount);
			}
		} else {
			airSupply = MAX_AIR_SUPPLY;
		}

		entity.setTag(AIR_SUPPLY, airSupply);
		entity.getEntityMeta().setAirTicks(Math.max(airSupply, 0));
	}

	private void handleBlockContactDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		var position = entity.getPosition();
		var boundingBox = entity.getBoundingBox();

		int minX = (int) Math.floor(position.x() - boundingBox.width() / 2);
		int maxX = (int) Math.floor(position.x() + boundingBox.width() / 2);
		int minY = position.blockY();
		int maxY = (int) Math.floor(position.y() + boundingBox.height());
		int minZ = (int) Math.floor(position.z() - boundingBox.depth() / 2);
		int maxZ = (int) Math.floor(position.z() + boundingBox.depth() / 2);

		for (int blockX = minX; blockX <= maxX; blockX++) {
			for (int blockY = minY; blockY <= maxY; blockY++) {
				for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
					var block = instance.getBlock(blockX, blockY, blockZ);

					if (block.compare(Block.CACTUS)) {
						entity.damage(DamageType.CACTUS, 1.0F);
					} else if (block.compare(Block.SWEET_BERRY_BUSH)) {
						this.handleBerryBushDamage(entity, block);
					} else if (block.compare(Block.CAMPFIRE) || block.compare(Block.SOUL_CAMPFIRE)) {
						this.handleCampfireDamage(entity, block);
					} else if (block.compare(Block.WITHER_ROSE)) {
						this.handleWitherRoseEffect(entity);
					}
				}
			}
		}

		if (entity.isOnGround()) {
			var belowBlock = instance.getBlock(position.add(0, -0.5, 0));

			if (belowBlock.compare(Block.MAGMA_BLOCK) && !entity.isSneaking()) {
				entity.damage(DamageType.HOT_FLOOR, 1.0F);
			}
		}
	}

	private void handleWitherRoseEffect(LivingEntity entity) {
		if (this.difficultyProvider.getValue(entity) == Difficulty.PEACEFUL) return;
		if (entity instanceof Player player && player.getGameMode().invulnerable()) return;

		entity.addEffect(new Potion(PotionEffect.WITHER, 0, 40, PotionFlags.defaultFlags()));
	}

	private void handleBerryBushDamage(LivingEntity entity, Block block) {
		if (this.isBerryBushImmune(entity)) return;

		var ageProperty = block.getProperty("age");

		if (ageProperty == null || "0".equals(ageProperty)) return;

		Pos position = entity.getPosition();
		Pos previousPosition = entity.getPreviousPosition();
		double movementX = Math.abs(position.x() - previousPosition.x());
		double movementZ = Math.abs(position.z() - previousPosition.z());

		if (movementX >= 0.003 || movementZ >= 0.003) {
			entity.damage(DamageType.SWEET_BERRY_BUSH, 1.0F);
		}
	}

	private void handleCampfireDamage(LivingEntity entity, Block block) {
		var litProperty = block.getProperty("lit");

		if (!"true".equals(litProperty)) return;

		var fireDamage = block.compare(Block.SOUL_CAMPFIRE) ? 2.0F : 1.0F;
		entity.damage(DamageType.CAMPFIRE, fireDamage);
	}

	private void handleFreezeDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		int freezeTicks = entity.hasTag(FREEZE_TICKS) ? entity.getTag(FREEZE_TICKS) : 0;
		boolean inPowderSnow = this.isInPowderSnow(instance, entity);
		boolean canFreeze = this.canFreeze(entity);

		if (inPowderSnow && canFreeze) {
			freezeTicks = Math.min(freezeTicks + 1, FREEZE_MAX_TICKS);
		} else {
			freezeTicks = Math.max(freezeTicks - 2, 0);
		}

		entity.setTag(FREEZE_TICKS, freezeTicks);
		entity.getEntityMeta().setTickFrozen(freezeTicks);

		if (freezeTicks >= FREEZE_MAX_TICKS
				&& entity.getAliveTicks() % FREEZE_DAMAGE_INTERVAL == 0
				&& canFreeze) {
			entity.damage(DamageType.FREEZE, 1.0F);
		}
	}

	private void handleWaterSensitiveDamage(LivingEntity entity) {
		if (!WATER_SENSITIVE_TYPES.contains(entity.getEntityType())) return;

		var instance = entity.getInstance();

		if (instance == null) return;
		if (!this.isTouchingWater(entity) && !this.isInRain(instance, entity)) return;

		entity.damage(DamageType.DROWN, 1.0F);
	}

	private boolean canFreeze(LivingEntity entity) {
		if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
		if (this.isFreezeImmuneEntityType(entity)) return false;

		for (var slot : EquipmentSlot.armors()) {
			var material = entity.getEquipment(slot).material();

			if (RegistryTags.contains(RegistryTags.FREEZE_IMMUNE_WEARABLES, material)) return false;
		}

		return true;
	}

	private boolean isFreezeImmuneEntityType(LivingEntity entity) {
		return RegistryTags.contains(RegistryTags.FREEZE_IMMUNE_ENTITY_TYPES, entity.getEntityType());
	}

	private void extinguish(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		this.lowerWaterCauldron(instance, entity);
		entity.setFireTicks(0);

		ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
				SoundEvent.ENTITY_GENERIC_EXTINGUISH_FIRE, Sound.Source.NEUTRAL,
				0.7F, 1.0F
		), entity);
	}

	private void setFireTicks(LivingEntity entity, int fireTicks) {
		var adjustedFireTicks = this.enchantmentFeature.getFireDuration(entity, fireTicks);

		if (entity.getFireTicks() < adjustedFireTicks) {
			entity.setTag(VanillaEnchantmentFeature.FIRE_DURATION_ALREADY_SCALED, true);
			entity.setFireTicks(adjustedFireTicks);
		}

		this.clearFreeze(entity);
	}

	private void clearFreeze(LivingEntity entity) {
		entity.setTag(FREEZE_TICKS, 0);
		entity.getEntityMeta().setTickFrozen(0);
	}

	private boolean isInLava(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return false;

		var position = entity.getPosition();
		var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);

		if (FluidUtil.getFluidHeights(blockGetter, position, entity.getBoundingBox()).lava() > 0.0) return true;

		return this.isInLavaCauldron(blockGetter.getBlock(position), entity);
	}

	private @Nullable Block findBlockInside(LivingEntity entity, Predicate<Block> predicate) {
		var instance = entity.getInstance();

		if (instance == null) return null;

		var position = entity.getPosition();
		var boundingBox = entity.getBoundingBox();
		var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);

		int minX = (int) Math.floor(position.x() + boundingBox.minX() + 1.0E-5);
		int maxX = (int) Math.floor(position.x() + boundingBox.maxX() - 1.0E-5);
		int minY = (int) Math.floor(position.y() + boundingBox.minY() + 1.0E-5);
		int maxY = (int) Math.floor(position.y() + boundingBox.maxY() - 1.0E-5);
		int minZ = (int) Math.floor(position.z() + boundingBox.minZ() + 1.0E-5);
		int maxZ = (int) Math.floor(position.z() + boundingBox.maxZ() - 1.0E-5);

		for (int blockX = minX; blockX <= maxX; blockX++) {
			for (int blockY = minY; blockY <= maxY; blockY++) {
				for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
					var block = blockGetter.getBlock(blockX, blockY, blockZ);

					if (predicate.test(block)) return block;
				}
			}
		}

		return null;
	}

	private boolean isInLavaCauldron(Block block, LivingEntity entity) {
		if (!block.compare(Block.LAVA_CAULDRON)) return false;

		var position = entity.getPosition();
		var contentHeight = position.blockY() + 0.9375;
		var feetY = position.y() + entity.getBoundingBox().minY();

		return feetY <= contentHeight;
	}

	private boolean isTouchingWater(LivingEntity entity) {
		return FluidUtil.isTouchingWater(entity);
	}

	private boolean isInPowderSnow(Instance instance, LivingEntity entity) {
		return instance.getBlock(entity.getPosition()).compare(Block.POWDER_SNOW);
	}

	private boolean isInRain(Instance instance, LivingEntity entity) {
		if (!instance.getWeather().isRaining()) return false;
		if (!FluidUtil.hasOpenSky(instance)) return false;

		var position = entity.getPosition();
		var boundingBox = entity.getBoundingBox();

		if (this.isRainingAt(instance, position.blockX(), position.blockY(), position.blockZ())) return true;

		int topBlockY = CoordConversion.globalToBlock(position.y() + boundingBox.maxY());

		return this.isRainingAt(instance, position.blockX(), topBlockY, position.blockZ());
	}

	private boolean isRainingAt(Instance instance, int blockX, int blockY, int blockZ) {
		var chunk = instance.getChunkAt(blockX, blockZ);

		if (chunk == null) return false;

		var localBlockX = CoordConversion.globalToSectionRelative(blockX);
		var localBlockZ = CoordConversion.globalToSectionRelative(blockZ);
		var highestBlockY = chunk.motionBlockingHeightmap().getHeight(localBlockX, localBlockZ);

		return highestBlockY < blockY;
	}

	private boolean isInWaterCauldron(Instance instance, LivingEntity entity) {
		var position = entity.getPosition();
		var block = instance.getBlock(position);

		if (!block.compare(Block.WATER_CAULDRON)) return false;

		var levelProperty = block.getProperty("level");

		if (levelProperty == null) return false;

		int level = Integer.parseInt(levelProperty);
		double contentHeight = position.blockY() + (6.0 + level * 3.0) / 16.0;
		double feetY = position.y() + entity.getBoundingBox().minY();

		return feetY <= contentHeight;
	}

	private boolean lowerWaterCauldron(Instance instance, LivingEntity entity) {
		var position = entity.getPosition();
		var block = instance.getBlock(position);

		if (!block.compare(Block.WATER_CAULDRON)) return false;

		var levelProperty = block.getProperty("level");

		if (levelProperty == null) return false;

		int level = Integer.parseInt(levelProperty);

		if (level <= 1) {
			instance.setBlock(position, Block.CAULDRON);
		} else {
			instance.setBlock(position, block.withProperty("level", String.valueOf(level - 1)));
		}

		return true;
	}

	private boolean isEyeInWater(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return false;

		int eyeBlockX = entity.getPosition().blockX();
		int eyeBlockY = (int) Math.floor(entity.getPosition().y() + entity.getEyeHeight());
		int eyeBlockZ = entity.getPosition().blockZ();
		var eyeBlock = instance.getBlock(eyeBlockX, eyeBlockY, eyeBlockZ);

		return this.isWaterFluidBlock(eyeBlock);
	}

	private boolean isWaterFluidBlock(Block block) {
		return block.compare(Block.WATER)
				|| block.compare(Block.BUBBLE_COLUMN)
				|| "true".equals(block.getProperty("waterlogged"));
	}

	private boolean hasWaterBreathing(LivingEntity entity) {
		return entity.hasEffect(PotionEffect.WATER_BREATHING)
				|| entity.hasEffect(PotionEffect.CONDUIT_POWER)
				|| entity.hasEffect(PotionEffect.BREATH_OF_THE_NAUTILUS);
	}

	private boolean shouldEffectsRefillAirSupply(LivingEntity entity) {
		return !entity.hasEffect(PotionEffect.BREATH_OF_THE_NAUTILUS)
				|| entity.hasEffect(PotionEffect.WATER_BREATHING)
				|| entity.hasEffect(PotionEffect.CONDUIT_POWER);
	}

	private boolean canBreatheUnderwater(LivingEntity entity) {
		return RegistryTags.contains(RegistryTags.CAN_BREATHE_UNDER_WATER, entity.getEntityType());
	}

	private boolean isFireImmune(LivingEntity entity) {
		return entity.getEntityType().fireImmune();
	}

	private boolean isBerryBushImmune(LivingEntity entity) {
		var entityType = entity.getEntityType();

		return entityType == EntityType.FOX || entityType == EntityType.BEE;
	}

	private int decreaseAirSupply(LivingEntity entity, int currentSupply) {
		double oxygenBonus = entity.getAttributeValue(Attribute.OXYGEN_BONUS);

		if (oxygenBonus > 0.0 && Math.random() >= 1.0 / (oxygenBonus + 1.0)) {
			return currentSupply;
		}

		return currentSupply - 1;
	}

	private int increaseAirSupply(int currentSupply) {
		return Math.min(currentSupply + 4, MAX_AIR_SUPPLY);
	}
}
