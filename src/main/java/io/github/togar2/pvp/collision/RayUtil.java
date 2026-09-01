package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import org.jetbrains.annotations.Nullable;

public final class RayUtil {
	private RayUtil() {}

	/**
	 * Checks whether a moving bounding box intersects a static bounding box, writing the
	 * intersection details to {@code finalResult} when the hit is not farther than the one it
	 * already holds.
	 *
	 * @param movingBoundingBox the bounding box being moved
	 * @param rayStart          the position the movement starts at
	 * @param rayDirection      the movement vector
	 * @param staticBoundingBox the bounding box being checked against
	 * @param staticOffset      the world position of the static bounding box
	 * @param finalResult       the sweep result to write the intersection details to
	 * @return true if an intersection within the current result was found
	 */
	public static boolean checkIntersection(BoundingBox movingBoundingBox, Point rayStart, Point rayDirection,
	                                        BoundingBox staticBoundingBox, Point staticOffset,
	                                        SweepResult finalResult) {
		return !Double.isNaN(intersectionPercentage(movingBoundingBox, rayStart, rayDirection,
				staticBoundingBox, staticOffset, finalResult.result, finalResult));
	}

	public static double intersectionPercentage(BoundingBox movingBoundingBox, Point rayStart, Point rayDirection,
	                                            BoundingBox staticBoundingBox, Point staticOffset,
	                                            double maxPercentage) {
		return intersectionPercentage(movingBoundingBox, rayStart, rayDirection,
				staticBoundingBox, staticOffset, maxPercentage, null);
	}

	private static double intersectionPercentage(BoundingBox movingBoundingBox, Point rayStart, Point rayDirection,
	                                             BoundingBox staticBoundingBox, Point staticOffset,
	                                             double maxPercentage, @Nullable SweepResult finalResult) {
		final var halfWidth = movingBoundingBox.width() / 2;
		final var halfHeight = movingBoundingBox.height() / 2;
		final var halfDepth = movingBoundingBox.depth() / 2;

		final var rayCenterX = rayStart.x() + movingBoundingBox.minX() + halfWidth;
		final var rayCenterY = rayStart.y() + movingBoundingBox.minY() + halfHeight;
		final var rayCenterZ = rayStart.z() + movingBoundingBox.minZ() + halfDepth;

		final var rayDirectionX = rayDirection.x();
		final var rayDirectionY = rayDirection.y();
		final var rayDirectionZ = rayDirection.z();

		final var expandedMinX = staticBoundingBox.minX() + staticOffset.x() - halfWidth;
		final var expandedMinY = staticBoundingBox.minY() + staticOffset.y() - halfHeight;
		final var expandedMinZ = staticBoundingBox.minZ() + staticOffset.z() - halfDepth;
		final var expandedMaxX = staticBoundingBox.maxX() + staticOffset.x() + halfWidth;
		final var expandedMaxY = staticBoundingBox.maxY() + staticOffset.y() + halfHeight;
		final var expandedMaxZ = staticBoundingBox.maxZ() + staticOffset.z() + halfDepth;

		final double entryX, exitX;

		if (rayDirectionX == 0) {
			if (rayCenterX < expandedMinX || rayCenterX > expandedMaxX) return Double.NaN;

			entryX = Double.NEGATIVE_INFINITY;
			exitX = Double.POSITIVE_INFINITY;
		} else if (rayDirectionX > 0) {
			entryX = epsilon((expandedMinX - rayCenterX) / rayDirectionX);
			exitX = (expandedMaxX - rayCenterX) / rayDirectionX;
		} else {
			entryX = epsilon((expandedMaxX - rayCenterX) / rayDirectionX);
			exitX = (expandedMinX - rayCenterX) / rayDirectionX;
		}

		final double entryZ, exitZ;

		if (rayDirectionZ == 0) {
			if (rayCenterZ < expandedMinZ || rayCenterZ > expandedMaxZ) return Double.NaN;

			entryZ = Double.NEGATIVE_INFINITY;
			exitZ = Double.POSITIVE_INFINITY;
		} else if (rayDirectionZ > 0) {
			entryZ = epsilon((expandedMinZ - rayCenterZ) / rayDirectionZ);
			exitZ = (expandedMaxZ - rayCenterZ) / rayDirectionZ;
		} else {
			entryZ = epsilon((expandedMaxZ - rayCenterZ) / rayDirectionZ);
			exitZ = (expandedMinZ - rayCenterZ) / rayDirectionZ;
		}

		final double entryY, exitY;

		if (rayDirectionY == 0) {
			if (rayCenterY < expandedMinY || rayCenterY > expandedMaxY) return Double.NaN;

			entryY = Double.NEGATIVE_INFINITY;
			exitY = Double.POSITIVE_INFINITY;
		} else if (rayDirectionY > 0) {
			entryY = epsilon((expandedMinY - rayCenterY) / rayDirectionY);
			exitY = (expandedMaxY - rayCenterY) / rayDirectionY;
		} else {
			entryY = epsilon((expandedMaxY - rayCenterY) / rayDirectionY);
			exitY = (expandedMinY - rayCenterY) / rayDirectionY;
		}

		var percentage = entryX;
		var collisionFace = 0;

		if (entryZ > percentage) {
			percentage = entryZ;
			collisionFace = 1;
		}

		if (entryY > percentage) {
			percentage = entryY;
			collisionFace = 2;
		}

		if (percentage > Math.min(exitX, Math.min(exitY, exitZ)) || percentage < 0) return Double.NaN;

		percentage *= 0.99999;

		if (!(percentage <= maxPercentage)) return Double.NaN;

		if (finalResult != null) {
			finalResult.result = percentage;
			finalResult.normalX = collisionFace == 0 ? 1 : 0;
			finalResult.normalY = collisionFace == 2 ? 1 : 0;
			finalResult.normalZ = collisionFace == 1 ? 1 : 0;
		}

		return percentage;
	}

	private static double epsilon(double value) {
		return Math.abs(value) < Point.EPSILON ? 0 : value;
	}
}
