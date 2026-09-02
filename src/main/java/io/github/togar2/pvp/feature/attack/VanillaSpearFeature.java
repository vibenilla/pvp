package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.AttackCooldownFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.CollisionUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import java.util.Comparator;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.player.PlayerStabEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttackRange;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla implementation of {@link SpearFeature}
 */
public class VanillaSpearFeature implements SpearFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaSpearFeature> DEFINED = new DefinedFeature<>(
			FeatureType.SPEAR, VanillaSpearFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.ENCHANTMENT, FeatureType.KNOCKBACK, FeatureType.EXHAUSTION, FeatureType.FALL,
			FeatureType.ATTACK_COOLDOWN, FeatureType.PLAYER_STATE
	);

	public static final Tag<Long> SPEAR_USE_START = Tag.Long("spearUseStart");

	private static final long SPEAR_USE_TIME = 72000L;
	private static final int CONTACT_COOLDOWN_TICKS = 10;
	private static final AttackRange DEFAULT_ATTACK_RANGE = new AttackRange(0.0F, 3.0F, 0.0F, 5.0F, 0.3F, 1.0F);
	private static final float PIERCING_KNOCKBACK_BONUS = 0.4F;
	private static final float STAB_KNOCKBACK_BONUS = 0.4F;
	private static final float LUNGE_IMPULSE_PER_LEVEL = 0.458F;
	private static final float LUNGE_EXHAUSTION_PER_LEVEL = 4.0F;
	private static final int LUNGE_POST_IMPULSE_GRACE_TICKS = 10;
	private static final double STAB_CHARGE_TOLERANCE_TICKS = 5.0;

	private record SpearProperties(
			float damageMultiplier,
			int delayTicks,
			int dismountMaxTicks, float dismountMinSpeed,
			int knockbackMaxTicks, float knockbackMinSpeed,
			int damageMaxTicks, float damageMinRelativeSpeed
	) {}

	private static final Map<Tool, SpearProperties> SPEAR_PROPERTIES = new EnumMap<>(Tool.class);

	static {
		// Values from Items.java: spear(material, attackDuration, damageMultiplier, delay,
		//   dismountTime, dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold)
		SPEAR_PROPERTIES.put(Tool.WOODEN_SPEAR, new SpearProperties(
				0.7F, (int) (0.75F * 20),
				(int) (5.0F * 20), 14.0F,
				(int) (10.0F * 20), 5.1F,
				(int) (15.0F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.STONE_SPEAR, new SpearProperties(
				0.82F, (int) (0.7F * 20),
				(int) (4.5F * 20), 13.0F,
				(int) (9.0F * 20), 5.1F,
				(int) (13.75F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.COPPER_SPEAR, new SpearProperties(
				0.82F, (int) (0.65F * 20),
				(int) (4.0F * 20), 12.0F,
				(int) (8.25F * 20), 5.1F,
				(int) (12.5F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.IRON_SPEAR, new SpearProperties(
				0.95F, (int) (0.6F * 20),
				(int) (2.5F * 20), 11.0F,
				(int) (6.75F * 20), 5.1F,
				(int) (11.25F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.GOLDEN_SPEAR, new SpearProperties(
				0.7F, (int) (0.7F * 20),
				(int) (3.5F * 20), 13.0F,
				(int) (8.5F * 20), 5.1F,
				(int) (13.75F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.DIAMOND_SPEAR, new SpearProperties(
				1.075F, (int) (0.5F * 20),
				(int) (3.0F * 20), 10.0F,
				(int) (6.5F * 20), 5.1F,
				(int) (10.0F * 20), 4.6F
		));
		SPEAR_PROPERTIES.put(Tool.NETHERITE_SPEAR, new SpearProperties(
				1.2F, (int) (0.4F * 20),
				(int) (2.5F * 20), 9.0F,
				(int) (5.5F * 20), 5.1F,
				(int) (8.75F * 20), 4.6F
		));
	}

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private EnchantmentFeature enchantmentFeature;
	private KnockbackFeature knockbackFeature;
	private ExhaustionFeature exhaustionFeature;
	private FallFeature fallFeature;
	private AttackCooldownFeature attackCooldownFeature;
	private PlayerStateFeature playerStateFeature;

	private static final Tag<Map<UUID, Long>> RECENT_STABS = Tag.Transient("spearRecentStabs");

	public VanillaSpearFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.knockbackFeature = this.configuration.get(FeatureType.KNOCKBACK);
		this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
		this.fallFeature = this.configuration.get(FeatureType.FALL);
		this.attackCooldownFeature = this.configuration.get(FeatureType.ATTACK_COOLDOWN);
		this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerStabEvent.class, event -> {
			Player player = event.getPlayer();
			ItemStack stack = player.getItemInMainHand();
			Tool tool = Tool.fromMaterial(stack.material());
			if (tool == null || !tool.isSpear()) return;
			if (this.attackCooldownFeature.cannotAttackWith(player, stack, STAB_CHARGE_TOLERANCE_TICKS)) return;

			this.performPiercingAttack(player, tool);
		});

		node.addListener(PlayerUseItemEvent.class, event -> {
			Tool tool = Tool.fromMaterial(event.getItemStack().material());
			if (tool == null || !tool.isSpear()) return;

			event.setItemUseTime(SPEAR_USE_TIME);
			event.getPlayer().setTag(SPEAR_USE_START, event.getPlayer().getAliveTicks());

			SoundEvent useSound = tool == Tool.WOODEN_SPEAR ? SoundEvent.ITEM_SPEAR_WOOD_USE : SoundEvent.ITEM_SPEAR_USE;
			ViewUtil.viewersAndSelf(event.getPlayer()).playSound(Sound.sound(
					useSound, Sound.Source.PLAYER,
					1.0F, 1.0F
			), event.getPlayer());
		});

		node.addListener(PlayerTickEvent.class, event -> {
			Player player = event.getPlayer();

			Vec knownMotion = this.playerStateFeature.getKnownMovement(player);

			if (player.getItemUseHand() == null) return;

			ItemStack usingStack = player.getItemInHand(player.getItemUseHand());
			Tool tool = Tool.fromMaterial(usingStack.material());
			if (tool == null || !tool.isSpear()) return;

			SpearProperties properties = SPEAR_PROPERTIES.get(tool);
			if (properties == null) return;

			Long startTickObject = player.getTag(SPEAR_USE_START);
			if (startTickObject == null) return;

			long ticksUsed = player.getAliveTicks() - startTickObject;
			if (ticksUsed < properties.delayTicks()) return;

			this.performKineticStab(player, tool, properties, (int) (ticksUsed - properties.delayTicks()), player.getItemUseHand(), knownMotion);
		});

		node.addListener(PlayerCancelItemUseEvent.class, event -> {
			Player player = event.getPlayer();
			Tool tool = Tool.fromMaterial(event.getItemStack().material());
			if (tool == null || !tool.isSpear()) return;

			player.removeTag(SPEAR_USE_START);
			player.removeTag(RECENT_STABS);
		});
	}

	private void performPiercingAttack(Player attacker, Tool tool) {
		float baseDamage = (float) attacker.getAttributeValue(Attribute.ATTACK_DAMAGE);
		ItemStack weapon = attacker.getItemInMainHand();
		List<Entity> hitEntities = this.findEntitiesAlongRay(attacker, weapon);

		double cooldownProgress = 1.0;
		if (attacker.getItemUseHand() != PlayerHand.MAIN) {
			cooldownProgress = this.attackCooldownFeature.getAttackCooldownProgress(attacker);
			baseDamage *= (float) (0.2 + cooldownProgress * cooldownProgress * 0.8);
		}

		boolean hitSomething = false;
		for (Entity target : hitEntities) {
			float magicalDamage = this.enchantmentFeature.getAttackDamage(weapon, target)
					* (float) cooldownProgress;
			float totalDamage = baseDamage + magicalDamage;

			boolean damaged = this.damageTarget(target, new Damage(
					DamageType.SPEAR,
					attacker, attacker,
					null, totalDamage
			));

			if (target instanceof LivingEntity living) {
				this.knockbackFeature.applyAttackKnockback(attacker, living, PIERCING_KNOCKBACK_BONUS);
				this.knockbackFeature.applyAttackKnockback(attacker, living,
						this.enchantmentFeature.getKnockback(attacker, weapon));

				if (damaged) {
					this.enchantmentFeature.onUserDamaged(living, attacker);
					this.enchantmentFeature.onTargetDamaged(attacker, living, weapon);
				}
			}

			hitSomething = true;
			this.itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, 1);
			this.exhaustionFeature.addAttackExhaustion(attacker);
		}

		this.attackCooldownFeature.resetCooldownProgress(attacker);
		this.applyLungeEffect(attacker);
		this.playPiercingSounds(attacker, tool, hitSomething);
	}

	private void applyLungeEffect(Player attacker) {
		int lungeLevel = this.enchantmentFeature.getEquipmentLevel(attacker, Enchantment.LUNGE);
		if (lungeLevel <= 0) return;

		if (attacker.getVehicle() != null) return;
		if (attacker.isFlyingWithElytra()) return;
		if (FluidUtil.isTouchingWater(attacker)) return;
		if (attacker.getGameMode() != GameMode.CREATIVE && attacker.getFood() < 7) return;

		Vec direction = attacker.getPosition().direction();
		Vec horizontalDirection = new Vec(direction.x(), 0, direction.z());

		double impulseMagnitude = LUNGE_IMPULSE_PER_LEVEL * lungeLevel;
		Vec velocity = attacker.getVelocity();
		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;

		attacker.setVelocity(new Vec(
				velocity.x() + horizontalDirection.x() * impulseMagnitude * tps,
				velocity.y(),
				velocity.z() + horizontalDirection.z() * impulseMagnitude * tps
		));
		this.fallFeature.applyPostImpulseGraceTime(attacker, LUNGE_POST_IMPULSE_GRACE_TICKS);

		this.exhaustionFeature.addExhaustion(attacker, LUNGE_EXHAUSTION_PER_LEVEL * lungeLevel);
		this.itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, 1);

		SoundEvent[] lungeSounds = {
				SoundEvent.ITEM_SPEAR_LUNGE_1,
				SoundEvent.ITEM_SPEAR_LUNGE_2,
				SoundEvent.ITEM_SPEAR_LUNGE_3
		};
		SoundEvent lungeSound = lungeSounds[ThreadLocalRandom.current().nextInt(lungeSounds.length)];
		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
				lungeSound, Sound.Source.PLAYER,
				1.0F, 1.0F
		), attacker);
	}

	private void performKineticStab(Player attacker, Tool tool, SpearProperties properties, int ticksUsed, PlayerHand hand, Vec attackerKnownMotion) {
		Vec attackerLook = attacker.getPosition().direction();
		double attackerSpeedProjection = attackerLook.dot(attackerKnownMotion);
		ItemStack weapon = attacker.getItemInHand(hand);

		List<Entity> entities = this.findEntitiesAlongRay(attacker, weapon);
		boolean affected = false;

		long currentTick = attacker.getAliveTicks();
		Map<UUID, Long> playerStabs = attacker.getTag(RECENT_STABS);
		if (playerStabs == null) {
			playerStabs = new HashMap<>();
			attacker.setTag(RECENT_STABS, playerStabs);
		}

		playerStabs.entrySet().removeIf(entry -> currentTick - entry.getValue() > CONTACT_COOLDOWN_TICKS);

		for (Entity target : entities) {
			Long lastStabTick = playerStabs.get(target.getUuid());
			if (lastStabTick != null && currentTick - lastStabTick < CONTACT_COOLDOWN_TICKS) continue;

			playerStabs.put(target.getUuid(), currentTick);

			double targetSpeedProjection = attackerLook.dot(this.getKnownMotion(target));
			double relativeSpeed = Math.max(0.0, attackerSpeedProjection - targetSpeedProjection);

			boolean dealsDismount = this.testCondition(ticksUsed, attackerSpeedProjection, relativeSpeed,
					properties.dismountMaxTicks(), properties.dismountMinSpeed(), 0.0F);
			boolean dealsKnockback = this.testCondition(ticksUsed, attackerSpeedProjection, relativeSpeed,
					properties.knockbackMaxTicks(), properties.knockbackMinSpeed(), 0.0F);
			boolean dealsDamage = this.testCondition(ticksUsed, attackerSpeedProjection, relativeSpeed,
					properties.damageMaxTicks(), 0.0F, properties.damageMinRelativeSpeed());

			if (!dealsDismount && !dealsKnockback && !dealsDamage) continue;

			float baseMobDamage = (float) attacker.getAttribute(Attribute.ATTACK_DAMAGE).getBaseValue();
			float damageDealt = baseMobDamage + (float) Math.floor(relativeSpeed * properties.damageMultiplier())
					+ this.enchantmentFeature.getAttackDamage(weapon, target);

			boolean stabAffected = this.applyStabAttack(attacker, target, weapon, damageDealt, dealsDamage, dealsKnockback, dealsDismount);
			if (stabAffected) {
				affected = true;
				this.itemDamageFeature.damageEquipment(attacker,
						hand == PlayerHand.MAIN ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, 1);
				this.exhaustionFeature.addAttackExhaustion(attacker);
			}
		}

		if (affected) {
			SoundEvent hitSound = tool == Tool.WOODEN_SPEAR ? SoundEvent.ITEM_SPEAR_WOOD_HIT : SoundEvent.ITEM_SPEAR_HIT;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					hitSound, Sound.Source.PLAYER,
					1.0F, 1.0F
			), attacker);
		}
	}

	private boolean applyStabAttack(Player attacker, Entity target, ItemStack weapon, float damage,
	                                boolean dealsDamage, boolean dealsKnockback, boolean dealsDismount) {
		boolean dealtDamage = false;
		if (dealsDamage) {
			dealtDamage = this.damageTarget(target, new Damage(
					DamageType.SPEAR,
					attacker, attacker,
					null, damage
			));
		}

		boolean affected = dealsKnockback || dealtDamage;

		if (dealsKnockback && target instanceof LivingEntity living) {
			this.knockbackFeature.applyAttackKnockback(attacker, living, STAB_KNOCKBACK_BONUS);
			this.knockbackFeature.applyAttackKnockback(attacker, living,
					this.enchantmentFeature.getKnockback(attacker, weapon));
		}

		if (dealsDismount && target.getVehicle() != null) {
			affected = true;
			target.getVehicle().removePassenger(target);
		}

		if (dealtDamage && target instanceof LivingEntity living) {
			this.enchantmentFeature.onUserDamaged(living, attacker);
			this.enchantmentFeature.onTargetDamaged(attacker, living, weapon);
		}

		return affected;
	}

	private boolean damageTarget(Entity target, Damage damage) {
		if (target instanceof LivingEntity living) return living.damage(damage);
		if (target instanceof CrystalEntity crystal) return crystal.damage(damage);

		return false;
	}

	private boolean testCondition(int ticksUsed, double attackerSpeed, double relativeSpeed,
	                              int maxDurationTicks, float minSpeed, float minRelativeSpeed) {
		return ticksUsed <= maxDurationTicks
				&& attackerSpeed >= (double) minSpeed
				&& relativeSpeed >= (double) minRelativeSpeed;
	}

	private Vec getKnownMotion(Entity entity) {
		if (entity instanceof Player) return this.playerStateFeature.getKnownMovement(entity);

		var vehicle = entity.getVehicle();
		return vehicle != null ? vehicle.getVelocity() : entity.getVelocity();
	}

	private List<Entity> findEntitiesAlongRay(Player attacker, ItemStack weapon) {
		var instance = attacker.getInstance();
		if (instance == null) return List.of();

		AttackRange attackRange = weapon.get(DataComponents.ATTACK_RANGE, DEFAULT_ATTACK_RANGE);
		double margin = attackRange.hitboxMargin();

		Vec eye = attacker.getPosition().add(0.0, attacker.getEyeHeight(), 0.0).asVec();
		Vec look = attacker.getPosition().direction();
		Vec from = eye.add(look.mul(attackRange.effectiveMinReach(attacker)));
		double movementComponent = this.getKnownMotion(attacker).div(ServerFlag.SERVER_TICKS_PER_SECOND).dot(look);
		Vec to = eye.add(look.mul(attackRange.effectiveMaxReach(attacker) + Math.max(0.0, movementComponent)));

		var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);
		Pos blockHit = CollisionUtil.clipBlocks(blockGetter, eye, to);
		if (blockHit != null) {
			to = blockHit.asVec();
			if (eye.distanceSquared(to) < eye.distanceSquared(from)) return List.of();
		}

		Vec searchMin = from.min(to).sub(margin + 1.0);
		Vec searchMax = from.max(to).add(margin + 1.0);
		Vec searchCenter = searchMin.add(searchMax).mul(0.5);
		double searchRange = searchMax.sub(searchMin).length() / 2.0 + 4.0;

		List<StabHit> hits = new ArrayList<>();
		for (Entity candidate : instance.getNearbyEntities(searchCenter, searchRange)) {
			if (!this.canHitEntity(attacker, candidate)) continue;

			var box = candidate.getBoundingBox();
			var position = candidate.getPosition();
			Vec min = new Vec(position.x() + box.minX(), position.y() + box.minY(), position.z() + box.minZ());
			Vec max = new Vec(position.x() + box.maxX(), position.y() + box.maxY(), position.z() + box.maxZ());
			if (!intersects(min, max, searchMin, searchMax)) continue;

			Vec hit;
			if (contains(min, max, from)) {
				hit = from;
			} else {
				hit = clipBox(min, max, from, to);
				if (hit == null && margin > 0.0) {
					Vec outsideHit = clipBox(min.sub(margin), max.add(margin), from, to);
					if (outsideHit != null) {
						Vec towards = min.add(max).mul(0.5);
						Pos towardsBlockHit = CollisionUtil.clipBlocks(blockGetter, outsideHit, towards);
						if (towardsBlockHit != null) towards = towardsBlockHit.asVec();
						hit = clipBox(min, max, outsideHit, towards);
					}
				}
			}

			if (hit != null) hits.add(new StabHit(candidate, from.distanceSquared(hit)));
		}

		hits.sort(Comparator.comparingDouble(StabHit::distanceSquared));

		return hits.stream().map(StabHit::entity).toList();
	}

	private record StabHit(Entity entity, double distanceSquared) {}

	private boolean canHitEntity(Player attacker, Entity target) {
		if (target == attacker) return false;
		if (target instanceof LivingEntity living && living.isDead()) return false;
		if (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;
		if (!isPickable(target)) return false;

		return rootVehicle(attacker) != rootVehicle(target);
	}

	private static boolean isPickable(Entity entity) {
		if (entity instanceof LivingEntity) return true;
		if (entity instanceof CrystalEntity) return true;

		var type = entity.getEntityType();
		if (type == EntityType.ARMOR_STAND) {
			return !(entity.getEntityMeta() instanceof ArmorStandMeta armorStandMeta) || !armorStandMeta.isMarker();
		}
		if (type == EntityType.END_CRYSTAL || type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME
				|| type == EntityType.PAINTING || type == EntityType.INTERACTION) {
			return true;
		}

		var name = type.key().value();
		return name.endsWith("_boat") || name.endsWith("_raft") || name.contains("minecart");
	}

	private static Entity rootVehicle(Entity entity) {
		var root = entity;
		while (root.getVehicle() != null) root = root.getVehicle();
		return root;
	}

	private static boolean intersects(Vec min, Vec max, Vec otherMin, Vec otherMax) {
		return min.x() <= otherMax.x() && max.x() >= otherMin.x()
				&& min.y() <= otherMax.y() && max.y() >= otherMin.y()
				&& min.z() <= otherMax.z() && max.z() >= otherMin.z();
	}

	private static boolean contains(Vec min, Vec max, Vec point) {
		return point.x() > min.x() && point.x() < max.x()
				&& point.y() > min.y() && point.y() < max.y()
				&& point.z() > min.z() && point.z() < max.z();
	}

	private static @Nullable Vec clipBox(Vec min, Vec max, Vec from, Vec to) {
		if (contains(min, max, from)) return null;

		Vec delta = to.sub(from);
		double entry = 0.0;
		double exit = 1.0;
		for (var axis = 0; axis < 3; axis++) {
			double start = axisValue(from, axis);
			double direction = axisValue(delta, axis);
			double lower = axisValue(min, axis);
			double upper = axisValue(max, axis);

			if (Math.abs(direction) < 1.0E-7) {
				if (start < lower || start > upper) return null;
				continue;
			}

			double first = (lower - start) / direction;
			double second = (upper - start) / direction;
			entry = Math.max(entry, Math.min(first, second));
			exit = Math.min(exit, Math.max(first, second));
			if (entry > exit) return null;
		}

		return from.add(delta.mul(entry));
	}

	private static double axisValue(Vec vec, int axis) {
		return switch (axis) {
			case 0 -> vec.x();
			case 1 -> vec.y();
			default -> vec.z();
		};
	}

	private void playPiercingSounds(Player attacker, Tool tool, boolean hit) {
		boolean isWood = tool == Tool.WOODEN_SPEAR;
		SoundEvent attackSound = isWood ? SoundEvent.ITEM_SPEAR_WOOD_ATTACK : SoundEvent.ITEM_SPEAR_ATTACK;

		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
				attackSound, Sound.Source.PLAYER,
				1.0F, 1.0F
		), attacker);

		if (hit) {
			SoundEvent hitSound = isWood ? SoundEvent.ITEM_SPEAR_WOOD_HIT : SoundEvent.ITEM_SPEAR_HIT;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					hitSound, Sound.Source.PLAYER,
					1.0F, 1.0F
			), attacker);
		}
	}

	public static boolean isSpear(Material material) {
		Tool tool = Tool.fromMaterial(material);
		return tool != null && tool.isSpear();
	}
}
