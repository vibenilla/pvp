package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@EnvTest
public final class FishingBobberTest {
    @Test
    public void castBobberFloatsAtTheWaterSurface(Env env) {
        var instance = env.createFlatInstance();
        var shooter = env.createPlayer(instance, new Pos(8.0, 41.0, 4.0));
        shooter.setItemInMainHand(ItemStack.of(Material.FISHING_ROD));
        for (var y = 37; y <= 39; y++) instance.setBlock(8, y, 8, Block.WATER);

        var bobber = new FishingBobber(shooter, false);
        bobber.setInstance(instance, new Pos(8.5, 44.0, 8.5)).join();
        for (var tick = 0; tick < 60; tick++) env.tick();

        assertFalse(bobber.isRemoved());
        assertEquals(39.0 + 8.0 / 9.0, bobber.getPosition().y(), 0.3);
    }

    @Test
    public void bobberOnTheGroundStopsMoving(Env env) {
        var instance = env.createFlatInstance();
        var shooter = env.createPlayer(instance, new Pos(8.0, 41.0, 4.0));
        shooter.setItemInMainHand(ItemStack.of(Material.FISHING_ROD));

        var bobber = new FishingBobber(shooter, false);
        bobber.setInstance(instance, new Pos(8.5, 44.0, 8.5)).join();
        for (var tick = 0; tick < 60; tick++) env.tick();

        assertFalse(bobber.isRemoved());
        assertEquals(40.0, bobber.getPosition().y(), 0.05);
    }
}
