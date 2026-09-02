package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.projectile.VanillaFishingRodFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.FluidUtil;
import java.util.concurrent.ThreadLocalRandom;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.other.FishingHookMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
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

		this.setAerodynamics(this.getAerodynamics().withHorizontalAirResistance(0.92).withVerticalAirResistance(0.92));
	}

	@Override
	public void tick(long time) {
		this.prevPos = this.getPosition();
		if (!(this.getShooter() instanceof Player shooter)) {
			this.remove();
			return;
		}
		if (this.shouldStopFishing(shooter)) return;

		this.tickState();
		if (this.isRemoved()) return;

		super.tick(time);
		if (this.isRemoved()) return;

		var physicsResult = this.getPreviousPhysicsResult();
		if (this.state == State.IN_AIR && physicsResult != null
				&& (this.onGround || physicsResult.collisionX() || physicsResult.collisionZ())) {
			this.velocity = Vec.ZERO;
		}
	}

	private void tickState() {
		if (this.onGround) {
			this.stuckTime++;
			if (this.stuckTime >= 1200) {
				this.remove();
				return;
			}
		} else {
			this.stuckTime = 0;
		}

		var instance = this.getInstance();
		if (instance == null) return;

		double liquidHeight = this.getWaterHeight(instance);
		boolean inWater = liquidHeight > 0.0;
		double tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		Vec movement = this.velocity.div(tps);

		switch (this.state) {
			case IN_AIR -> {
				if (this.hooked != null) {
					movement = Vec.ZERO;
					this.state = State.HOOKED_ENTITY;
				} else if (inWater) {
					movement = movement.mul(0.3, 0.2, 0.3);
					this.state = State.BOBBING;
				}
			}
			case HOOKED_ENTITY -> {
				movement = Vec.ZERO;
				if (this.hooked != null) {
					if (this.hooked.isRemoved() || this.hooked.getInstance() != instance) {
						this.setHookedEntity(null);
						this.state = State.IN_AIR;
					} else {
						Pos hookedPos = this.hooked.getPosition();
						this.teleport(hookedPos.withY(hookedPos.y() + this.hooked.getBoundingBox().height() * 0.8));
					}
				}
			}
			case BOBBING -> {
				double force = this.position.y() + movement.y() - Math.floor(this.position.y()) - liquidHeight;
				if (Math.abs(force) < 0.01) force += Math.signum(force) * 0.1;

				movement = new Vec(movement.x() * 0.9,
						movement.y() - force * ThreadLocalRandom.current().nextFloat() * 0.2, movement.z() * 0.9);
			}
		}

		if (!inWater && !this.onGround && this.hooked == null) {
			movement = movement.sub(0.0, this.customGravity, 0.0);
		}

		this.velocity = movement.mul(tps);
	}

	private double getWaterHeight(Instance instance) {
		var blockGetter = new ChunkBlockGetter(instance, this.currentChunk, Block.AIR);
		var block = blockGetter.getBlock(this.position);
		if (!FluidUtil.isWater(block)) return 0.0;

		var above = blockGetter.getBlock(this.position.add(0.0, 1.0, 0.0));

		return FluidUtil.isWater(above) ? 1.0 : FluidUtil.getOwnHeight(block);
	}

	@Override
	protected boolean sticksToBlocks() {
		return false;
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

		if (entity instanceof Player) {
			Vec pull = velocity;
			if (entity instanceof CombatPlayer custom) custom.setVelocityNoUpdate(current -> current.add(pull));
			return;
		}

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
