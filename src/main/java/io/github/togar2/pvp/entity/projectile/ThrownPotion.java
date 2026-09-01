package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.entity.AreaEffectCloud;
import io.github.togar2.pvp.feature.effect.EffectFeature;
import io.github.togar2.pvp.utils.EffectUtil;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.instance.block.Block;
import net.minestom.server.entity.metadata.item.LingeringPotionMeta;
import net.minestom.server.entity.metadata.item.SplashPotionMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionType;
import net.minestom.server.worldevent.WorldEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Collectors;

public class ThrownPotion extends CustomEntityProjectile implements ItemHoldingProjectile {
	private final EffectFeature effectFeature;
	private final boolean lingering;

	public ThrownPotion(@Nullable Entity shooter, EffectFeature effectFeature, boolean lingering) {
		super(shooter, lingering ? EntityType.LINGERING_POTION : EntityType.SPLASH_POTION);
		this.effectFeature = effectFeature;
		this.lingering = lingering;

		// Why does Minestom have the wrong value 0.03 in its registries?
        this.setAerodynamics(this.getAerodynamics().withGravity(0.05));
	}

	@Override
	public boolean onHit(Entity entity) {
        this.splash(entity);
		return true;
	}

	@Override
	public boolean onStuck() {
        this.splash(null);
		return true;
	}

	@Override
	protected boolean shouldUpdateVelocityBeforeMovement() {
		return true;
	}

	@Override
	protected double getWaterInertia() {
		return 0.8;
	}

	public void splash(@Nullable Entity entity) {
		var item = this.getItem();

		var potionContents = item.get(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		var potions = this.effectFeature.getAllPotions(potionContents);
		boolean waterPotion = potionContents.potion() == PotionType.WATER && potionContents.customEffects().isEmpty();

		if (waterPotion) {
			this.applyWaterSplash(entity);
			this.dowseFireBlocks();
		} else if (!potions.isEmpty()) {
			if (this.lingering) {
				var areaEffectCloud = new AreaEffectCloud(potionContents, this.getShooter(), this.effectFeature);
				areaEffectCloud.setInstance(Objects.requireNonNull(this.getInstance()), this.getPosition());
			} else {
				this.applySplash(potionContents, entity);
			}
		}

		Pos position = this.getPosition();

		boolean instantEffect = false;
		for (Potion potion : potions) {
			if (potion.effect().instantaneous()) {
				instantEffect = true;
				break;
			}
		}

		WorldEvent effect = instantEffect ? WorldEvent.PARTICLES_INSTANT_POTION_SPLASH : WorldEvent.PARTICLES_SPELL_POTION_SPLASH;
		EffectUtil.sendNearby(
				Objects.requireNonNull(this.getInstance()), effect, position.blockX(),
				position.blockY(), position.blockZ(), this.effectFeature.getPotionColor(potionContents),
				64.0, false
		);
	}

	private void applyWaterSplash(@Nullable Entity hitEntity) {
		var boundingBox = this.getBoundingBox().expand(8.0, 4.0, 8.0);
		var entities = Objects.requireNonNull(this.getInstance()).getEntities().stream()
				.filter(entity -> !(entity instanceof Player))
				.filter(entity -> boundingBox.intersectEntity(this.getPosition().add(0, -2, 0), entity))
				.filter(entity -> entity instanceof LivingEntity)
				.map(entity -> (LivingEntity) entity)
				.collect(Collectors.toList());
		entities.addAll(Objects.requireNonNull(this.getInstance()).getPlayers().stream()
				.filter(player -> boundingBox.intersectEntity(this.getPosition().add(0, -2, 0), player))
				.filter(player -> player.getGameMode() != GameMode.SPECTATOR)
				.toList());

		if (hitEntity instanceof LivingEntity livingEntity && !entities.contains(livingEntity)) {
			entities.add(livingEntity);
		}

		for (var entity : entities) {
			if (!entity.isOnFire() || entity.isDead()) continue;
			if (this.getDistanceSquared(entity) >= 16.0) continue;

			entity.setFireTicks(0);
		}
	}

	private void applySplash(PotionContents potionContents, @Nullable Entity hitEntity) {
		var boundingBox = this.getBoundingBox().expand(8.0, 4.0, 8.0);
		var entities = Objects.requireNonNull(this.getInstance()).getEntities().stream()
				.filter(entity -> !(entity instanceof Player))
				.filter(entity -> boundingBox.intersectEntity(this.getPosition().add(0, -2, 0), entity))
				.filter(entity -> entity instanceof LivingEntity)
				.map(entity -> (LivingEntity) entity)
				.collect(Collectors.toList());
		entities.addAll(Objects.requireNonNull(this.getInstance()).getPlayers().stream()
				.filter(player -> boundingBox.intersectEntity(this.getPosition().add(0, -2, 0), player))
				.filter(player -> player.getGameMode() != GameMode.SPECTATOR)
				.toList());

		if (hitEntity instanceof LivingEntity && !entities.contains(hitEntity)) {
			entities.add((LivingEntity) hitEntity);
		}

		if (entities.isEmpty()) return;

		for (var entity : entities) {
			if (entity.getEntityType() == EntityType.ARMOR_STAND) continue;

			double distanceSquared = this.getDistanceSquared(entity);
			if (distanceSquared >= 16.0) continue;

			double proximity = entity == hitEntity ? 1.0 : (1.0 - Math.sqrt(distanceSquared) / 4.0);
			this.effectFeature.addSplashPotionEffects(entity, potionContents, proximity, this, this.getShooter());
		}
	}

	private void dowseFireBlocks() {
		var instance = this.getInstance();

		if (instance == null) return;

		var impactPosition = this.getPosition();

		this.dowseFire(impactPosition);
		this.dowseFire(impactPosition.add(1, 0, 0));
		this.dowseFire(impactPosition.add(-1, 0, 0));
		this.dowseFire(impactPosition.add(0, 0, 1));
		this.dowseFire(impactPosition.add(0, 0, -1));
		this.dowseFire(impactPosition.add(0, 1, 0));
	}

	private void dowseFire(Pos position) {
		var instance = this.getInstance();

		if (instance == null) return;

		var block = instance.getBlock(position);

		if (block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE)) {
			instance.setBlock(position, Block.AIR);
			return;
		}

		var litProperty = block.getProperty("lit");

		if (!"true".equals(litProperty)) return;

		if (block.compare(Block.CAMPFIRE) || block.compare(Block.SOUL_CAMPFIRE)
				|| block.key().value().contains("candle")) {
			instance.setBlock(position, block.withProperty("lit", "false"));
		}
	}

	@NotNull
	public ItemStack getItem() {
		if (this.lingering) {
			return ((LingeringPotionMeta) this.getEntityMeta()).getItem();
		}
		return ((SplashPotionMeta) this.getEntityMeta()).getItem();
	}

	@Override
	public void setItem(@NotNull ItemStack item) {
		if (this.lingering) {
			((LingeringPotionMeta) this.getEntityMeta()).setItem(item);
		} else {
			((SplashPotionMeta) this.getEntityMeta()).setItem(item);
		}
	}
}
