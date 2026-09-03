package io.github.togar2.pvp.collision;

import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

public record PhysicsResult(
        Pos newPosition,
        Vec newVelocity,
        boolean isOnGround,
        boolean collisionX,
        boolean collisionY,
        boolean collisionZ,
        Vec originalDelta,
        Point[] collisionPoints,
        Shape[] collisionShapes,
        Point[] collisionShapePositions,
        boolean hasCollision,
        double collisionFraction
) {}
