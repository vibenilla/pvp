package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.explosion.VanillaExplosionSupplier;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.projectile.FireworkRocketMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class FireworkRocket extends CustomEntityProjectile {
    private static final double HORIZONTAL_ACCELERATION = 1.15;
    private static final double VERTICAL_ACCELERATION = 0.04;

    private final boolean shotAtAngle;
    private int life;
    private int lifetime;
    private ItemStack itemStack;
    private LivingEntity attachedToEntity;

    public FireworkRocket(@Nullable Entity shooter, ItemStack itemStack, boolean shotAtAngle) {
        super(shooter, EntityType.FIREWORK_ROCKET);

        this.itemStack = itemStack.withAmount(1);
        this.life = 0;
        this.setNoGravity(true);
        this.setAerodynamics(new Aerodynamics(0.0, 1.0, 1.0));

        var fireworkList = this.itemStack.get(DataComponents.FIREWORKS);
        var flightCount = 1;

        if (fireworkList != null) {
            flightCount += fireworkList.flightDuration();
        }

        var random = ThreadLocalRandom.current();
        this.shotAtAngle = shotAtAngle;
        this.lifetime = 10 * flightCount + random.nextInt(6) + random.nextInt(7);
        this.setVelocity(new Vec(
                0.002297 * (random.nextDouble() - random.nextDouble()),
                0.05,
                0.002297 * (random.nextDouble() - random.nextDouble())
        ).mul(ServerFlag.SERVER_TICKS_PER_SECOND));

        var meta = (FireworkRocketMeta) this.getEntityMeta();
        meta.setShooter(null);
        meta.setFireworkInfo(this.itemStack);
        meta.setShotAtAngle(shotAtAngle);
    }

    public FireworkRocket(LivingEntity attachedToEntity, ItemStack itemStack) {
        this(attachedToEntity, itemStack, false);

        this.attachedToEntity = attachedToEntity;
        ((FireworkRocketMeta) this.getEntityMeta()).setShooter(attachedToEntity);
    }

    @Override
    public void update(long time) {
        if (this.attachedToEntity != null) {
            this.updateAttachedRocket();
        }

        super.update(time);

        if (this.life == 0 && !this.isSilent()) {
            ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
                    SoundEvent.ENTITY_FIREWORK_ROCKET_LAUNCH, Sound.Source.AMBIENT,
                    3.0F, 1.0F
            ), this);
        }

        this.life++;

        if (this.life > this.lifetime) {
            this.explode();
        }
    }

    @Override
    protected void movementTick() {
        if (this.attachedToEntity != null
                && !this.attachedToEntity.isRemoved()
                && !this.attachedToEntity.isDead()) {
            return;
        }

        if (!this.shotAtAngle) {
            var previousResult = this.getPreviousPhysicsResult();
            var horizontalCollision = previousResult != null
                    && (previousResult.collisionX() || previousResult.collisionZ());
            var horizontalAcceleration = horizontalCollision ? 1.0 : HORIZONTAL_ACCELERATION;

            this.velocity = this.velocity
                    .mul(horizontalAcceleration, 1.0, horizontalAcceleration)
                    .add(0.0, VERTICAL_ACCELERATION * ServerFlag.SERVER_TICKS_PER_SECOND, 0.0);
        }

        super.movementTick();
    }

    private void updateAttachedRocket() {
        if (this.attachedToEntity.isRemoved() || this.attachedToEntity.isDead()) {
            this.attachedToEntity = null;
            return;
        }

        if (this.attachedToEntity.isFlyingWithElytra()) {
            var look = this.attachedToEntity.getPosition().direction();
            var velocity = this.attachedToEntity.getVelocity();
            var ticksPerSecond = (double) ServerFlag.SERVER_TICKS_PER_SECOND;
            var boostedVelocity = velocity
                    .add(look.mul(0.1 * ticksPerSecond))
                    .add(look.mul(1.5 * ticksPerSecond).sub(velocity).mul(0.5));

            this.attachedToEntity.setVelocity(boostedVelocity);
        }

        this.refreshPosition(this.attachedToEntity.getPosition());
        this.setVelocity(this.attachedToEntity.getVelocity());
    }

    @Override
    public boolean onHit(@NotNull Entity entity) {
        this.explode();

        return true;
    }

    @Override
    public boolean onStuck() {
        if (this.hasExplosion()) {
            this.explode();

            return true;
        }

        return false;
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity != this.getShooter();
    }

    private void explode() {
        this.triggerStatus((byte) 17);
        this.dealExplosionDamage();
        this.scheduler().scheduleNextProcess(this::remove);
    }

    private void dealExplosionDamage() {
        var explosions = this.getExplosions();

        if (explosions.isEmpty()) {
            return;
        }

        var damageAmount = 5.0F + explosions.size() * 2.0F;
        var shooter = this.getShooter();
        var damage = new Damage(DamageType.FIREWORKS, this, Objects.requireNonNullElse(shooter, this), null, damageAmount);
        var instance = this.getInstance();

        if (instance == null) {
            return;
        }

        var center = this.getPosition();
        var radius = 5.0;
        var damageBox = new BoundingBox(radius * 2.0, radius * 2.0, radius * 2.0);
        var source = center.sub(0.0, radius, 0.0);

        for (var entity : instance.getEntities()) {
            if (!(entity instanceof LivingEntity livingEntity)) {
                continue;
            }

            if (!damageBox.intersectEntity(source, entity) || this.getDistance(entity) > radius) {
                continue;
            }

            if (!this.canDamage(center, entity)) {
                continue;
            }

            damage.setAmount(damageAmount * (float) Math.sqrt((radius - this.getDistance(entity)) / radius));
            livingEntity.damage(damage);
        }
    }

    private boolean canDamage(Point center, Entity entity) {
        var basePosition = entity.getPosition();

        for (var testStep = 0; testStep < 2; testStep++) {
            var targetPosition = basePosition.add(0.0, entity.getBoundingBox().height() * 0.5 * testStep, 0.0);

            if (VanillaExplosionSupplier.noBlocking(entity.getInstance(), center, targetPosition)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasExplosion() {
        return !this.getExplosions().isEmpty();
    }

    private List<FireworkExplosion> getExplosions() {
        var fireworkList = this.itemStack.get(DataComponents.FIREWORKS, FireworkList.EMPTY);

        return fireworkList.explosions();
    }

    public ItemStack getItemStack() {
        return this.itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack.material() == Material.FIREWORK_ROCKET
                ? itemStack.withAmount(1)
                : ItemStack.of(Material.FIREWORK_ROCKET);

        ((FireworkRocketMeta) this.getEntityMeta()).setFireworkInfo(this.itemStack);
    }
}
