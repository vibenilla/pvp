package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.item.SnowballMeta;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Snowball extends CustomEntityProjectile implements ItemHoldingProjectile {

	public Snowball(@Nullable Entity shooter) {
		super(shooter, EntityType.SNOWBALL);
	}

	@Override
	public boolean onHit(Entity entity) {
        this.triggerStatus((byte) 3); // Snowball particles

		int damage = entity.getEntityType() == EntityType.BLAZE ? 3 : 0;
		((LivingEntity) entity).damage(new Damage(DamageType.THROWN, this, this.getShooter(), null, damage));

		return true;
	}

	@Override
	public boolean onStuck() {
        this.triggerStatus((byte) 3); // Snowball particles

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
	public void setItem(@NotNull ItemStack item) {
		((SnowballMeta) this.getEntityMeta()).setItem(item);
	}

	@Override
	protected int getUpdateInterval() {
		return 10;
	}
}
