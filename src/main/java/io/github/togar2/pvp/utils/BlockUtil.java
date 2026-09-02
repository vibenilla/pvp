package io.github.togar2.pvp.utils;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockTags;

import java.util.Objects;

public class BlockUtil {
	public static boolean isClimbable(Block.Getter blockGetter, int blockX, int blockY, int blockZ, Block block) {
		var climbable = Block.staticRegistry().getTag(BlockTags.CLIMBABLE);

		if (climbable != null && climbable.contains(block)) return true;

		var trapdoors = Block.staticRegistry().getTag(BlockTags.TRAPDOORS);

		if (trapdoors == null || !trapdoors.contains(block)) return false;
		if (!"true".equals(block.getProperty("open"))) return false;

		var below = blockGetter.getBlock(blockX, blockY - 1, blockZ);

		return below.compare(Block.LADDER)
				&& Objects.equals(below.getProperty("facing"), block.getProperty("facing"));
	}

	public static boolean isClimbable(Block.Getter blockGetter, Point position) {
		return isClimbable(blockGetter, position.blockX(), position.blockY(), position.blockZ(),
				blockGetter.getBlock(position));
	}

	public static Vec getStuckSpeedMultiplier(Block.Getter blockGetter, Pos position, BoundingBox boundingBox, boolean weaving) {
		var startX = (int) Math.floor(position.x() + boundingBox.minX() + 1.0E-5);
		var startY = (int) Math.floor(position.y() + boundingBox.minY() + 1.0E-5);
		var startZ = (int) Math.floor(position.z() + boundingBox.minZ() + 1.0E-5);
		var endX = (int) Math.ceil(position.x() + boundingBox.maxX() - 1.0E-5) - 1;
		var endY = (int) Math.ceil(position.y() + boundingBox.maxY() - 1.0E-5) - 1;
		var endZ = (int) Math.ceil(position.z() + boundingBox.maxZ() - 1.0E-5) - 1;
		var multiplier = Vec.ZERO;

		for (var blockX = startX; blockX <= endX; blockX++) {
			for (var blockY = startY; blockY <= endY; blockY++) {
				for (var blockZ = startZ; blockZ <= endZ; blockZ++) {
					var block = blockGetter.getBlock(blockX, blockY, blockZ);

					if (block.compare(Block.COBWEB)) {
						multiplier = weaving ? new Vec(0.5, 0.25, 0.5) : new Vec(0.25, 0.05F, 0.25);
					} else if (block.compare(Block.POWDER_SNOW)) {
						if (blockGetter.getBlock(position).compare(Block.POWDER_SNOW))
							multiplier = new Vec(0.9F, 1.5, 0.9F);
					} else if (block.compare(Block.SWEET_BERRY_BUSH)) {
						multiplier = new Vec(0.8F, 0.75, 0.8F);
					}
				}
			}
		}

		return multiplier;
	}
}
