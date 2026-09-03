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
        var instance = entity.getInstance();
        assert instance != null;
        if (!instance.isChunkLoaded(to)) return false;

        var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);
        var minY = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType()).minY();

        var y = to.y();
        var blockY = to.blockY();
        var landed = false;
        while (!landed && blockY > minY) {
            if (blockGetter.getBlock(to.blockX(), blockY - 1, to.blockZ()).blocksMotion()) {
                landed = true;
            } else {
                y--;
                blockY--;
            }
        }

        if (!landed) return false;

        var target = to.withY(y);
        var boundingBox = entity.getBoundingBox();
        if (BlockUtil.hasCollision(blockGetter, target, boundingBox)
                || BlockUtil.containsLiquid(blockGetter, target, boundingBox)) {
            return false;
        }

        entity.teleport(target);
        entity.triggerStatus((byte) 46);

        return true;
    }

    public static void tryChorusTeleport(Entity entity, float diameter) {
        var random = ThreadLocalRandom.current();
        var instance = entity.getInstance();
        assert instance != null;
        var prevPosition = entity.getPosition();
        var prevX = prevPosition.x();
        var prevY = prevPosition.y();
        var prevZ = prevPosition.z();

        var pitch = prevPosition.pitch();
        var yaw = prevPosition.yaw();

        var dimensionType = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType());
        assert dimensionType != null;

        for (var index = 0; index < 16; index++) {
            var x = prevX + (random.nextDouble() - 0.5) * diameter;
            var y = Math.clamp(prevY + (random.nextDouble() - 0.5) * diameter,
                    dimensionType.minY(), dimensionType.minY()
                            + dimensionType.logicalHeight() - 1);
            var z = prevZ + (random.nextDouble() - 0.5) * diameter;

            if (entity.getVehicle() != null) {
                entity.getVehicle().removePassenger(entity);
            }

            if (randomTeleport(entity, new Pos(x, y, z, yaw, pitch))) {
                ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
                        SoundEvent.ITEM_CHORUS_FRUIT_TELEPORT, Sound.Source.PLAYER,
                        1.0F, 1.0F
                ), entity);

                break;
            }
        }
    }
}
