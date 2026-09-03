package io.github.togar2.pvp.utils;

import io.github.togar2.pvp.collision.BlockCollision;
import io.github.togar2.pvp.collision.EntityCollision;
import io.github.togar2.pvp.collision.PhysicsResult;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

public final class CollisionUtil {
    private CollisionUtil() {}

    public static PhysicsResult handlePhysics(Block.Getter blockGetter, BoundingBox boundingBox,
                                              Pos position, Vec velocity,
                                              @Nullable PhysicsResult previousPhysicsResult,
                                              boolean singleCollision) {
        return BlockCollision.handlePhysics(boundingBox, velocity, position, blockGetter,
                previousPhysicsResult, singleCollision);
    }

    public static PhysicsResult blocklessCollision(Pos position, Vec velocity) {
        return BlockCollision.blocklessCollision(position, velocity);
    }

    public static List<EntityCollisionResult> checkEntityCollisions(Instance instance, BoundingBox boundingBox,
                                                                    Point position, Vec velocity, double extendRadius,
                                                                    Function<Entity, Boolean> entityFilter,
                                                                    @Nullable PhysicsResult physicsResult) {
        return EntityCollision.checkCollision(instance.getEntityTracker(), boundingBox, position, velocity,
                extendRadius, entityFilter, physicsResult);
    }

    public static boolean hasLineOfSight(Instance instance, Point start, Point end) {
        var startPosition = start.asPos();
        var blockGetter = new ChunkBlockGetter(instance, instance.getChunkAt(startPosition), Block.STONE);
        var result = BlockCollision.handlePhysics(new BoundingBox(0.0, 0.0, 0.0), end.sub(start).asVec(),
                startPosition, blockGetter, null, false);

        return result.newPosition().samePoint(end, 1.0E-5);
    }

    public static @Nullable Pos clipBlocks(Block.Getter blockGetter, Point start, Point end) {
        var delta = end.sub(start).asVec();
        if (delta.isZero()) return null;

        var result = BlockCollision.handlePhysics(new BoundingBox(0.0, 0.0, 0.0), delta, start.asPos(),
                blockGetter, null, true);

        return result.hasCollision() ? result.newPosition() : null;
    }

    public static Pos applyWorldBorder(WorldBorder worldBorder, Pos currentPosition, Pos newPosition) {
        var radius = worldBorder.diameter() / 2.0;
        var collisionX = newPosition.x() > worldBorder.centerX() + radius
                || newPosition.x() < worldBorder.centerX() - radius;
        var collisionZ = newPosition.z() > worldBorder.centerZ() + radius
                || newPosition.z() < worldBorder.centerZ() - radius;

        if (!collisionX && !collisionZ) return newPosition;

        return newPosition.withCoord(
                collisionX ? currentPosition.x() : newPosition.x(),
                newPosition.y(),
                collisionZ ? currentPosition.z() : newPosition.z()
        );
    }
}
