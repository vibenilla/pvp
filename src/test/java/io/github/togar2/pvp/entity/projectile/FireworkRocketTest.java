package io.github.togar2.pvp.entity.projectile;

import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkList;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class FireworkRocketTest {
    @Test
    public void placedRocketFliesUpward(Env env) {
        var instance = env.createFlatInstance();
        var rocket = new FireworkRocket(null, ItemStack.of(Material.FIREWORK_ROCKET).with(
                DataComponents.FIREWORKS, new FireworkList(1, List.of())
        ), false);
        rocket.setInstance(instance, new Pos(0.5, 41.0, 0.5)).join();

        for (var tick = 0; tick < 15; tick++) env.tick();

        assertTrue(rocket.getPosition().y() > 46.0, "rocket only reached " + rocket.getPosition().y());
    }
}
