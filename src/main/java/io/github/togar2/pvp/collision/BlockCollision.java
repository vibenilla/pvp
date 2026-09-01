package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BlockCollision {
	public static final Point[] NO_COLLISION_POINTS = new Point[3];
	public static final Shape[] NO_COLLISION_SHAPES = new Shape[3];
	public static final Point[] NO_COLLISION_SHAPE_POSITIONS = new Point[3];

	private BlockCollision() {}

	/**
	 * Moves a bounding box with physics applied, checking against blocks.
	 * <p>
	 * Works by getting all the full blocks the bounding box could interact with. All bounding boxes
	 * inside those full blocks are checked for collisions.
	 */
	public static PhysicsResult handlePhysics(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                          Block.Getter blockGetter,
	                                          @Nullable PhysicsResult lastPhysicsResult,
	                                          boolean singleCollision) {
		return handlePhysics(boundingBox, velocity, entityPosition, blockGetter,
				lastPhysicsResult, singleCollision, null);
	}

	public static PhysicsResult handlePhysics(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                          Block.Getter blockGetter,
	                                          @Nullable PhysicsResult lastPhysicsResult,
	                                          boolean singleCollision,
	                                          @Nullable WorldBorder worldBorder) {
		if (velocity.isZero()) {
			return new PhysicsResult(entityPosition, Vec.ZERO, false, false, false, false,
					velocity, NO_COLLISION_POINTS, NO_COLLISION_SHAPES, NO_COLLISION_SHAPE_POSITIONS,
					false, Double.MAX_VALUE);
		}

		final var cachedResult = cachedPhysics(velocity, entityPosition, blockGetter, lastPhysicsResult);

		if (cachedResult != null) return cachedResult;

		return stepPhysics(boundingBox, velocity, entityPosition, blockGetter, singleCollision, worldBorder);
	}

	/**
	 * Simulates collision physics as if the world had no blocks.
	 */
	public static PhysicsResult blocklessCollision(Pos entityPosition, Vec entityVelocity) {
		return new PhysicsResult(entityPosition.add(entityVelocity), entityVelocity, false,
				false, false, false, entityVelocity, NO_COLLISION_POINTS, NO_COLLISION_SHAPES,
				NO_COLLISION_SHAPE_POSITIONS, false, Double.MAX_VALUE);
	}

	private static boolean intersectShapeSwept(Shape shape, Point rayStart, Point rayDirection, Point shapePosition,
	                                           BoundingBox movingBoundingBox, SweepResult finalResult) {
		var hitBlock = false;

		for (var blockSection : boundingBoxes(shape)) {
			if (RayUtil.checkIntersection(movingBoundingBox, rayStart, rayDirection,
					blockSection, shapePosition, finalResult)) {
				finalResult.collidedPositionX = rayStart.x() + rayDirection.x() * finalResult.result;
				finalResult.collidedPositionY = rayStart.y() + rayDirection.y() * finalResult.result;
				finalResult.collidedPositionZ = rayStart.z() + rayDirection.z() * finalResult.result;
				finalResult.collidedShapeX = shapePosition.x();
				finalResult.collidedShapeY = shapePosition.y();
				finalResult.collidedShapeZ = shapePosition.z();
				finalResult.collidedBlockX = shapePosition.blockX();
				finalResult.collidedBlockY = shapePosition.blockY();
				finalResult.collidedBlockZ = shapePosition.blockZ();
				finalResult.collidedShape = shape;
				hitBlock = true;
			}
		}

		return hitBlock;
	}

	private static List<BoundingBox> boundingBoxes(Shape shape) {
		if (shape instanceof ShapeImpl shapeImpl) return shapeImpl.boundingBoxes();
		if (shape instanceof BoundingBox boundingBox) return List.of(boundingBox);

		return List.of();
	}

	private static @Nullable PhysicsResult cachedPhysics(Vec velocity, Pos entityPosition,
	                                                     Block.Getter blockGetter,
	                                                     @Nullable PhysicsResult lastPhysicsResult) {
		if (lastPhysicsResult == null
				|| !lastPhysicsResult.collisionY()
				|| velocity.x() != 0 || velocity.z() != 0
				|| !velocity.samePoint(lastPhysicsResult.originalDelta())
				|| !entityPosition.samePoint(lastPhysicsResult.newPosition())) {
			return null;
		}

		if (!(lastPhysicsResult.collisionShapes()[1] instanceof ShapeImpl lastShape)) return null;

		final var blockPosition = lastPhysicsResult.collisionShapePositions()[1];
		assert blockPosition != null;

		final var lastBlockBoxes = lastShape.boundingBoxes();

		if (lastBlockBoxes.isEmpty()) return null;

		final var currentShape = blockGetter.getBlock(blockPosition, Block.Getter.Condition.TYPE).collisionShape();

		return currentShape instanceof ShapeImpl shape && shape.boundingBoxes().equals(lastBlockBoxes)
				? lastPhysicsResult : null;
	}

	private static PhysicsResult stepPhysics(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                         Block.Getter blockGetter, boolean singleCollision,
	                                         @Nullable WorldBorder worldBorder) {
		final var finalResult = new SweepResult(1 - Point.EPSILON, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);

		var collidedPoints = NO_COLLISION_POINTS;
		var collisionShapes = NO_COLLISION_SHAPES;
		var collisionShapePositions = NO_COLLISION_SHAPE_POSITIONS;

		double borderMinX = 0, borderMaxX = 0, borderMinZ = 0, borderMaxZ = 0;
		var sweepBorder = false;

		if (worldBorder != null) {
			final var radius = worldBorder.diameter() / 2;
			final var wallMinX = Math.floor(worldBorder.centerX() - radius);
			final var wallMaxX = Math.ceil(worldBorder.centerX() + radius);
			final var wallMinZ = Math.floor(worldBorder.centerZ() - radius);
			final var wallMaxZ = Math.ceil(worldBorder.centerZ() + radius);
			borderMinX = wallMinX - boundingBox.minX();
			borderMaxX = wallMaxX - boundingBox.maxX();
			borderMinZ = wallMinZ - boundingBox.minZ();
			borderMaxZ = wallMaxZ - boundingBox.maxZ();

			final var margin = Math.max(Math.max(boundingBox.width(), boundingBox.depth()), 1);
			final var targetX = entityPosition.x() + velocity.x();
			final var targetZ = entityPosition.z() + velocity.z();
			sweepBorder = (targetX < borderMinX || targetX > borderMaxX || targetZ < borderMinZ || targetZ > borderMaxZ)
					&& entityPosition.x() >= wallMinX - margin && entityPosition.x() < wallMaxX + margin
					&& entityPosition.z() >= wallMinZ - margin && entityPosition.z() < wallMaxZ + margin;
		}

		var position = entityPosition;
		var remaining = velocity;
		var foundX = false;
		var foundY = false;
		var foundZ = false;

		while (true) {
			if (sweepBorder) {
				sweepWorldBorderAxis(position.x(), remaining.x(), borderMinX, borderMaxX, 0,
						position, remaining, finalResult);
				sweepWorldBorderAxis(position.z(), remaining.z(), borderMinZ, borderMaxZ, 2,
						position, remaining, finalResult);
			}

			sweepBlocks(boundingBox, remaining, position, blockGetter, finalResult);

			final var result = finalResult.result;
			var deltaX = result * remaining.x();
			var deltaY = result * remaining.y();
			var deltaZ = result * remaining.z();

			if (Math.abs(deltaX) < Point.EPSILON) deltaX = 0;
			if (Math.abs(deltaY) < Point.EPSILON) deltaY = 0;
			if (Math.abs(deltaZ) < Point.EPSILON) deltaZ = 0;

			position = position.add(deltaX, deltaY, deltaZ);

			final int axis;

			if (finalResult.normalX != 0) axis = 0;
			else if (finalResult.normalY != 0) axis = 1;
			else if (finalResult.normalZ != 0) axis = 2;
			else break;

			if (axis == 0) foundX = true;
			else if (axis == 1) foundY = true;
			else foundZ = true;

			if (collidedPoints == NO_COLLISION_POINTS) collidedPoints = new Point[3];

			collidedPoints[axis] = new Vec(finalResult.collidedPositionX, finalResult.collidedPositionY, finalResult.collidedPositionZ);

			final var collidedShape = finalResult.collidedShape;

			if (collidedShape != null) {
				if (collisionShapes == NO_COLLISION_SHAPES) {
					collisionShapes = new Shape[3];
					collisionShapePositions = new Point[3];
				}

				collisionShapes[axis] = collidedShape;
				collisionShapePositions[axis] = new BlockVec(finalResult.collidedBlockX, finalResult.collidedBlockY, finalResult.collidedBlockZ);
			}

			if (singleCollision || (foundX && foundY && foundZ)) break;

			remaining = new Vec(
					axis == 0 ? 0 : remaining.x() - deltaX,
					axis == 1 ? 0 : remaining.y() - deltaY,
					axis == 2 ? 0 : remaining.z() - deltaZ);

			if (remaining.isZero()) break;

			finalResult.normalX = 0;
			finalResult.normalY = 0;
			finalResult.normalZ = 0;
			finalResult.result = 1 - Point.EPSILON;
		}

		final var anyCollision = foundX || foundY || foundZ;
		final var allCollision = foundX && foundY && foundZ;

		final Vec newDelta;

		if (!anyCollision) {
			newDelta = velocity;
		} else if (allCollision) {
			newDelta = Vec.ZERO;
		} else {
			newDelta = new Vec(foundX ? 0 : velocity.x(), foundY ? 0 : velocity.y(), foundZ ? 0 : velocity.z());
		}

		return new PhysicsResult(position, newDelta,
				foundY && velocity.y() < 0,
				foundX, foundY, foundZ,
				velocity, collidedPoints, collisionShapes, collisionShapePositions,
				anyCollision, finalResult.result);
	}

	private static void sweepWorldBorderAxis(double position, double velocity,
	                                         double minimum, double maximum, int axis,
	                                         Pos entityPosition, Vec entityVelocity,
	                                         SweepResult finalResult) {
		final double percentage;

		if (velocity > 0) {
			if (position > maximum || position + velocity <= maximum) return;

			percentage = (maximum - position) / velocity;
		} else if (velocity < 0) {
			if (position < minimum || position + velocity >= minimum) return;

			percentage = (minimum - position) / velocity;
		} else {
			return;
		}

		final var acceptedPercentage = percentage * 0.99999;

		if (!(acceptedPercentage <= finalResult.result)) return;

		finalResult.result = acceptedPercentage;
		finalResult.normalX = axis == 0 ? 1 : 0;
		finalResult.normalY = 0;
		finalResult.normalZ = axis == 2 ? 1 : 0;
		finalResult.collidedPositionX = entityPosition.x() + entityVelocity.x() * acceptedPercentage;
		finalResult.collidedPositionY = entityPosition.y() + entityVelocity.y() * acceptedPercentage;
		finalResult.collidedPositionZ = entityPosition.z() + entityVelocity.z() * acceptedPercentage;
		finalResult.collidedShape = null;
	}

	private static void sweepBlocks(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                Block.Getter blockGetter, SweepResult finalResult) {
		final var startX = entityPosition.x();
		final var startY = entityPosition.y();
		final var startZ = entityPosition.z();
		final var endX = startX + velocity.x();
		final var endY = startY + velocity.y();
		final var endZ = startZ + velocity.z();

		final var minX = (int) Math.floor(Math.min(startX, endX) + boundingBox.minX());
		final var minY = (int) Math.floor(Math.min(startY, endY) + boundingBox.minY());
		final var minZ = (int) Math.floor(Math.min(startZ, endZ) + boundingBox.minZ());
		final var maxX = (int) Math.floor(Math.max(startX, endX) + boundingBox.maxX());
		final var maxY = (int) Math.floor(Math.max(startY, endY) + boundingBox.maxY());
		final var maxZ = (int) Math.floor(Math.max(startZ, endZ) + boundingBox.maxZ());

		final var stepX = velocity.x() < 0 ? -1 : 1;
		final var stepY = velocity.y() < 0 ? -1 : 1;
		final var stepZ = velocity.z() < 0 ? -1 : 1;
		final var firstX = stepX > 0 ? minX : maxX;
		final var lastX = stepX > 0 ? maxX : minX;
		final var firstY = stepY > 0 ? minY : maxY;
		final var lastY = stepY > 0 ? maxY : minY;
		final var firstZ = stepZ > 0 ? minZ : maxZ;
		final var lastZ = stepZ > 0 ? maxZ : minZ;

		for (var blockX = firstX; blockX != lastX + stepX; blockX += stepX) {
			for (var blockY = firstY; blockY != lastY + stepY; blockY += stepY) {
				for (var blockZ = firstZ; blockZ != lastZ + stepZ; blockZ += stepZ) {
					checkBoundingBox(blockX, blockY, blockZ, velocity, entityPosition,
							boundingBox, blockGetter, finalResult);
				}
			}
		}
	}

	private static boolean checkBoundingBox(int blockX, int blockY, int blockZ,
	                                        Vec entityVelocity, Pos entityPosition, BoundingBox boundingBox,
	                                        Block.Getter blockGetter, SweepResult finalResult) {
		final var currentBlock = blockGetter.getBlock(blockX, blockY, blockZ, Block.Getter.Condition.TYPE);
		final var currentShape = currentBlock.collisionShape();

		final var currentCollidable = !currentShape.relativeEnd().isZero();
		final var currentShort = currentShape.relativeEnd().y() < 0.5;

		if (currentShort && shouldCheckLower(entityVelocity, entityPosition, blockX, blockY, blockZ)) {
			final var belowPosition = new BlockVec(blockX, blockY - 1, blockZ);
			final var belowBlock = blockGetter.getBlock(belowPosition, Block.Getter.Condition.TYPE);
			final var belowShape = belowBlock.collisionShape();

			final var currentPosition = new BlockVec(blockX, blockY, blockZ);

			if (belowShape.relativeEnd().y() > 1) {
				final var belowHit = intersectShapeSwept(belowShape, entityPosition, entityVelocity,
						belowPosition, boundingBox, finalResult);
				final var currentHit = currentCollidable && intersectShapeSwept(currentShape, entityPosition,
						entityVelocity, currentPosition, boundingBox, finalResult);

				return belowHit || currentHit;
			} else {
				return currentCollidable && intersectShapeSwept(currentShape, entityPosition, entityVelocity,
						currentPosition, boundingBox, finalResult);
			}
		}

		if (currentCollidable && intersectShapeSwept(currentShape, entityPosition, entityVelocity,
				new BlockVec(blockX, blockY, blockZ), boundingBox, finalResult)) {
			if (currentShort) {
				final var belowPosition = new BlockVec(blockX, blockY - 1, blockZ);
				final var belowBlock = blockGetter.getBlock(belowPosition, Block.Getter.Condition.TYPE);
				final var belowShape = belowBlock.collisionShape();

				if (belowShape.relativeEnd().y() > 1)
					intersectShapeSwept(belowShape, entityPosition, entityVelocity,
							belowPosition, boundingBox, finalResult);
			}

			return true;
		}

		return false;
	}

	private static boolean shouldCheckLower(Vec entityVelocity, Pos entityPosition,
	                                        int blockX, int blockY, int blockZ) {
		final var yVelocity = entityVelocity.y();

		if (yVelocity == 0) return Math.floor(entityPosition.y()) == blockY;

		final var xVelocity = entityVelocity.x();
		final var zVelocity = entityVelocity.z();

		if (xVelocity == 0 && zVelocity == 0)
			return yVelocity < 0 && blockY == Math.floor(entityPosition.y() + yVelocity);

		final var underYX = xVelocity != 0
				&& computeHeight(yVelocity, xVelocity, entityPosition.y(), entityPosition.x(), blockX) >= blockY;
		final var underYZ = zVelocity != 0
				&& computeHeight(yVelocity, zVelocity, entityPosition.y(), entityPosition.z(), blockZ) >= blockY;

		return underYX && underYZ;
	}

	private static double computeHeight(double yVelocity, double velocity,
	                                    double entityY, double position, int blockPosition) {
		final var slope = yVelocity / velocity;

		return slope * (blockPosition - position + (slope > 0 ? 1 : 0)) + entityY;
	}
}
