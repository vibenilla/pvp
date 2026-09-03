package io.github.togar2.pvp.player;

import io.github.togar2.pvp.feature.state.VanillaPlayerStateFeature;
import io.github.togar2.pvp.utils.BlockUtil;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.CollisionUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.FluidUtil.FluidHeights;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import io.github.togar2.pvp.collision.PhysicsResult;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityVelocityEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class CombatPlayerImpl extends Player implements CombatPlayer {
    private boolean velocityUpdate = false;
    private boolean horizontalCollision = false;
    private PhysicsResult previousPhysicsResult = null;
    private Vec stuckSpeedMultiplier = Vec.ZERO;

    public CombatPlayerImpl(@NotNull PlayerConnection playerConnection, GameProfile profile) {
        super(playerConnection, profile);
    }

    @Override
    public void setVelocity(@NotNull Vec velocity) {
        var entityVelocityEvent = new EntityVelocityEvent(this, velocity);
        EventDispatcher.callCancellable(entityVelocityEvent, () -> {
            this.velocity = entityVelocityEvent.getVelocity();
            this.velocityUpdate = true;
        });
    }

    @Override
    public void setVelocityNoUpdate(Function<Vec, Vec> function) {
        this.velocity = function.apply(this.velocity);
    }

    @Override
    public void sendImmediateVelocityUpdate() {
        if (this.velocityUpdate) {
            this.velocityUpdate = false;
            this.sendPacketToViewersAndSelf(this.getVelocityPacket());
        }
    }

    @Override
    public boolean hasHorizontalCollision() {
        return this.horizontalCollision;
    }

    public boolean isOnGroundAfterTicks(int ticks) {
        if (this.vehicle != null) return false;

        var ticksPerSecond = ServerFlag.SERVER_TICKS_PER_SECOND;
        var velocity = this.velocity.div(ticksPerSecond);
        var position = this.position;

        var knownMovement = this.getTag(VanillaPlayerStateFeature.KNOWN_MOVEMENT);
        if (knownMovement != null && knownMovement.lengthSquared() > velocity.lengthSquared()) velocity = knownMovement;

        var aerodynamics = this.getAerodynamics();
        if (velocity.y() < 0 && this.hasEffect(PotionEffect.SLOW_FALLING))
            aerodynamics = aerodynamics.withGravity(0.01);

        var prevPhysicsResult = this.previousPhysicsResult;
        var stuckSpeedMultiplier = this.stuckSpeedMultiplier;
        for (var tick = 0; tick < ticks; tick++) {
            var blockGetter = new ChunkBlockGetter(this.instance, this.currentChunk, Block.STONE);
            var movementResult = this.simulateMovement(position, velocity, stuckSpeedMultiplier,
                    aerodynamics, this.onGround, blockGetter, prevPhysicsResult);
            var physicsResult = movementResult.physicsResult();
            prevPhysicsResult = physicsResult;
            stuckSpeedMultiplier = movementResult.stuckSpeedMultiplier();

            if (physicsResult.isOnGround()) return true;

            velocity = physicsResult.newVelocity();
            position = physicsResult.newPosition();

            var levitation = this.getEffect(PotionEffect.LEVITATION);
            if (levitation != null) {
                velocity = velocity.withY(
                        ((0.05 * (double) (levitation.potion().amplifier() + 1) - (velocity.y())) * 0.2)
                );
            }
        }

        return false;
    }

    @Override
    protected void movementTick() {
        this.gravityTickCount = this.onGround ? 0 : this.gravityTickCount + 1;
        this.horizontalCollision = false;
        if (this.vehicle != null) return;

        var ticksPerSecond = ServerFlag.SERVER_TICKS_PER_SECOND;

        var aerodynamics = this.getAerodynamics();
        if (this.velocity.y() < 0 && this.hasEffect(PotionEffect.SLOW_FALLING))
            aerodynamics = aerodynamics.withGravity(0.01);

        var blockGetter = new ChunkBlockGetter(this.instance, this.currentChunk, Block.STONE);
        var movementResult = this.simulateMovement(this.position, this.velocity.div(ticksPerSecond), this.stuckSpeedMultiplier,
                aerodynamics, this.onGround, blockGetter, this.previousPhysicsResult);
        var physicsResult = movementResult.physicsResult();
        this.previousPhysicsResult = physicsResult;
        this.stuckSpeedMultiplier = movementResult.stuckSpeedMultiplier();

        var finalChunk = this.instance.getChunkAt(physicsResult.newPosition());
        if (finalChunk == null || !finalChunk.isLoaded()) return;

        this.horizontalCollision = physicsResult.collisionX() || physicsResult.collisionZ();
        var oldHorizontalSpeed = this.velocity.div(ticksPerSecond).withY(0.0).length();
        var newHorizontalSpeed = physicsResult.newVelocity().withY(0.0).length();
        this.velocity = physicsResult.newVelocity().mul(ticksPerSecond);

        this.handleFallFlyingCollision(physicsResult, oldHorizontalSpeed, newHorizontalSpeed);

        var levitation = this.getEffect(PotionEffect.LEVITATION);
        if (levitation != null) {
            this.velocity = this.velocity.withY(
                    ((0.05 * (double)
                            (levitation.potion().amplifier() + 1)
                            - (this.velocity.y() / ticksPerSecond)) * 0.2) * ticksPerSecond
            );
        }

        this.sendImmediateVelocityUpdate();
    }

    private record MovementResult(PhysicsResult physicsResult, Vec stuckSpeedMultiplier) {}

    private MovementResult simulateMovement(Pos position, Vec velocity, Vec stuckSpeedMultiplier,
                                            Aerodynamics aerodynamics, boolean onGround,
                                            Block.Getter blockGetter, @Nullable PhysicsResult previousPhysicsResult) {
        var fluidHeights = this.isFlying()
                ? new FluidHeights(0.0, 0.0) : FluidUtil.getFluidHeights(blockGetter, position, this.boundingBox);
        var gravity = this.hasNoGravity() ? 0.0 : aerodynamics.gravity();
        var isFalling = velocity.y() <= 0.0;

        var stuck = stuckSpeedMultiplier.lengthSquared() > 1.0E-7;
        var delta = stuck ? velocity.mul(stuckSpeedMultiplier) : velocity;

        var gliding = this.isFlyingWithElytra() && fluidHeights.water() <= 0.0 && fluidHeights.lava() <= 0.0
                && !BlockUtil.isClimbable(blockGetter, position);
        if (gliding) delta = this.updateFallFlyingMovement(delta, gravity);

        var physicsResult = this.hasPhysics
                ? CollisionUtil.handlePhysics(blockGetter, this.boundingBox, position, delta, previousPhysicsResult, false)
                : CollisionUtil.blocklessCollision(position, delta);

        var newPosition = CollisionUtil.applyWorldBorder(this.instance.getWorldBorder(),
                position, physicsResult.newPosition());
        var movedVelocity = stuck ? Vec.ZERO : physicsResult.newVelocity();

        Vec newVelocity;

        if (fluidHeights.water() > 0.0) {
            var collidedHorizontally = physicsResult.collisionX() || physicsResult.collisionZ();
            var climbable = collidedHorizontally && BlockUtil.isClimbable(blockGetter, newPosition);
            newVelocity = this.travelInWater(movedVelocity, gravity, isFalling, onGround, climbable);
        } else if (fluidHeights.lava() > 0.0) {
            newVelocity = this.travelInLava(movedVelocity, gravity, isFalling, fluidHeights.lava());
        } else if (gliding) {
            newVelocity = movedVelocity;
        } else {
            newVelocity = PhysicsUtils.updateVelocity(newPosition, movedVelocity, blockGetter, aerodynamics,
                    !newPosition.samePoint(position), this.isFlying(), onGround, this.hasNoGravity());
        }

        if (physicsResult == previousPhysicsResult && newVelocity.samePoint(physicsResult.newVelocity())
                && newPosition.samePoint(physicsResult.newPosition())) {
            return new MovementResult(physicsResult, this.collectStuckSpeedMultiplier(blockGetter, newPosition));
        }

        var newPhysicsResult = new PhysicsResult(newPosition, newVelocity, physicsResult.isOnGround(),
                physicsResult.collisionX(), physicsResult.collisionY(), physicsResult.collisionZ(),
                physicsResult.originalDelta(), physicsResult.collisionPoints(), physicsResult.collisionShapes(),
                physicsResult.collisionShapePositions(), physicsResult.hasCollision(), physicsResult.collisionFraction());

        return new MovementResult(newPhysicsResult, this.collectStuckSpeedMultiplier(blockGetter, newPosition));
    }

    private Vec updateFallFlyingMovement(Vec movement, double gravity) {
        var look = this.position.direction();
        var lean = Math.toRadians(this.position.pitch());
        var lookHorizontal = Math.sqrt(look.x() * look.x() + look.z() * look.z());
        var moveHorizontal = Math.sqrt(movement.x() * movement.x() + movement.z() * movement.z());
        var lift = Math.cos(lean) * Math.cos(lean);

        movement = movement.add(0.0, gravity * (-1.0 + lift * 0.75), 0.0);
        if (movement.y() < 0.0 && lookHorizontal > 0.0) {
            var convert = movement.y() * -0.1 * lift;
            movement = movement.add(look.x() * convert / lookHorizontal, convert, look.z() * convert / lookHorizontal);
        }

        if (lean < 0.0 && lookHorizontal > 0.0) {
            var convert = moveHorizontal * -Math.sin(lean) * 0.04;
            movement = movement.add(-look.x() * convert / lookHorizontal, convert * 3.2, -look.z() * convert / lookHorizontal);
        }

        if (lookHorizontal > 0.0) {
            movement = movement.add(
                    (look.x() / lookHorizontal * moveHorizontal - movement.x()) * 0.1, 0.0,
                    (look.z() / lookHorizontal * moveHorizontal - movement.z()) * 0.1
            );
        }

        return movement.mul(0.99F, 0.98F, 0.99F);
    }

    private Vec travelInWater(Vec movement, double gravity, boolean isFalling, boolean onGround, boolean climbable) {
        var slowDown = this.isSprinting() ? 0.9F : 0.8F;
        var waterWalker = (float) this.getAttributeValue(Attribute.WATER_MOVEMENT_EFFICIENCY);

        if (!onGround) waterWalker *= 0.5F;

        if (waterWalker > 0.0F) slowDown += (0.54600006F - slowDown) * waterWalker;

        if (this.hasEffect(PotionEffect.DOLPHINS_GRACE)) slowDown = 0.96F;

        if (climbable) movement = movement.withY(0.2);

        return this.applyFluidFallingAdjustment(movement.mul(slowDown, 0.8F, slowDown), gravity, isFalling);
    }

    private Vec travelInLava(Vec movement, double gravity, boolean isFalling, double lavaHeight) {
        var jumpThreshold = this.getEyeHeight() < 0.4 ? 0.0 : 0.4;

        if (lavaHeight <= jumpThreshold) {
            movement = this.applyFluidFallingAdjustment(movement.mul(0.5, 0.8F, 0.5), gravity, isFalling);
        } else {
            movement = movement.mul(0.5);
        }

        if (gravity != 0.0) movement = movement.sub(0.0, gravity / 4.0, 0.0);

        return movement;
    }

    private Vec applyFluidFallingAdjustment(Vec movement, double gravity, boolean isFalling) {
        if (gravity == 0.0 || this.isSprinting()) return movement;

        double verticalMovement;

        if (isFalling && Math.abs(movement.y() - 0.005) >= 0.003
                && Math.abs(movement.y() - gravity / 16.0) < 0.003) {
            verticalMovement = -0.003;
        } else {
            verticalMovement = movement.y() - gravity / 16.0;
        }

        return movement.withY(verticalMovement);
    }

    private Vec collectStuckSpeedMultiplier(Block.Getter blockGetter, Pos position) {
        if (this.isFlying()) return Vec.ZERO;

        return BlockUtil.getStuckSpeedMultiplier(blockGetter, position, this.boundingBox, this.hasEffect(PotionEffect.WEAVING));
    }

    private void handleFallFlyingCollision(PhysicsResult physicsResult, double oldHorizontalSpeed, double newHorizontalSpeed) {
        if (!this.isFlyingWithElytra()) return;
        if (!physicsResult.collisionX() && !physicsResult.collisionZ()) return;

        var speedDifference = oldHorizontalSpeed - newHorizontalSpeed;
        var damage = (float) (speedDifference * 10.0 - 3.0);
        if (!(damage > 0.0F)) return;

        ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
                damage > 4.0F ? SoundEvent.ENTITY_PLAYER_BIG_FALL : SoundEvent.ENTITY_PLAYER_SMALL_FALL,
                Sound.Source.PLAYER,
                1.0F,
                1.0F
        ), this);

        this.damage(DamageType.FLY_INTO_WALL, damage);
    }
}
