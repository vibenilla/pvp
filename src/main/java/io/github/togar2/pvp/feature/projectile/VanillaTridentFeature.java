package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.entity.projectile.ThrownTrident;
import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.AttackCooldownFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vanilla implementation of {@link TridentFeature}
 */
public class VanillaTridentFeature implements TridentFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaTridentFeature> DEFINED = new DefinedFeature<>(
			FeatureType.TRIDENT, VanillaTridentFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.ENCHANTMENT, FeatureType.ATTACK_COOLDOWN, FeatureType.EXHAUSTION
	);

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private EnchantmentFeature enchantmentFeature;
	private AttackCooldownFeature attackCooldownFeature;
	private ExhaustionFeature exhaustionFeature;

	public static final Tag<Long> RIPTIDE_START = Tag.Long("riptideStart");
	public static final Tag<Boolean> RIPTIDE_OFF_HAND = Tag.Boolean("riptideOffHand");
	public static final Tag<ItemStack> RIPTIDE_ITEM = Tag.Transient("riptideItem");
	private static final float RIPTIDE_SPIN_ATTACK_DAMAGE = 8.0F;

	public VanillaTridentFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.attackCooldownFeature = this.configuration.get(FeatureType.ATTACK_COOLDOWN);
		this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerUseItemEvent.class, event -> {
			var player = event.getPlayer();
			var stack = event.getItemStack();
			if (stack.material() != Material.TRIDENT) return;

			var riptideStrength = this.getRiptideStrength(stack);

			if (this.nextDamageWillBreak(stack)
					|| (riptideStrength > 0.0F && !this.isInWaterOrRain(player))) {
				event.setCancelled(true);
			}
		});

		node.addListener(PlayerCancelItemUseEvent.class, event -> {
			Player player = event.getPlayer();
			ItemStack stack = event.getItemStack();
			if (stack.material() != Material.TRIDENT) return;

			long ticks = player.getCurrentItemUseTime();
			if (ticks < 10) return;
			if (this.nextDamageWillBreak(stack)) return;

			var riptideStrength = this.getRiptideStrength(stack);
			var soundEvent = this.getTridentSound(stack);
			if (riptideStrength > 0.0F && !this.isInWaterOrRain(player)) return;
			if (riptideStrength > 0.0F && player.getVehicle() != null) return;

			this.itemDamageFeature.damageEquipment(player, event.getHand() == PlayerHand.MAIN ?
					EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, 1);

			if (riptideStrength > 0.0F) {
				player.setTag(RIPTIDE_OFF_HAND, event.getHand() == PlayerHand.OFF);
				player.setTag(RIPTIDE_ITEM, stack);
				this.applyRiptide(player, riptideStrength, soundEvent);
				event.setRiptideSpinAttack(true);
			} else {
				ItemStack thrownStack = player.getItemInHand(event.getHand());
				ThrownTrident trident = new ThrownTrident(player, thrownStack, this.enchantmentFeature);

				Pos position = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
				trident.shootFromRotation(position.pitch(), position.yaw(), 0, 2.5, 1.0);
				trident.setInstance(Objects.requireNonNull(player.getInstance()), position.withView(trident.getPosition()));

				Vec playerVel = player.getVelocity();
				trident.setVelocity(trident.getVelocity().add(playerVel.x(),
						player.isOnGround() ? 0.0 : playerVel.y(), playerVel.z()));

				ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
						soundEvent, Sound.Source.PLAYER,
						1.0f, 1.0f
				), trident);
				if (player.getGameMode() != GameMode.CREATIVE) player.setItemInHand(event.getHand(), thrownStack.consume(1));
			}
		});

		node.addListener(PlayerTickEvent.class, event -> {
			if (event.getPlayer().getPlayerMeta().isInRiptideSpinAttack()) {
				Player player = event.getPlayer();
				long ticks = player.getAliveTicks() - player.getTag(RIPTIDE_START);
				AtomicBoolean stopRiptide = new AtomicBoolean(ticks >= 20);

				assert player.getInstance() != null;
				player.getInstance().getEntityTracker().nearbyEntities(player.getPosition(), 5,
						EntityTracker.Target.ENTITIES, entity -> {
							if (entity != player && !stopRiptide.get() && entity instanceof LivingEntity
									&& entity.getBoundingBox().intersectEntity(entity.getPosition(), player)) {
								stopRiptide.set(true);

								this.applyRiptideHit(player, (LivingEntity) entity);
								if (player instanceof CombatPlayer combatPlayer)
									combatPlayer.setVelocityNoUpdate(velocity -> velocity.mul(-0.2));
							}
						});

				if (player instanceof CombatPlayer combatPlayer && combatPlayer.hasHorizontalCollision()) {
					stopRiptide.set(true);
				}

				if (stopRiptide.get())
					event.getPlayer().refreshActiveHand(false, false, false);
			}
		});
	}

	private void applyRiptideHit(Player player, LivingEntity target) {
		var offHand = Boolean.TRUE.equals(player.getTag(RIPTIDE_OFF_HAND));
		var hand = offHand ? PlayerHand.OFF : PlayerHand.MAIN;
		var slot = offHand ? EquipmentSlot.OFF_HAND : EquipmentSlot.MAIN_HAND;
		var stack = player.getTag(RIPTIDE_ITEM);
		if (stack == null) {
			stack = player.getItemInHand(hand);
		}

		var cooldownProgress = this.attackCooldownFeature.getAttackCooldownProgress(player);
		this.attackCooldownFeature.resetCooldownProgress(player);

		var baseDamage = RIPTIDE_SPIN_ATTACK_DAMAGE * (0.2F + (float) (cooldownProgress * cooldownProgress) * 0.8F);
		var magicalDamage = this.enchantmentFeature.getAttackDamage(stack, EntityGroup.ofEntity(target)) * (float) cooldownProgress;
		var damage = baseDamage + magicalDamage;
		var damaged = target.damage(new Damage(DamageType.PLAYER_ATTACK, player, player, null, damage));
		if (!damaged) return;

		this.enchantmentFeature.onUserDamaged(target, player);
		this.enchantmentFeature.onTargetDamaged(player, target, stack);
		var weapon = stack.get(DataComponents.WEAPON);
		if (weapon != null) {
			this.itemDamageFeature.damageEquipment(player, slot, weapon.itemDamagePerAttack());
		}

		this.exhaustionFeature.addAttackExhaustion(player);
	}

	@Override
	public void applyRiptide(Player player, int level) {
		var strength = (float) (3.0 * ((1.0 + level) / 4.0));
		var soundEvent = level >= 3 ? SoundEvent.ITEM_TRIDENT_RIPTIDE_3 :
				(level == 2 ? SoundEvent.ITEM_TRIDENT_RIPTIDE_2 : SoundEvent.ITEM_TRIDENT_RIPTIDE_1);
		this.applyRiptide(player, strength, soundEvent);
	}

	private void applyRiptide(Player player, float strength, SoundEvent soundEvent) {
		float yaw = player.getPosition().yaw();
		float pitch = player.getPosition().pitch();
		double h = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
		double k = -Math.sin(Math.toRadians(pitch));
		double l = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
		double length = Math.sqrt(h * h + k * k + l * l);

		player.setTag(RIPTIDE_START, player.getAliveTicks());
		player.setVelocity(player.getVelocity().add(new Vec(
				h * (strength / length),
				k * (strength / length),
				l * (strength / length)
		).mul(ServerFlag.SERVER_TICKS_PER_SECOND)));

		if (player.isOnGround()) {
			player.refreshPosition(player.getPosition().add(0.0, 1.1999999, 0.0));
		}

		ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
				soundEvent, Sound.Source.PLAYER,
				1.0f, 1.0f
		), player);

		player.refreshActiveHand(false, false, true);
	}

	private float getRiptideStrength(ItemStack stack) {
		return Math.max(0.0F, this.enchantmentFeature.modifyValue(
				stack, EffectComponent.TRIDENT_SPIN_ATTACK_STRENGTH, 0.0F
		));
	}

	private SoundEvent getTridentSound(ItemStack stack) {
		return this.enchantmentFeature.pickHighestLevel(
				stack, EffectComponent.TRIDENT_SOUND, SoundEvent.ITEM_TRIDENT_THROW
		);
	}

	private boolean isInWaterOrRain(Player player) {
		return FluidUtil.isTouchingWater(player) || FluidUtil.isInRain(player);
	}

	private boolean nextDamageWillBreak(ItemStack stack) {
		if (stack.has(DataComponents.UNBREAKABLE)) return false;

		var maxDamage = stack.get(DataComponents.MAX_DAMAGE, 0);

		if (maxDamage <= 0) return false;

		return stack.get(DataComponents.DAMAGE, 0) + 1 >= maxDamage;
	}
}
