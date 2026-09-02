package io.github.togar2.pvp.feature.effect;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.entity.projectile.Arrow;
import io.github.togar2.pvp.events.PotionVisibilityEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.food.FoodFeature;
import io.github.togar2.pvp.potion.effect.CombatPotionEffect;
import io.github.togar2.pvp.potion.effect.CombatPotionEffects;
import io.github.togar2.pvp.potion.item.CombatPotionType;
import io.github.togar2.pvp.potion.item.CombatPotionTypes;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.EffectUtil;
import io.github.togar2.pvp.utils.PotionFlags;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.entity.metadata.cube.SlimeMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.entity.EntityPotionAddEvent;
import net.minestom.server.event.entity.EntityPotionRemoveEvent;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.gamerule.GameRule;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.worldevent.WorldEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link EffectFeature}
 */
public class VanillaEffectFeature implements EffectFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEffectFeature> DEFINED = new DefinedFeature<>(
			FeatureType.EFFECT, VanillaEffectFeature::new,
			FeatureType.EXHAUSTION, FeatureType.FOOD, FeatureType.VERSION
	);

	public static final Tag<Map<PotionEffect, Integer>> DURATION_LEFT = Tag.Transient("effectDurationLeft");
	public static final Tag<Set<PotionEffect>> REPLACING_EFFECTS = Tag.Transient("replacingEffects");
	public static final Tag<Set<PotionEffect>> INTERNAL_EFFECT_UPDATES = Tag.Transient("internalEffectUpdates");
	public static final Tag<Map<PotionEffect, HiddenPotion>> HIDDEN_EFFECTS = Tag.Transient("hiddenEffects");
	public static final int DEFAULT_POTION_COLOR = 0xff385dc6;
	private static final double WIND_CHARGED_MIN_POWER = 3.0;
	private static final double WIND_CHARGED_RANDOM_POWER = 2.0;
	private static final int OOZING_SLIME_COUNT = 2;
	private static final int OOZING_SLIME_SIZE = 2;
	private static final int OOZING_SLIME_CHECK_RADIUS = 2;
	private static final int WEAVING_POSITION_ATTEMPTS = 15;
	private static final int WEAVING_RADIUS = 1;
	private static final int WEAVING_LEVEL_EVENT = 3018;
	private static final double BLOCK_EVENT_DISTANCE = 64.0;
	private static final double INFESTED_CHANCE = 0.1;
	private static final double INFESTED_VELOCITY_SCALE = 0.3;
	private static final double INFESTED_VERTICAL_VELOCITY_SCALE = 1.5;

	private final FeatureConfiguration configuration;

	private ExhaustionFeature exhaustionFeature;
	private FoodFeature foodFeature;
	private CombatVersion version;

	public VanillaEffectFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
		CombatPotionEffects.registerAll();
		CombatPotionTypes.registerAll();
	}

	@Override
	public void initDependencies() {
		this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
		this.foodFeature = this.configuration.get(FeatureType.FOOD);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityDeathEvent.class, event -> {
			if (event.getEntity() instanceof LivingEntity entity) {
				this.applyWindChargedBurst(entity);
				this.spawnOozingSlimes(entity);
				this.spawnWeavingCobwebs(entity);
			}

			event.getEntity().removeTag(HIDDEN_EFFECTS);
			event.getEntity().removeTag(DURATION_LEFT);
			event.getEntity().clearEffects();
		});

		node.addListener(EntityDamageEvent.class, event -> {
			var entity = event.getEntity();

			if (!entity.hasEffect(PotionEffect.INFESTED)) return;

			var previousLastDamage = entity.getLastDamageSource();

			entity.scheduler().scheduleNextProcess(() -> {
				if (entity.isRemoved() || entity.getLastDamageSource() == previousLastDamage) return;

				this.spawnInfestedSilverfish(entity);
			});
		});

		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;
			Map<PotionEffect, Integer> potionMap = this.getDurationLeftMap(entity);

			for (TimedPotion potion : entity.getActiveEffects()) {
				potionMap.putIfAbsent(potion.potion().effect(), potion.potion().duration() - 1);
				int durationLeft = potionMap.get(potion.potion().effect());

				if (durationLeft > 0) {
					CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.potion().effect());
					int amplifier = potion.potion().amplifier();

					if (combatPotionEffect.canApplyUpdateEffect(durationLeft, amplifier)) {
						combatPotionEffect.applyUpdateEffect(entity, amplifier, this.exhaustionFeature, this.foodFeature);
					}

					potionMap.put(potion.potion().effect(), durationLeft - 1);
				}
			}

			this.tickHiddenPotions(entity);

			if (entity instanceof Player player && player.hasEffect(PotionEffect.ABSORPTION) && player.getAdditionalHearts() <= 0) {
				player.removeEffect(PotionEffect.ABSORPTION);
			}

			if (potionMap.size() != entity.getActiveEffects().size()) {
				potionMap.keySet().removeIf(effect -> !entity.hasEffect(effect));
			}
		});

		node.addListener(EntityPotionAddEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;
			var potion = event.getPotion();

			if (this.isImmuneToPotion(entity, potion.effect())) {
				event.setCancelled(true);
				return;
			}

			var internalUpdate = this.getInternalEffectUpdates(entity).contains(potion.effect());
			if (!internalUpdate) {
				CombatPotionEffects.get(potion.effect()).onStarted(entity, potion.amplifier());
			}

			var currentPotion = entity.getEffect(potion.effect());
			if (!internalUpdate && currentPotion != null && this.updatePotionStack(entity, currentPotion, potion)) {
				event.setCancelled(true);
				return;
			}

			if (currentPotion != null) {
				this.getReplacingEffects(entity).add(potion.effect());
			}

			entity.scheduler().scheduleNextProcess(() -> {
				this.getReplacingEffects(entity).remove(potion.effect());

				if (!this.hasActivePotion(entity, potion)) {
					return;
				}

				var potionMap = this.getDurationLeftMap(entity);
				var infinite = potion.duration() == Potion.INFINITE_DURATION;
				potionMap.put(potion.effect(), infinite ? Integer.MAX_VALUE : potion.duration());

				var combatPotionEffect = CombatPotionEffects.get(potion.effect());
				combatPotionEffect.onApplied(entity, potion.amplifier(), this.version);

				this.updatePotionVisibility(entity);
			});
		});

		node.addListener(EntityPotionRemoveEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;

			if (this.getReplacingEffects(entity).contains(event.getPotion().effect())) {
				return;
			}

			CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(event.getPotion().effect());
			combatPotionEffect.onRemoved(entity, event.getPotion().amplifier(), this.version);
			this.promoteHiddenPotion(entity, event.getPotion().effect());

			entity.scheduler().scheduleNextTick(() -> this.updatePotionVisibility(entity));
		});
	}

	private Map<PotionEffect, Integer> getDurationLeftMap(Entity entity) {
		Map<PotionEffect, Integer> potionMap = entity.getTag(DURATION_LEFT);
		if (potionMap == null) {
			potionMap = new ConcurrentHashMap<>();
			entity.setTag(DURATION_LEFT, potionMap);
		}
		return potionMap;
	}

	private Set<PotionEffect> getReplacingEffects(Entity entity) {
		var replacingEffects = entity.getTag(REPLACING_EFFECTS);
		if (replacingEffects == null) {
			replacingEffects = ConcurrentHashMap.newKeySet();
			entity.setTag(REPLACING_EFFECTS, replacingEffects);
		}
		return replacingEffects;
	}

	private Set<PotionEffect> getInternalEffectUpdates(Entity entity) {
		var internalEffectUpdates = entity.getTag(INTERNAL_EFFECT_UPDATES);
		if (internalEffectUpdates == null) {
			internalEffectUpdates = ConcurrentHashMap.newKeySet();
			entity.setTag(INTERNAL_EFFECT_UPDATES, internalEffectUpdates);
		}
		return internalEffectUpdates;
	}

	private Map<PotionEffect, HiddenPotion> getHiddenEffects(Entity entity) {
		var hiddenEffects = entity.getTag(HIDDEN_EFFECTS);
		if (hiddenEffects == null) {
			hiddenEffects = new ConcurrentHashMap<PotionEffect, HiddenPotion>();
			entity.setTag(HIDDEN_EFFECTS, hiddenEffects);
		}
		return hiddenEffects;
	}

	private boolean hasActivePotion(LivingEntity entity, Potion potion) {
		return entity.getActiveEffects().stream()
				.anyMatch(timedPotion -> timedPotion.potion() == potion);
	}

	private boolean updatePotionStack(LivingEntity entity, TimedPotion currentTimedPotion, Potion potion) {
		var currentPotion = currentTimedPotion.potion();
		var currentDuration = this.getRemainingDuration(entity, currentTimedPotion);
		var activePotion = new Potion(currentPotion.effect(), currentPotion.amplifier(), currentDuration, currentPotion.flags());
		var hiddenEffects = this.getHiddenEffects(entity);
		var hiddenPotion = hiddenEffects.get(potion.effect());
		var activeChanged = false;

		if (potion.amplifier() > activePotion.amplifier()) {
			if (this.isShorterDurationThan(potion, activePotion)) {
				hiddenPotion = new HiddenPotion(activePotion, hiddenPotion);
				hiddenEffects.put(potion.effect(), hiddenPotion);
			}

			return false;
		}

		if (this.isShorterDurationThan(activePotion, potion)) {
			if (potion.amplifier() == activePotion.amplifier()) {
				activePotion = new Potion(potion.effect(), potion.amplifier(), potion.duration(), potion.flags());
				activeChanged = true;
			} else {
				hiddenPotion = this.updateHiddenPotion(hiddenPotion, potion);
				hiddenEffects.put(potion.effect(), hiddenPotion);
			}
		}

		var mergedFlags = this.mergePotionFlags(activePotion, potion, activeChanged);
		if (mergedFlags != activePotion.flags()) {
			activePotion = new Potion(activePotion.effect(), activePotion.amplifier(), activePotion.duration(), mergedFlags);
			activeChanged = true;
		}

		if (activeChanged) {
			this.replaceActivePotion(entity, activePotion);
		}

		return true;
	}

	private HiddenPotion updateHiddenPotion(@Nullable HiddenPotion hiddenPotion, Potion potion) {
		if (hiddenPotion == null) {
			return new HiddenPotion(potion, null);
		}

		var currentPotion = hiddenPotion.potion();
		var nextHiddenPotion = hiddenPotion.hiddenPotion();

		if (potion.amplifier() > currentPotion.amplifier()) {
			if (this.isShorterDurationThan(potion, currentPotion)) {
				nextHiddenPotion = new HiddenPotion(currentPotion, nextHiddenPotion);
			}

			return new HiddenPotion(potion, nextHiddenPotion);
		}

		if (this.isShorterDurationThan(currentPotion, potion)) {
			if (potion.amplifier() == currentPotion.amplifier()) {
				currentPotion = potion;
			} else {
				nextHiddenPotion = this.updateHiddenPotion(nextHiddenPotion, potion);
			}
		}

		return new HiddenPotion(
				new Potion(currentPotion.effect(), currentPotion.amplifier(), currentPotion.duration(), this.mergePotionFlags(currentPotion, potion, false)),
				nextHiddenPotion
		);
	}

	private byte mergePotionFlags(Potion currentPotion, Potion potion, boolean activeChanged) {
		var flags = currentPotion.flags();
		var ambient = (potion.flags() & Potion.AMBIENT_FLAG) == Potion.AMBIENT_FLAG;
		var currentAmbient = (flags & Potion.AMBIENT_FLAG) == Potion.AMBIENT_FLAG;
		if (activeChanged || !ambient && currentAmbient) {
			flags = (byte) (ambient ? flags | Potion.AMBIENT_FLAG : flags & ~Potion.AMBIENT_FLAG);
		}

		flags = (byte) ((flags & ~(Potion.PARTICLES_FLAG | Potion.ICON_FLAG | Potion.BLEND_FLAG))
				| (potion.flags() & (Potion.PARTICLES_FLAG | Potion.ICON_FLAG | Potion.BLEND_FLAG)));
		return flags;
	}

	private boolean isShorterDurationThan(Potion potion, Potion otherPotion) {
		return potion.duration() != Potion.INFINITE_DURATION
				&& (potion.duration() < otherPotion.duration() || otherPotion.duration() == Potion.INFINITE_DURATION);
	}

	private int getRemainingDuration(LivingEntity entity, TimedPotion timedPotion) {
		if (timedPotion.potion().duration() == Potion.INFINITE_DURATION) {
			return Potion.INFINITE_DURATION;
		}

		var durationLeft = this.getDurationLeftMap(entity).get(timedPotion.potion().effect());
		if (durationLeft != null) {
			return durationLeft;
		}

		var elapsedTicks = (int) (entity.getAliveTicks() - timedPotion.startingTicks());
		return Math.max(timedPotion.potion().duration() - elapsedTicks, 0);
	}

	private void tickHiddenPotions(LivingEntity entity) {
		var hiddenEffects = entity.getTag(HIDDEN_EFFECTS);
		if (hiddenEffects == null || hiddenEffects.isEmpty()) {
			return;
		}

		hiddenEffects.replaceAll((effect, hiddenPotion) -> this.tickHiddenPotion(hiddenPotion));
	}

	private HiddenPotion tickHiddenPotion(HiddenPotion hiddenPotion) {
		var potion = hiddenPotion.potion();
		var duration = potion.duration();
		if (duration != Potion.INFINITE_DURATION && duration > 0) {
			potion = new Potion(potion.effect(), potion.amplifier(), duration - 1, potion.flags());
		}

		var nextHiddenPotion = hiddenPotion.hiddenPotion() == null ? null : this.tickHiddenPotion(hiddenPotion.hiddenPotion());
		return new HiddenPotion(potion, nextHiddenPotion);
	}

	private void replaceActivePotion(LivingEntity entity, Potion potion) {
		var replacingEffects = this.getReplacingEffects(entity);
		var internalEffectUpdates = this.getInternalEffectUpdates(entity);
		replacingEffects.add(potion.effect());
		internalEffectUpdates.add(potion.effect());

		entity.scheduler().scheduleNextProcess(() -> {
			entity.addEffect(potion);
			entity.scheduler().scheduleNextProcess(() -> {
				replacingEffects.remove(potion.effect());
				internalEffectUpdates.remove(potion.effect());
			});
		});
	}

	public static int getHiddenAmplifier(LivingEntity entity, PotionEffect potionEffect) {
		var hiddenEffects = entity.getTag(HIDDEN_EFFECTS);
		if (hiddenEffects == null) return -1;

		var hiddenPotion = hiddenEffects.get(potionEffect);
		return hiddenPotion == null ? -1 : hiddenPotion.potion().amplifier();
	}

	private void promoteHiddenPotion(LivingEntity entity, PotionEffect potionEffect) {
		var hiddenEffects = entity.getTag(HIDDEN_EFFECTS);
		if (hiddenEffects == null) {
			return;
		}

		var hiddenPotion = hiddenEffects.remove(potionEffect);
		if (hiddenPotion == null || hiddenPotion.potion().duration() == 0) {
			return;
		}

		if (hiddenPotion.hiddenPotion() != null) {
			hiddenEffects.put(potionEffect, hiddenPotion.hiddenPotion());
		}

		this.replaceActivePotion(entity, hiddenPotion.potion());
	}

	private boolean isImmuneToPotion(LivingEntity entity, PotionEffect potionEffect) {
		var entityType = entity.getEntityType();

		if ((potionEffect == PotionEffect.POISON || potionEffect == PotionEffect.REGENERATION)
				&& EntityGroup.ignoresPoisonAndRegen(entity)) {
			return true;
		}

		return (entityType == EntityType.SILVERFISH && potionEffect == PotionEffect.INFESTED)
				|| (entityType == EntityType.SLIME && potionEffect == PotionEffect.OOZING);
	}

	private record HiddenPotion(Potion potion, @Nullable HiddenPotion hiddenPotion) {
	}

	private void applyWindChargedBurst(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.WIND_CHARGED)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var random = ThreadLocalRandom.current();
		var position = entity.getPosition().add(0.0, entity.getBoundingBox().height() / 2.0F, 0.0);
		var power = WIND_CHARGED_MIN_POWER + random.nextDouble(WIND_CHARGED_RANDOM_POWER);

		entity.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_SMALL, false, false,
				position.x(), position.y(), position.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));
		entity.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_LARGE, false, false,
				position.x(), position.y(), position.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));
		ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
				SoundEvent.ENTITY_BREEZE_WIND_BURST,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0F, 1.0F
		), entity);

		var radius = power * 2.0;

		for (var nearbyEntity : instance.getNearbyEntities(position, radius)) {
			if (nearbyEntity == entity) {
				continue;
			}

			this.applyWindChargedKnockback(position, radius, nearbyEntity);
		}
	}

	private void applyWindChargedKnockback(Point position, double radius, Entity nearbyEntity) {
		if (nearbyEntity instanceof Player player && !this.shouldApplyWindChargedKnockback(player)) {
			return;
		}

		var originY = nearbyEntity.getPosition().y() + nearbyEntity.getEyeHeight();
		var origin = new Vec(nearbyEntity.getPosition().x(), originY, nearbyEntity.getPosition().z());
		var direction = origin.sub(position.asVec());
		var distance = direction.length();

		if (distance <= 0.0 || distance > radius) {
			return;
		}

		var exposure = VanillaExplosionSupplier.getExposure(position, nearbyEntity);
		var knockback = (1.0 - distance / radius) * exposure;

		if (nearbyEntity instanceof LivingEntity nearbyLiving) {
			knockback *= 1.0 - nearbyLiving.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
		}

		var knockbackVector = direction.normalize().mul(knockback);
		var velocity = nearbyEntity.getVelocity();

		nearbyEntity.setVelocity(velocity.add(knockbackVector.mul(ServerFlag.SERVER_TICKS_PER_SECOND)));
	}

	private boolean shouldApplyWindChargedKnockback(Player player) {
		return player.getGameMode() != GameMode.SPECTATOR
				&& (player.getGameMode() != GameMode.CREATIVE || !player.isFlying());
	}

	private void spawnOozingSlimes(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.OOZING)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var maxEntityCramming = this.getMaxEntityCramming();
		var nearbySlimes = maxEntityCramming < 1 ? 0 : instance.getNearbyEntities(entity.getPosition(), OOZING_SLIME_CHECK_RADIUS).stream()
				.filter(nearbyEntity -> nearbyEntity.getEntityType() == EntityType.SLIME)
				.limit(maxEntityCramming)
				.count();
		var spawnCount = maxEntityCramming < 1 ? OOZING_SLIME_COUNT : Math.clamp(maxEntityCramming - (int) nearbySlimes, 0, OOZING_SLIME_COUNT);
		var random = ThreadLocalRandom.current();

		for (var slimeNumber = 0; slimeNumber < spawnCount; slimeNumber++) {
			var slime = new Entity(EntityType.SLIME);

			if (slime.getEntityMeta() instanceof SlimeMeta slimeMeta) {
				slimeMeta.setSize(OOZING_SLIME_SIZE);
			}

			var position = new Pos(entity.getPosition().x(), entity.getPosition().y() + 0.5, entity.getPosition().z(), random.nextFloat(360.0F), 0.0F);
			slime.setInstance(instance, position);
		}
	}

	private void spawnWeavingCobwebs(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.WEAVING)) return;
		if (!this.canWeavingPlaceCobwebs(entity)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var random = ThreadLocalRandom.current();
		var cobwebCount = random.nextInt(2, 4);
		var positions = new HashSet<BlockVec>();
		var origin = entity.getPosition();

		for (var attempt = 0; attempt < WEAVING_POSITION_ATTEMPTS; attempt++) {
			var blockPosition = new BlockVec(
					origin.blockX() + random.nextInt(-WEAVING_RADIUS, WEAVING_RADIUS + 1),
					origin.blockY() + random.nextInt(-WEAVING_RADIUS, WEAVING_RADIUS + 1),
					origin.blockZ() + random.nextInt(-WEAVING_RADIUS, WEAVING_RADIUS + 1)
			);

			if (positions.contains(blockPosition)) {
				continue;
			}

			var block = instance.getBlock(blockPosition);
			var below = blockPosition.sub(0, 1, 0);

			if (!block.replaceable() || !instance.getBlock(below).solid()) {
				continue;
			}

			positions.add(blockPosition);

			if (positions.size() >= cobwebCount) {
				break;
			}
		}

		for (var position : positions) {
			instance.setBlock(position, Block.COBWEB);
			EffectUtil.sendNearby(instance, WorldEvent.fromId(WEAVING_LEVEL_EVENT), position.blockX(), position.blockY(), position.blockZ(), 0, BLOCK_EVENT_DISTANCE, false);
		}
	}

	private void spawnInfestedSilverfish(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.INFESTED)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var random = ThreadLocalRandom.current();

		if (random.nextDouble() > INFESTED_CHANCE) return;

		var spawnCount = random.nextInt(1, 3);
		var position = entity.getPosition();

		for (var silverfishNumber = 0; silverfishNumber < spawnCount; silverfishNumber++) {
			var silverfish = new Entity(EntityType.SILVERFISH);
			var randomAngle = random.nextDouble(-Math.PI / 2.0, Math.PI / 2.0);
			var velocity = position.direction()
					.mul(INFESTED_VELOCITY_SCALE)
					.mul(1.0, INFESTED_VERTICAL_VELOCITY_SCALE, 1.0)
					.rotateAroundY(randomAngle);

			silverfish.setVelocity(velocity.mul(ServerFlag.SERVER_TICKS_PER_SECOND));
			silverfish.setInstance(instance, new Pos(position.x(), position.y() + entity.getBoundingBox().height() / 2.0, position.z(), random.nextFloat(360.0F), 0.0F));
			entity.getViewersAsAudience().playSound(Sound.sound(
					SoundEvent.ENTITY_SILVERFISH_HURT,
					Sound.Source.HOSTILE,
					1.0F, 1.0F
			), silverfish);
		}
	}

	private int getMaxEntityCramming() {
		return GameRule.MAX_ENTITY_CRAMMING.defaultValue();
	}

	private boolean canWeavingPlaceCobwebs(LivingEntity entity) {
		return entity instanceof Player || GameRule.MOB_GRIEFING.defaultValue();
	}

	@Override
	public int getPotionColor(PotionContents contents) {
		if (contents.customColor() != null) {
			RGBLike rgbLike = contents.customColor();
			return PotionColorUtils.rgba(255, rgbLike.red(), rgbLike.green(), rgbLike.blue());
		} else if (contents.equals(PotionContents.EMPTY)) {
			return DEFAULT_POTION_COLOR;
		} else {
			Collection<Potion> effects = this.getAllPotions(contents);
			int color = PotionColorUtils.getPotionColor(effects);
			return color == -1 ? DEFAULT_POTION_COLOR : color;
		}
	}

	@Override
	public List<Potion> getAllPotions(PotionType potionType,
	                                  Collection<net.minestom.server.potion.CustomPotionEffect> customEffects) {
		// PotionType effects plus custom effects
		List<Potion> potions = new ArrayList<>();

		CombatPotionType combatPotionType = CombatPotionTypes.get(potionType);
		if (combatPotionType != null) potions.addAll(combatPotionType.getEffects(this.version));

		potions.addAll(customEffects.stream().map((customPotion) ->
				new Potion(Objects.requireNonNull(customPotion.id()),
						(byte)customPotion.amplifier(), customPotion.duration(),
						PotionFlags.create(
								customPotion.isAmbient(),
								customPotion.showParticles(),
								customPotion.showIcon()
						))).toList());

		return potions;
	}

	@Override
	public void updatePotionVisibility(LivingEntity entity) {
		boolean ambient;
		List<Particle> particles;
		boolean invisible;

		if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
			ambient = false;
			particles = List.of();
			invisible = true;
		} else {
			Collection<TimedPotion> effects = entity.getActiveEffects();
			if (effects.isEmpty()) {
				ambient = false;
				particles = List.of();
				invisible = false;
			} else {
				ambient = true;
				particles = new ArrayList<>();

				for (TimedPotion potion : effects) {
					if (!potion.potion().isAmbient()) {
						ambient = false;
					}

					if (potion.potion().hasParticles()) {
						CombatPotionEffect effect = CombatPotionEffects.get(potion.potion().effect());
						particles.add(effect.getParticle(potion.potion()));
					}
				}

				invisible = entity.hasEffect(PotionEffect.INVISIBILITY);
			}
		}

		PotionVisibilityEvent potionVisibilityEvent = new PotionVisibilityEvent(entity, ambient, particles, invisible);
		EventDispatcher.callCancellable(potionVisibilityEvent, () -> {
			LivingEntityMeta meta = (LivingEntityMeta) entity.getEntityMeta();

			meta.setPotionEffectAmbient(potionVisibilityEvent.isAmbient());
			meta.setEffectParticles(potionVisibilityEvent.getParticles());
			meta.setInvisible(potionVisibilityEvent.isInvisible());
		});
	}

	@Override
	public void addArrowEffects(LivingEntity entity, Arrow arrow) {
		PotionContents potionContents = arrow.getPotion();
		float durationScale = arrow.getPotionDurationScale();

		CombatPotionType combatPotionType = CombatPotionTypes.get(potionContents.potion());
		if (combatPotionType != null) {
			for (Potion potion : combatPotionType.getEffects(this.version)) {
				CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
				if (combatPotionEffect.isInstant()) {
					combatPotionEffect.applyInstantEffect(arrow, null,
							entity, potion.amplifier(), 1.0, this.exhaustionFeature, this.foodFeature);
				} else {
					int duration = Math.max((int) (potion.duration() * durationScale), 1);
					entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
				}
			}
		}

		if (potionContents.customEffects().isEmpty()) return;

		potionContents.customEffects().stream().map(customPotion ->
						new Potion(Objects.requireNonNull(customPotion.id()),
								(byte)customPotion.amplifier(), customPotion.duration(),
								PotionFlags.create(
										customPotion.isAmbient(),
										customPotion.showParticles(),
										customPotion.showIcon()
								)))
				.forEach(potion -> {
					CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
					if (combatPotionEffect.isInstant()) {
						combatPotionEffect.applyInstantEffect(arrow, null,
								entity, potion.amplifier(), 1.0, this.exhaustionFeature, this.foodFeature);
					} else {
						var duration = Math.max((int) (potion.duration() * durationScale), 1);
						entity.addEffect(new Potion(potion.effect(), potion.amplifier(),
								duration, potion.flags()));
					}
				});
	}

	@Override
	public void addSplashPotionEffects(LivingEntity entity, PotionContents potionContents, double proximity,
	                                   @Nullable Entity source, @Nullable Entity attacker) {
		for (Potion potion : this.getAllPotions(potionContents)) {
			CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
			if (combatPotionEffect.isInstant()) {
				combatPotionEffect.applyInstantEffect(source, attacker,
						entity, potion.amplifier(), proximity, this.exhaustionFeature, this.foodFeature);
			} else {
				int duration = potion.duration();
				if (this.version.legacy()) duration = (int) Math.floor(duration * 0.75);
				duration = (int) (proximity * (double) duration + 0.5);

				if (duration > 20) {
					entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
				}
			}
		}
	}

	@Override
	public void addLingeringPotionEffects(LivingEntity entity, PotionContents potionContents,
	                                      @Nullable Entity source, @Nullable Entity attacker) {
		for (var potion : this.getAllPotions(potionContents)) {
			var combatPotionEffect = CombatPotionEffects.get(potion.effect());

			if (combatPotionEffect.isInstant()) {
				combatPotionEffect.applyInstantEffect(source, attacker,
						entity, potion.amplifier(), 0.5, this.exhaustionFeature, this.foodFeature);
			} else {
				var duration = Math.max(potion.duration() / 4, 1);
				entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
			}
		}
	}
}
