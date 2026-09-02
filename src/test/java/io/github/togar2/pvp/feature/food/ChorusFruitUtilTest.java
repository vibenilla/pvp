package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.utils.BlockUtil;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class ChorusFruitUtilTest {
    @Test
    public void teleportLandsOnAFreeSpotAboveASolidBlock(Env env) {
        var instance = env.createFlatInstance();
        instance.loadChunk(0, 0).join();
        for (var x = 3; x <= 13; x++) {
            for (var z = 3; z <= 13; z++) {
                if ((x + z) % 3 != 0) continue;
                for (var y = 40; y <= 43; y++) instance.setBlock(x, y, z, Block.STONE);
            }
        }

        var entity = new LivingEntity(EntityType.ZOMBIE);
        entity.setNoGravity(true);
        entity.setInstance(instance, new Pos(8.0, 40.0, 8.0)).join();
        var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);

        var teleports = 0;
        for (var attempt = 0; attempt < 40; attempt++) {
            var before = entity.getPosition();
            ChorusFruitUtil.tryChorusTeleport(entity, 8.0F);
            var position = entity.getPosition();
            if (position.samePoint(before)) continue;

            teleports++;
            assertFalse(BlockUtil.hasCollision(blockGetter, position, entity.getBoundingBox()));
            assertTrue(blockGetter.getBlock(position.blockX(), position.blockY() - 1, position.blockZ()).blocksMotion());
        }

        assertTrue(teleports > 0);
    }
}
