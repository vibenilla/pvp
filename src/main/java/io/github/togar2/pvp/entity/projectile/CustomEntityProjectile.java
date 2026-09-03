package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.collision.PhysicsResult;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.CollisionUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ProjectileUtil;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.metadata.projectile.ProjectileMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithBlockEvent;
import net.minestom.server.event.entity.projectile.ProjectileCollideWithEntityEvent;
import net.minestom.server.event.entity.projectile.ProjectileUncollideEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class CustomEntityProjectile extends Entity {
    private static final BoundingBox POINT_BOX = new BoundingBox(0, 0, 0);
    private static final BoundingBox UNSTUCK_BOX = new BoundingBox(0.12, 0.6, 0.12);

    private Entity shooter;
    protected boolean noClip;

    protected Vec collisionDirection;

    private PhysicsResult previousPhysicsResult = null;
    private boolean leftOwner;

    public CustomEntityProjectile(@Nullable Entity shooter, @NotNull EntityType entityType) {
        super(entityType);
        this.shooter = shooter;
        this.setup();
    }

    private void setup() {
        this.collidesWithEntities = false;
        this.preventBlockPlacement = false;
        this.setAerodynamics(new Aerodynamics(this.getAerodynamics().gravity(), 0.99, 0.99));
        if (this.getEntityMeta() instanceof ProjectileMeta) {
            ((ProjectileMeta) this.getEntityMeta()).setShooter(this.shooter);
        }
        this.setSynchronizationTicks(this.getUpdateInterval());
    }

    public @Nullable Entity getShooter() {
        return this.shooter;
    }

    public void setShooter(@Nullable Entity shooter) {
        this.shooter = shooter;

        if (this.getEntityMeta() instanceof ProjectileMeta projectileMeta) {
            projectileMeta.setShooter(shooter);
        }
    }

    public boolean onStuck() {
        return false;
    }

    public boolean onStuck(ProjectileCollideWithBlockEvent event) {
        return this.onStuck();
    }

    public void onUnstuck() {

    }

    public boolean onHit(Entity entity) {
        return false;
    }

    public void shootFrom(Pos from, double power, double spread) {
        var to = from.add(this.shooter.getPosition().direction());
        this.shoot(from, to, power, spread);
    }

    @Deprecated
    public void shootTo(Point to, double power, double spread) {
        var from = this.shooter.getPosition().add(0, this.shooter.getEyeHeight(), 0);
        this.shoot(from, to, power, spread);
    }

    public void shootFromRotation(float pitch, float yaw, float yBias, double power, double spread) {
        var dx = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        var dy = -Math.sin(Math.toRadians(pitch + yBias));
        var dz = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
        this.shoot(dx, dy, dz, power, spread);
    }

    public void shoot(double dx, double dy, double dz, double power, double spread) {

        var length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;
        var random = ThreadLocalRandom.current();
        spread *= 0.0172275;
        dx += (random.nextDouble() - random.nextDouble()) * spread;
        dy += (random.nextDouble() - random.nextDouble()) * spread;
        dz += (random.nextDouble() - random.nextDouble()) * spread;

        var velocityScale = ServerFlag.SERVER_TICKS_PER_SECOND * power;
        this.velocity = new Vec(dx * velocityScale, dy * velocityScale, dz * velocityScale);
        this.setView(
                (float) Math.toDegrees(Math.atan2(dx, dz)),
                (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))
        );
    }

    private void shoot(@NotNull Pos from, @NotNull Point to, double power, double spread) {
        var pitch = -from.pitch();
        var pitchDiff = pitch - 45;
        if (pitchDiff == 0) pitchDiff = 0.0001F;
        var pitchAdjust = pitchDiff * 0.002145329238474369;

        var dx = to.x() - from.x();
        var dy = to.y() - from.y() + pitchAdjust;
        var dz = to.z() - from.z();
        if (!this.hasNoGravity()) {
            var horizontalLength = Math.sqrt(dx * dx + dz * dz);
            dy += horizontalLength * 0.20000000298023224;
        }

        this.shoot(dx, dy, dz, power, spread);
    }

    @Override
    public void tick(long time) {
        super.tick(time);
        if (this.isRemoved()) return;

        if (this.isStuck() && this.shouldUnstuck()) {
            EventDispatcher.call(new ProjectileUncollideEvent(this));
            this.collisionDirection = null;
            this.setNoGravity(false);
            this.onUnstuck();
        }
    }

    public boolean isStuck() {
        return this.collisionDirection != null;
    }

    protected float computeMargin() {
        return Math.clamp((this.getAliveTicks() - 2) / 20.0F, 0.0F, 0.3F);
    }

    protected @Nullable PhysicsResult getPreviousPhysicsResult() {
        return this.previousPhysicsResult;
    }

    private boolean shouldUnstuck() {
        var collidedPoint = this.position.add(this.collisionDirection.mul(0.003));
        var collidedBlockVec = collidedPoint.asBlockVec();
        var blockGetter = new ChunkBlockGetter(this.instance, this.currentChunk, Block.STONE);
        var block = blockGetter.getBlock(collidedPoint);

        return !block.collisionShape().intersectBox(collidedPoint.sub(collidedBlockVec).sub(0, 0.6, 0), UNSTUCK_BOX);
    }

    protected boolean canHit(Entity entity) {
        return entity instanceof LivingEntity && !(entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR);
    }

    @Override
    protected void synchronizePosition() {
        if (this.isStuck()) return;

        super.synchronizePosition();
    }

    private float prevYaw, prevPitch;

    @Override
    protected void movementTick() {
        this.gravityTickCount = this.isStuck() ? 0 : this.gravityTickCount + 1;
        if (this.vehicle != null) return;

        if (!this.isStuck()) {
            if (this.shouldUpdateVelocityBeforeMovement()) {
                this.updateVelocityBeforeMovement();
            }

            var diff = this.velocity.div(ServerFlag.SERVER_TICKS_PER_SECOND);
            this.checkLeftOwner(diff);

            if (this.instance.isInVoid(this.position)) {
                this.scheduler().scheduleNextProcess(this::remove);
                return;
            }

            var blockGetter = new ChunkBlockGetter(this.instance, this.currentChunk, Block.AIR);
            var physicsResult = ProjectileUtil.simulateMovement(this.position, diff, POINT_BOX,
                    this.instance.getWorldBorder(), blockGetter, this.hasPhysics, this.previousPhysicsResult, true);
            this.previousPhysicsResult = physicsResult;

            var newPosition = physicsResult.newPosition();

            if (!this.noClip) {
                var noCollideShooter = this.getAliveTicks() < 6;

                var margin = this.computeMargin();
                var sweepBox = margin > 0.0F ? new BoundingBox(margin * 2.0, margin * 2.0, margin * 2.0) : POINT_BOX;
                var entityResult = CollisionUtil.checkEntityCollisions(this.instance, sweepBox,
                        this.position.sub(0.0, margin, 0.0), diff, 3, e -> {
                            if ((noCollideShooter || !this.leftOwner) && e == this.shooter) return false;
                            return e != this && this.canHit(e);
                        }, physicsResult);

                for (var collided : entityResult.stream().sorted().toList()) {
                    var prevVelocity = this.velocity;

                    var event = new ProjectileCollideWithEntityEvent(this, collided.collisionPoint().asPos(), collided.entity());
                    EventDispatcher.call(event);
                    if (event.isCancelled()) continue;

                    this.refreshPosition(this.position.withCoord(collided.collisionPoint()), this.noClip, this.isStuck());

                    if (this.onHit(collided.entity())) {
                        this.scheduler().scheduleNextProcess(this::remove);
                        return;
                    }

                    if (this.velocity != prevVelocity) {
                        newPosition = this.position.add(this.velocity.div(ServerFlag.SERVER_TICKS_PER_SECOND));
                        break;
                    }
                }
            }

            var finalChunk = this.instance.getChunkAt(physicsResult.newPosition());
            if (finalChunk == null || !finalChunk.isLoaded()) return;

            if (physicsResult.hasCollision() && !this.isStuck() && this.sticksToBlocks()) {
                var signumX = physicsResult.collisionX() ? Math.signum(this.velocity.x()) : 0;
                var signumY = physicsResult.collisionY() ? Math.signum(this.velocity.y()) : 0;
                var signumZ = physicsResult.collisionZ() ? Math.signum(this.velocity.z()) : 0;
                var collisionDirection = new Vec(signumX, signumY, signumZ);

                var collidedPosition = collisionDirection.add(physicsResult.newPosition()).apply(Vec.Operator.FLOOR);
                var block = this.instance.getBlock(collidedPosition);

                var event = new ProjectileCollideWithBlockEvent(this, physicsResult.newPosition().withCoord(collidedPosition), block);
                EventDispatcher.call(event);
                if (!event.isCancelled()) {
                    this.setNoGravity(true);
                    this.setVelocity(Vec.ZERO);
                    this.collisionDirection = collisionDirection;
                    this.refreshPosition(this.position.withCoord(physicsResult.newPosition()), this.noClip, true);

                    if (this.onStuck(event)) {
                        this.scheduler().scheduleNextProcess(this::remove);
                    }
                }
            }

            this.onGround = physicsResult.isOnGround();

            var yaw = this.position.yaw();
            var pitch = this.position.pitch();

            if (!this.noClip) {
                yaw = (float) Math.toDegrees(Math.atan2(diff.x(), diff.z()));
                pitch = (float) Math.toDegrees(
                        Math.atan2(diff.y(), Math.sqrt(diff.x() * diff.x() + diff.z() * diff.z())));

                yaw = lerp(this.prevYaw, yaw);
                pitch = lerp(this.prevPitch, pitch);
            }

            this.prevYaw = yaw;
            this.prevPitch = pitch;

            this.refreshPosition(newPosition.withView(yaw, pitch), this.noClip, this.isStuck());

            if (!this.shouldUpdateVelocityBeforeMovement()) {
                this.updateVelocityAfterMovement();
            }
        }
    }

    private void updateVelocityBeforeMovement() {
        var aerodynamics = this.getAerodynamics();
        var touchingWater = FluidUtil.isTouchingWater(this);
        var horizontalInertia = touchingWater ? this.getWaterInertia() : aerodynamics.horizontalAirResistance();
        var verticalInertia = touchingWater ? this.getWaterInertia() : aerodynamics.verticalAirResistance();
        var gravity = this.hasNoGravity() ? 0.0 : aerodynamics.gravity() * ServerFlag.SERVER_TICKS_PER_SECOND;

        this.velocity = this.velocity.sub(0.0, gravity, 0.0).mul(horizontalInertia, verticalInertia, horizontalInertia);
    }

    private void updateVelocityAfterMovement() {
        if (FluidUtil.isTouchingWater(this)) {
            this.velocity = this.velocity.mul(this.getWaterInertia());
        }

        var aerodynamics = this.getAerodynamics();
        this.velocity = this.velocity.mul(
                aerodynamics.horizontalAirResistance(),
                aerodynamics.verticalAirResistance(),
                aerodynamics.horizontalAirResistance()
        ).sub(0.0, this.hasNoGravity() ? 0.0 : aerodynamics.gravity() * ServerFlag.SERVER_TICKS_PER_SECOND, 0.0);
    }

    private void checkLeftOwner(Vec movement) {
        if (this.leftOwner) return;

        this.leftOwner = this.isOutsideOwnerCollisionRange(movement);
    }

    private boolean isOutsideOwnerCollisionRange(Vec movement) {
        if (this.shooter == null) return true;

        var ownerPosition = this.shooter.getPosition();
        var ownerBox = this.shooter.getBoundingBox();
        var projectileBox = this.getBoundingBox();
        var nextPosition = this.position.add(movement);

        var minX = Math.min(this.position.x() + projectileBox.minX(), nextPosition.x() + projectileBox.minX()) - 1.0;
        var maxX = Math.max(this.position.x() + projectileBox.maxX(), nextPosition.x() + projectileBox.maxX()) + 1.0;
        var minY = Math.min(this.position.y() + projectileBox.minY(), nextPosition.y() + projectileBox.minY()) - 1.0;
        var maxY = Math.max(this.position.y() + projectileBox.maxY(), nextPosition.y() + projectileBox.maxY()) + 1.0;
        var minZ = Math.min(this.position.z() + projectileBox.minZ(), nextPosition.z() + projectileBox.minZ()) - 1.0;
        var maxZ = Math.max(this.position.z() + projectileBox.maxZ(), nextPosition.z() + projectileBox.maxZ()) + 1.0;

        return maxX < ownerPosition.x() + ownerBox.minX()
                || minX > ownerPosition.x() + ownerBox.maxX()
                || maxY < ownerPosition.y() + ownerBox.minY()
                || minY > ownerPosition.y() + ownerBox.maxY()
                || maxZ < ownerPosition.z() + ownerBox.minZ()
                || minZ > ownerPosition.z() + ownerBox.maxZ();
    }

    private static float lerp(float first, float second) {
        return first + (second - first) * 0.2F;
    }

    @Override
    public void setView(float yaw, float pitch) {
        this.prevYaw = yaw;
        this.prevPitch = pitch;

        super.setView(yaw, pitch);
    }

    @Override
    public @NotNull CompletableFuture<Void> teleport(@NotNull Pos position) {
        this.prevYaw = position.yaw();
        this.prevPitch = position.pitch();

        return super.teleport(position);
    }

    protected int getUpdateInterval() {
        return 20;
    }

    protected boolean sticksToBlocks() {
        return true;
    }

    protected boolean shouldUpdateVelocityBeforeMovement() {
        return false;
    }

    protected double getWaterInertia() {
        return 1.0;
    }
}
