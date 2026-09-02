package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.utils.BlockUtil;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.world.DimensionType;

import java.util.concurrent.ThreadLocalRandom;

public class ChorusFruitUtil {
	private static boolean randomTeleport(Entity entity, Pos to) {
		Instance instance = entity.getInstance();
		assert instance != null;
		if (!instance.isChunkLoaded(to)) return false;

		var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);
		int minY = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType()).minY();

		double y = to.y();
		int blockY = to.blockY();
		boolean landed = false;
		while (!landed && blockY > minY) {
			if (blockGetter.getBlock(to.blockX(), blockY - 1, to.blockZ()).blocksMotion()) {
				landed = true;
			} else {
				y--;
				blockY--;
			}
		}

		if (!landed) return false;

		Pos target = to.withY(y);
		BoundingBox boundingBox = entity.getBoundingBox();
		if (BlockUtil.hasCollision(blockGetter, target, boundingBox)
				|| BlockUtil.containsLiquid(blockGetter, target, boundingBox)) {
			return false;
		}

		entity.teleport(target);
		entity.triggerStatus((byte) 46);

		return true;
	}

	public static void tryChorusTeleport(Entity entity, float diameter) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Instance instance = entity.getInstance();
		assert instance != null;
		Pos prevPosition = entity.getPosition();
		double prevX = prevPosition.x();
		double prevY = prevPosition.y();
		double prevZ = prevPosition.z();

		float pitch = prevPosition.pitch();
		float yaw = prevPosition.yaw();

		DimensionType dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType());
		assert dimensionType != null;

		// Max 16 tries
		for (int i = 0; i < 16; i++) {
			double x = prevX + (random.nextDouble() - 0.5) * diameter;
			double y = Math.clamp(prevY + (random.nextDouble() - 0.5) * diameter,
					dimensionType.minY(), dimensionType.minY()
							+ dimensionType.logicalHeight() - 1);
			double z = prevZ + (random.nextDouble() - 0.5) * diameter;

			if (entity.getVehicle() != null) {
				entity.getVehicle().removePassenger(entity);
			}

			if (randomTeleport(entity, new Pos(x, y, z, yaw, pitch))) {
				ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
						SoundEvent.ITEM_CHORUS_FRUIT_TELEPORT, Sound.Source.PLAYER,
						1.0f, 1.0f
				), entity);

				break;
			}
		}
	}
}
