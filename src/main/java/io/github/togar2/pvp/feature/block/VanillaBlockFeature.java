package io.github.togar2.pvp.feature.block;

import java.util.concurrent.ThreadLocalRandom;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.events.DamageBlockEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.ItemCooldownFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.item.component.BlocksAttacks;
import net.minestom.server.sound.SoundEvent;
import org.jspecify.annotations.Nullable;

/**
 * Vanilla implementation of {@link BlockFeature}
 */
public class VanillaBlockFeature implements BlockFeature {
	private static final float BLOCK_KNOCKBACK = 0.5F;

	public static final DefinedFeature<VanillaBlockFeature> DEFINED = new DefinedFeature<>(
			FeatureType.BLOCK, VanillaBlockFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.ITEM_COOLDOWN, FeatureType.VERSION
	);

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private ItemCooldownFeature itemCooldownFeature;
	private CombatVersion version;

	public VanillaBlockFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.itemCooldownFeature = this.configuration.get(FeatureType.ITEM_COOLDOWN);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	protected boolean isPiercing(Damage damage) {
		Entity source = damage.getSource();
		if (source != null && source.getEntityMeta() instanceof AbstractArrowMeta) {
			return ((AbstractArrowMeta) source.getEntityMeta()).getPiercingLevel() > 0;
		}

		return false;
	}

	@Override
	public boolean isDamageBlocked(LivingEntity entity, Damage damage) {
		if (damage.getAmount() <= 0) return false;

		if (this.isPiercing(damage)) return false;
		if (!(entity.getEntityMeta() instanceof LivingEntityMeta meta) || !meta.isHandActive()) return false;

		var blockingStack = entity.getItemInHand(meta.getActiveHand());
		var blocksAttacks = blockingStack.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks == null) return false;

		if (this.isBypassedBy(blocksAttacks, damage)) return false;

		if (entity instanceof Player blockingPlayer) {
			var blockDelayTicks = Math.round(blocksAttacks.blockDelaySeconds() * ServerFlag.SERVER_TICKS_PER_SECOND);
			if (blockingPlayer.getCurrentItemUseTime() < blockDelayTicks) return false;
		}

		if (this.version.legacy()) return true;

		return this.resolveBlockedDamage(blocksAttacks, damage, damage.getAmount(), this.getBlockingAngle(entity, damage)) > 0.0F;
	}

	private boolean isBypassedBy(BlocksAttacks blocksAttacks, Damage damage) {
		var bypassedBy = blocksAttacks.bypassedBy();

		return bypassedBy != null && bypassedBy.contains(damage.getType());
	}

	private float resolveBlockedDamage(BlocksAttacks blocksAttacks, Damage damage, float amount, double blockingAngle) {
		var damageTypeKey = damage.getType();
		float blocked = 0.0F;

		for (var reduction : blocksAttacks.damageReductions()) {
			if (blockingAngle > Math.toRadians(reduction.horizontalBlockingAngle())) continue;

			var typeFilter = reduction.type();

			if (typeFilter != null && !typeFilter.contains(damageTypeKey)) continue;

			blocked += Math.clamp(reduction.base() + reduction.factor() * amount, 0.0F, amount);
		}

		return Math.clamp(blocked, 0.0F, amount);
	}

	private int computeShieldItemDamage(@Nullable BlocksAttacks blocksAttacks, float blockedDamage) {
		if (blockedDamage <= 0) return 0;

		var damageFunction = blocksAttacks == null ? BlocksAttacks.ItemDamageFunction.DEFAULT : blocksAttacks.itemDamage();
		if (blockedDamage < damageFunction.threshold()) return 0;

		return (int) Math.floor(damageFunction.base() + damageFunction.factor() * blockedDamage);
	}

	@Override
	public boolean applyBlock(LivingEntity entity, Damage damage) {
		float amount = damage.getAmount();
		PlayerHand hand = ((LivingEntityMeta) entity.getEntityMeta()).getActiveHand();
		var blockingStack = entity.getItemInHand(hand);
		var blocksAttacks = blockingStack.get(DataComponents.BLOCKS_ATTACKS);

		float resultingDamage;
		float blockedDamage;
		if (this.version.legacy()) {
			resultingDamage = Math.max(0, (amount + 1) * 0.5f);
			blockedDamage = amount - resultingDamage;
		} else if (blocksAttacks != null) {
			var blockingAngle = this.getBlockingAngle(entity, damage);
			blockedDamage = this.resolveBlockedDamage(blocksAttacks, damage, amount, blockingAngle);
			resultingDamage = amount - blockedDamage;
		} else {
			resultingDamage = 0;
			blockedDamage = amount;
		}

		DamageBlockEvent damageBlockEvent = new DamageBlockEvent(entity, amount, resultingDamage, false);
		EventDispatcher.call(damageBlockEvent);
		if (damageBlockEvent.isCancelled()) return false;
		damage.setAmount(damageBlockEvent.getResultingDamage());

		int shieldDamage = this.computeShieldItemDamage(blocksAttacks, blockedDamage);
		if (shieldDamage > 0) {
            this.itemDamageFeature.damageEquipment(
					entity,
					hand == PlayerHand.MAIN ?
							EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND,
					shieldDamage
			);

			if (entity.getItemInHand(hand).isAir()) {
				((LivingEntityMeta) entity.getEntityMeta()).setHandActive(false);
				entity.getViewersAsAudience().playSound(Sound.sound(
						SoundEvent.ITEM_SHIELD_BREAK, Sound.Source.PLAYER,
						0.8f, 0.8f + ThreadLocalRandom.current().nextFloat(0.4f)
				));
			}
		}

		if (blocksAttacks != null && blocksAttacks.blockSound() != null && blockedDamage > 0) {
			entity.getViewersAsAudience().playSound(Sound.sound(
					blocksAttacks.blockSound(),
					entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F
			), entity);
		}

		// Take shield hit (knockback and disabling)
		DamageTypeInfo info = DamageTypeInfo.of(damage.getType());
		if (!info.projectile() && damage.getAttacker() instanceof LivingEntity attacker)
            this.takeShieldHit(entity, attacker, blocksAttacks, damageBlockEvent.knockbackAttacker());

		return resultingDamage == 0;
	}

	private double getBlockingAngle(LivingEntity entity, Damage damage) {
		if (damage.getSource() == null) return Math.PI;

		var attackerPos = damage.getSource().getPosition();
		var entityPos = entity.getPosition();
		var yaw = Math.toRadians(entityPos.yaw());
		var entityRotation = new Vec(-Math.sin(yaw), 0.0, Math.cos(yaw));
		var attackerDirection = attackerPos.asVec().sub(entityPos.asVec()).withY(0).normalize();
		var dotProduct = Math.clamp(attackerDirection.dot(entityRotation), -1.0, 1.0);

		return Math.acos(dotProduct);
	}

	protected void takeShieldHit(LivingEntity entity, LivingEntity attacker, @Nullable BlocksAttacks blocksAttacks, boolean applyKnockback) {
		Pos entityPos = entity.getPosition();
		Pos attackerPos = attacker.getPosition();

		float blockerStrength = (float) (BLOCK_KNOCKBACK * (1.0 - entity.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE)));
		entity.takeKnockback(blockerStrength,
				entityPos.x() - attackerPos.x(),
				entityPos.z() - attackerPos.z()
		);

		if (applyKnockback) {
			attacker.takeKnockback(BLOCK_KNOCKBACK,
					entityPos.x() - attackerPos.x(),
					entityPos.z() - attackerPos.z()
			);
		}

		if (!(entity instanceof Player player)) {
			return;
		}

		var weapon = attacker.getItemInMainHand().get(DataComponents.WEAPON);
		if (weapon != null && weapon.disableBlockingForSeconds() > 0.0F) {
			var disableScale = blocksAttacks == null ? 1.0F : blocksAttacks.disableCooldownScale();
			var cooldownTicks = Math.round(weapon.disableBlockingForSeconds() * disableScale * ServerFlag.SERVER_TICKS_PER_SECOND);

			if (cooldownTicks > 0) {
				this.disableShield(player, blocksAttacks, cooldownTicks);
			}
		}
	}

	protected void disableShield(Player player, @Nullable BlocksAttacks blocksAttacks, int cooldownTicks) {
		var hand = player.getPlayerMeta().getActiveHand();
		this.itemCooldownFeature.setCooldown(player, player.getItemInHand(hand), cooldownTicks);

		if (blocksAttacks != null && blocksAttacks.disableSound() != null) {
			player.getViewersAsAudience().playSound(Sound.sound(
					blocksAttacks.disableSound(), Sound.Source.PLAYER,
					0.8F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F
			), player);
		}

		// Shield disable status
		player.triggerStatus((byte) 30);
		player.triggerStatus((byte) 9);

		player.refreshActiveHand(false, hand == PlayerHand.OFF, false);
	}
}
