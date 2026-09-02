package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.item.ThrownEnderPearlMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class ThrownEnderpearl extends CustomEntityProjectile implements ItemHoldingProjectile {
	private Pos prevPos = Pos.ZERO;

	private final FallFeature fallFeature;

	public ThrownEnderpearl(@Nullable Entity shooter, FallFeature fallFeature) {
		super(shooter, EntityType.ENDER_PEARL);
		this.fallFeature = fallFeature;
	}

	private void teleportOwner() {
		Pos position = this.prevPos;
		ThreadLocalRandom random = ThreadLocalRandom.current();

		for (int i = 0; i < 32; i++) {
            this.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.PORTAL, false, false,
					position.x(), position.y() + random.nextDouble() * 2, position.z(),
					(float) random.nextGaussian(), 0.0F, (float) random.nextGaussian(),
					0, 1
			));
		}

		if (this.isRemoved()) return;

		Entity shooter = this.getShooter();
		if (shooter != null) {
			Pos shooterPos = shooter.getPosition();
			position = position.withPitch(shooterPos.pitch()).withYaw(shooterPos.yaw());
		}

		if (shooter instanceof Player player) {
			if (player.isOnline() && !player.isDead() && player.getInstance() == this.getInstance()
					&& player.getPlayerMeta().getBedInWhichSleepingPosition() == null) {
				if (player.getVehicle() != null) {
					player.getVehicle().removePassenger(player);
				}

				player.teleport(position.withView(0.0F, 0.0F), null, RelativeFlags.VIEW);
                this.fallFeature.resetFallDistance(player);
                this.fallFeature.clearCurrentImpulseContext(player);

				player.damage(DamageType.ENDER_PEARL, 5.0F);
				this.playTeleportSound(position);
			}
		} else if (shooter != null) {
			shooter.teleport(position);

			if (shooter instanceof LivingEntity livingShooter) {
				this.fallFeature.resetFallDistance(livingShooter);
				this.fallFeature.clearCurrentImpulseContext(livingShooter);
			}

			this.playTeleportSound(position);
		}
	}

	private void playTeleportSound(Pos position) {
		ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
				SoundEvent.ENTITY_PLAYER_TELEPORT, Sound.Source.PLAYER,
				1.0F, 1.0F
		), position.x(), position.y(), position.z());
	}

	@Override
	public boolean onHit(Entity entity) {
		((LivingEntity) entity).damage(new Damage(DamageType.THROWN, this, this.getShooter(), null, 0));

        this.teleportOwner();
		return true;
	}

	@Override
	public boolean onStuck() {
        this.teleportOwner();
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

	@Override
	public void tick(long time) {
		Entity shooter = this.getShooter();
		if (shooter instanceof Player && ((Player) shooter).isDead()) {
            this.remove();
		} else {
            this.prevPos = this.getPosition();
			super.tick(time);
		}
	}

	@Override
	public void setItem(@NotNull ItemStack item) {
		((ThrownEnderPearlMeta) this.getEntityMeta()).setItem(item);
	}

	@Override
	protected int getUpdateInterval() {
		return 10;
	}
}
