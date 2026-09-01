package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.utils.EntityUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.entity.metadata.projectile.ThrownTridentMeta;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class ThrownTrident extends AbstractArrow {
	private final ItemStack tridentItem;
	private boolean damageDone;
	private boolean hasStartedReturning;

	public ThrownTrident(@Nullable Entity shooter, ItemStack tridentItem, EnchantmentFeature enchantmentFeature) {
		super(shooter, EntityType.TRIDENT, enchantmentFeature);
		this.tridentItem = tridentItem;

		ThrownTridentMeta meta = ((ThrownTridentMeta) this.getEntityMeta());
		var loyalty = (int) this.enchantmentFeature.modifyConditionalValue(
				tridentItem, EffectComponent.TRIDENT_RETURN_ACCELERATION, 0.0F
		);
		meta.setLoyaltyLevel((byte) Math.min(Math.max(loyalty, 0), 127));

		meta.setHasEnchantmentGlint(!Objects.requireNonNull(tridentItem.get(DataComponents.ENCHANTMENTS))
				.enchantments().isEmpty());
	}

	@Override
	public void update(long time) {
		if (this.stuckTime > 4) this.damageDone = true;

		Entity shooter = this.getShooter();
		int loyalty = ((ThrownTridentMeta) this.getEntityMeta()).getLoyaltyLevel();
		if (loyalty > 0 && (this.damageDone || this.isNoClip()) && shooter != null) {
			if (shooter.isRemoved() || (shooter instanceof LivingEntity living && living.isDead())
					|| (shooter instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)) {
				if (this.pickupMode == PickupMode.ALLOWED)
					EntityUtil.spawnItemAtLocation(this, this.tridentItem, 0.1);
                this.remove();
			} else {
				// Move towards owner
				var ownerEyePosition = shooter.getPosition().add(0, shooter.getEyeHeight(), 0);
				if (!(shooter instanceof Player) && this.getPosition().distance(ownerEyePosition) < shooter.getBoundingBox().width() + 1.0) {
					this.remove();
					return;
				}

                this.setNoClip(true);
                this.setNoGravity(true);
				this.collisionDirection = null;
				((AbstractArrowMeta) this.getEntityMeta()).setInGround(false);
				var vector = ownerEyePosition.asVec().sub(this.position);
                this.refreshPosition(this.position.add(0, vector.y() * 0.015 * loyalty, 0));
                this.setVelocity(this.velocity.mul(0.95).add(vector.normalize().mul(0.05 * loyalty)
						.mul(ServerFlag.SERVER_TICKS_PER_SECOND)));

				if (!this.hasStartedReturning) {
                    this.getViewersAsAudience().playSound(Sound.sound(
							SoundEvent.ITEM_TRIDENT_RETURN, Sound.Source.NEUTRAL,
							10.0f, 1.0f
					), this.position.x(), this.position.y(), this.position.z());
                    this.hasStartedReturning = true;
				}
			}
		}

		super.update(time);
	}

	@Override
	protected boolean canHit(Entity entity) {
		return !this.damageDone && (super.canHit(entity) || entity instanceof CrystalEntity);
	}

	private void deflectBack() {
		this.setVelocity(this.velocity.mul(-0.01, -0.1, -0.01));
		var position = this.getPosition();
		this.refreshPosition(position.withYaw(position.yaw() + 170.0f + 20.0f * ThreadLocalRandom.current().nextFloat()));
	}

	@Override
	public boolean onHit(@NotNull Entity entity) {
		if (this.damageDone) return false;
		Entity shooter = this.getShooter();

		if (entity instanceof CrystalEntity crystal) {
			var damageObj = new Damage(DamageType.TRIDENT, this, shooter == null ? this : shooter, null, 8.0F);
			crystal.damage(damageObj);
			this.damageDone = true;

			this.deflectBack();
			this.getViewersAsAudience().playSound(Sound.sound(
					SoundEvent.ITEM_TRIDENT_HIT, Sound.Source.NEUTRAL,
					1.0f, 1.0f
			), this.position.x(), this.position.y(), this.position.z());

			return false;
		}

		if (!(entity instanceof LivingEntity living)) return false;

		var damage = 8.0F + this.enchantmentFeature.getAttackDamage(this.tridentItem, EntityGroup.ofEntity(living));
		var damageObj = new Damage(DamageType.TRIDENT, this, shooter == null ? this : shooter, null, damage);
		var damaged = living.damage(damageObj);

		if (damaged && shooter instanceof LivingEntity livingShooter) {
            this.enchantmentFeature.onUserDamaged(living, livingShooter);
            this.enchantmentFeature.onTargetDamaged(livingShooter, living, this.tridentItem);
		}

		if (damaged && this.canChannel(living)) {
			this.channelLightning(living.getPosition());
		}

        this.damageDone = true;

        this.deflectBack();
        this.getViewersAsAudience().playSound(Sound.sound(
				SoundEvent.ITEM_TRIDENT_HIT, Sound.Source.NEUTRAL,
				1.0f, 1.0f
		), this.position.x(), this.position.y(), this.position.z());

		return false;
	}

	@Override
	public boolean onStuck(ProjectileCollideWithBlockEvent event) {
		if (this.isLightningRod(event.getBlock()) && this.canChannel(event.getCollisionPosition())) {
			this.channelLightning(event.getCollisionPosition());
		}

		return super.onStuck(event);
	}

	@Override
	public boolean canBePickedUp(@Nullable Player player) {
		if (player == null) return true;
		if (this.getShooter() == player || this.getShooter() == null) {
			return super.canBePickedUp(player);
		} else return false;
	}

	@Override
	public boolean pickup(Player player) {
		return super.pickup(player)
				|| (this.isNoClip() && this.getShooter() == player && player.getInventory().addItemStack(this.tridentItem));
	}

	@Override
	protected void tickRemoval() {
		int loyalty = ((ThrownTridentMeta) this.getEntityMeta()).getLoyaltyLevel();
		if (this.pickupMode != PickupMode.ALLOWED || loyalty <= 0)
			super.tickRemoval();
	}

	@Override
	protected SoundEvent getDefaultSound() {
		return SoundEvent.ITEM_TRIDENT_HIT_GROUND;
	}

	@Override
	protected double getWaterInertia() {
		return 0.99;
	}

	@Override
	protected ItemStack getPickupItem() {
		return this.tridentItem;
	}

	private boolean canChannel(Point point) {
		if (!this.hasChanneling()) return false;

		var instance = this.getInstance();

		if (instance == null) return false;
		if (instance.getWeather().thunderLevel() <= 0.0F) return false;
		if (!FluidUtil.hasOpenSky(instance)) return false;

		return FluidUtil.isRainingAt(instance, point.blockX(), point.blockY(), point.blockZ());
	}

	private boolean canChannel(Entity entity) {
		if (!this.hasChanneling()) return false;

		var instance = this.getInstance();

		if (instance == null) return false;
		if (instance.getWeather().thunderLevel() <= 0.0F) return false;
		if (!FluidUtil.hasOpenSky(instance)) return false;

		return FluidUtil.isInRain(entity);
	}

	private boolean hasChanneling() {
		return this.tridentItem.get(DataComponents.ENCHANTMENTS).level(Enchantment.CHANNELING) > 0;
	}

	private void channelLightning(Point point) {
		var instance = Objects.requireNonNull(this.getInstance());
		var lightning = new Entity(EntityType.LIGHTNING_BOLT);

		lightning.setInstance(instance, point);
		lightning.scheduleRemove(20, TimeUnit.SERVER_TICK);
		ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
				SoundEvent.ITEM_TRIDENT_THUNDER, Sound.Source.WEATHER,
				5.0F, 1.0F
		), point.x(), point.y(), point.z());

		this.damageLightningEntities(instance, point);
	}

	private void damageLightningEntities(Instance instance, Point point) {
		for (var entity : instance.getNearbyEntities(point, 10.0)) {
			if (entity instanceof Player) continue;

			this.damageLightningEntity(point, entity);
		}
		for (var player : instance.getPlayers()) {
			this.damageLightningEntity(point, player);
		}
	}

	private void damageLightningEntity(Point point, Entity entity) {
		if (!(entity instanceof LivingEntity livingEntity)) {
			return;
		}

		var position = entity.getPosition();

		if (Math.abs(position.x() - point.x()) > 3.0
				|| position.y() - point.y() < -3.0
				|| position.y() - point.y() > 9.0
				|| Math.abs(position.z() - point.z()) > 3.0) {
			return;
		}

		this.setFireTicks(livingEntity, Math.max(livingEntity.getFireTicks(), 8 * ServerFlag.SERVER_TICKS_PER_SECOND));
		livingEntity.damage(DamageType.LIGHTNING_BOLT, 5.0F);
	}

	private boolean isLightningRod(Block block) {
		return block.key().value().endsWith("lightning_rod");
	}
}
