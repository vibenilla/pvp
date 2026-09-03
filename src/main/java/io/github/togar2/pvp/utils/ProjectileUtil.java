package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.collision.PhysicsResult;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Copied from Minestom, added singleCollision parameter and removed velocity update
public class ProjectileUtil {
    public static @NotNull PhysicsResult simulateMovement(@NotNull Pos entityPosition, @NotNull Vec entityVelocityPerTick,
                                                          @NotNull BoundingBox entityBoundingBox, @NotNull WorldBorder worldBorder,
                                                          @NotNull Block.Getter blockGetter, boolean entityHasPhysics,
                                                          @Nullable PhysicsResult previousPhysicsResult,
                                                          boolean singleCollision) {
        var physicsResult = entityHasPhysics ?
                CollisionUtil.handlePhysics(blockGetter, entityBoundingBox, entityPosition, entityVelocityPerTick, previousPhysicsResult, singleCollision) :
                CollisionUtil.blocklessCollision(entityPosition, entityVelocityPerTick);

        var newPosition = physicsResult.newPosition();
        var newVelocity = physicsResult.newVelocity();

        var positionWithinBorder = CollisionUtil.applyWorldBorder(worldBorder, entityPosition, newPosition);
        return new PhysicsResult(positionWithinBorder, newVelocity, physicsResult.isOnGround(), physicsResult.collisionX(), physicsResult.collisionY(), physicsResult.collisionZ(),
                physicsResult.originalDelta(), physicsResult.collisionPoints(), physicsResult.collisionShapes(), physicsResult.collisionShapePositions(), physicsResult.hasCollision(), physicsResult.collisionFraction());
    }
}
