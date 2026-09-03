package io.github.togar2.pvp.collision;

import net.minestom.server.collision.Shape;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable result of a swept collision test.
 * <p>
 * Minestom's equivalent declares no accessors and keeps every field package private, so it cannot
 * be read from outside {@code net.minestom.server.collision}. This library therefore carries its
 * own.
 */
public final class SweepResult {
    @Deprecated(forRemoval = true)
    public static final SweepResult NO_COLLISION =
            new SweepResult(Double.MAX_VALUE, 0.0, 0.0, 0.0, null, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    double result;
    double normalX, normalY, normalZ;
    double collidedPositionX, collidedPositionY, collidedPositionZ;
    double collidedShapeX, collidedShapeY, collidedShapeZ;
    int collidedBlockX, collidedBlockY, collidedBlockZ;
    @Nullable Shape collidedShape;

    public SweepResult(double result, double normalX, double normalY, double normalZ,
                       @Nullable Shape collidedShape,
                       double collidedPositionX, double collidedPositionY, double collidedPositionZ,
                       double collidedShapeX, double collidedShapeY, double collidedShapeZ) {
        this.result = result;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        this.collidedShape = collidedShape;
        this.collidedPositionX = collidedPositionX;
        this.collidedPositionY = collidedPositionY;
        this.collidedPositionZ = collidedPositionZ;
        this.collidedShapeX = collidedShapeX;
        this.collidedShapeY = collidedShapeY;
        this.collidedShapeZ = collidedShapeZ;
    }

    public double result() {
        return this.result;
    }

    public double normalX() {
        return this.normalX;
    }

    public double normalY() {
        return this.normalY;
    }

    public double normalZ() {
        return this.normalZ;
    }

    public @Nullable Shape collidedShape() {
        return this.collidedShape;
    }
}
