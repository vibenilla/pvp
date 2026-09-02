package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.projectile.VanillaFishingRodFeature;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.other.FishingHookMeta;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

public class FishingBobber extends CustomEntityProjectile {
	private final boolean legacy;
	private int stuckTime;
	private Entity hooked;
	private State state = State.IN_AIR;
	private Pos prevPos = Pos.ZERO;

	private final double customGravity;

	public FishingBobber(@Nullable Entity shooter, boolean legacy) {
		super(shooter, EntityType.FISHING_BOBBER);
		this.legacy = legacy;
        this.setOwnerEntity(shooter);

		// Custom gravity logic: gravity is applied before movement
        this.customGravity = legacy ? 0.04 : 0.03;
        this.setAerodynamics(this.getAerodynamics().withGravity(0));

		// Minestom seems to like having wrong values in its registries
        this.setAerodynamics(this.getAerodynamics().withHorizontalAirResistance(0.92).withVerticalAirResistance(0.92));
	}

	@Override
	public void tick(long time) {
        this.prevPos = this.getPosition();
        this.velocity = this.velocity.add(0, -this.customGravity * ServerFlag.SERVER_TICKS_PER_SECOND, 0);
		super.tick(time);
	}

	@Override
	public void update(long time) {
		if (!(this.getShooter() instanceof Player shooter)) {
            this.remove();
			return;
		}
		if (this.shouldStopFishing(shooter)) return;

		if (this.onGround) {
            this.stuckTime++;
			if (this.stuckTime >= 1200) {
                this.remove();
				return;
			}
		} else {
            this.stuckTime = 0;
		}

		if (this.state == State.IN_AIR) {
			if (this.hooked != null) {
                this.velocity = Vec.ZERO;
                this.setNoGravity(true);
                this.state = State.HOOKED_ENTITY;
			}
		} else {
			if (this.state == State.HOOKED_ENTITY) {
				if (this.hooked != null) {
					if (this.hooked.isRemoved() || this.hooked.getInstance() != this.getInstance()) {
                        this.setHookedEntity(null);
                        this.setNoGravity(false);
                        this.state = State.IN_AIR;
					} else {
						Pos hookedPos = this.hooked.getPosition();
                        this.teleport(hookedPos.withY(hookedPos.y() + this.hooked.getBoundingBox().height() * 0.8));
					}
				}
			}
		}
	}

	@Override
	public boolean onHit(Entity entity) {
		if (this.hooked != null) return false;
        this.setHookedEntity(entity);

		if (this.legacy) {
			if (entity instanceof Player player
					&& (player == this.getShooter() || player.getGameMode() == GameMode.CREATIVE))
				return false;

			Pos posNow = this.position;
			this.position = this.prevPos;
			if (((LivingEntity) entity).damage(new Damage(DamageType.GENERIC, null, null, null, 0))) {
				entity.setVelocity(this.calculateLegacyKnockback(entity.getVelocity(), entity.getPosition()));
			}
			this.position = posNow;
		}

		return false;
	}

	private void setHookedEntity(@Nullable Entity entity) {
		this.hooked = entity;
		((FishingHookMeta) this.getEntityMeta()).setHookedEntity(entity);
	}

	private void setOwnerEntity(@Nullable Entity entity) {
		((FishingHookMeta) this.getEntityMeta()).setOwnerEntity(entity);
	}

	private boolean shouldStopFishing(Player player) {
		boolean main = player.getItemInMainHand().material() == Material.FISHING_ROD;
		boolean off = player.getItemInOffHand().material() == Material.FISHING_ROD;
		if (player.isRemoved() || player.isDead() || (!main && !off)
				|| (!this.legacy && this.getDistanceSquared(player) > 1024)) {
            this.setOwnerEntity(null);
            this.remove();
			return true;
		}

		return false;
	}

	public int retrieve() {
		if (!(this.getShooter() instanceof Player shooter)) return 0;
		if (this.shouldStopFishing(shooter)) return 0;

		int durability = 0;
		if (this.hooked != null) {
			if (!this.legacy) {
                this.pullEntity(this.hooked);
                this.triggerStatus((byte) 31);
			}
			durability = this.hooked instanceof ItemEntity ? 3 : 5;
		}

        this.remove();

		return durability;
	}

	private void pullEntity(Entity entity) {
		Entity shooter = this.getShooter();
		if (shooter == null) return;

		Pos shooterPos = shooter.getPosition();
		Pos pos = this.getPosition();
		Vec velocity = new Vec(shooterPos.x() - pos.x(), shooterPos.y() - pos.y(),
				shooterPos.z() - pos.z()).mul(0.1);
		velocity = velocity.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
		entity.setVelocity(entity.getVelocity().add(velocity));
	}

	private Vec calculateLegacyKnockback(Vec currentVelocity, Pos entityPos) {
		currentVelocity = currentVelocity.div(ServerFlag.SERVER_TICKS_PER_SECOND);

		Pos position = this.getPosition();
		double dx = position.x() - entityPos.x();
		double dz = position.z() - entityPos.z();

		while (dx * dx + dz * dz < 0.0001) {
			dx = (Math.random() - Math.random()) * 0.01;
			dz = (Math.random() - Math.random()) * 0.01;
		}

		double distance = Math.sqrt(dx * dx + dz * dz);

		double x = currentVelocity.x() / 2;
		double y = currentVelocity.y() / 2;
		double z = currentVelocity.z() / 2;

		// Normalize to have similar knockback on every distance
		x -= dx / distance * 0.4;
		y += 0.4;
		z -= dz / distance * 0.4;

		if (y > 0.4)
			y = 0.4;

		return new Vec(x, y, z).mul(ServerFlag.SERVER_TICKS_PER_SECOND);
	}

	@Override
	public void remove() {
		Entity shooter = this.getShooter();
		if (shooter != null) {
			if (shooter.getTag(VanillaFishingRodFeature.FISHING_BOBBER) == this) {
				shooter.removeTag(VanillaFishingRodFeature.FISHING_BOBBER);
			}
		}

		super.remove();
	}

	private enum State {
		IN_AIR,
		HOOKED_ENTITY,
		BOBBING
	}

	@Override
	protected int getUpdateInterval() {
		return 5;
	}
}
