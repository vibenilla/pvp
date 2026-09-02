package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.player.CombatPlayerImpl;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkList;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    @Test
    public void attachedRocketBoostsCombatPlayersWithoutVelocityPackets(Env env) {
        MinecraftServer.getConnectionManager().setPlayerProvider(CombatPlayerImpl::new);
        var instance = env.createFlatInstance();
        var connection = env.createConnection();
        var player = assertInstanceOf(CombatPlayerImpl.class, connection.connect(instance, new Pos(8.0, 60.0, 8.0)));
        player.setFlyingWithElytra(true);
        var rocket = new FireworkRocket(player, ItemStack.of(Material.FIREWORK_ROCKET));
        rocket.setInstance(instance, player.getPosition()).join();
        var velocities = connection.trackIncoming(EntityVelocityPacket.class);

        for (var tick = 0; tick < 5; tick++) env.tick();

        assertTrue(player.getVelocity().z() > 0.0, "server side velocity carries the boost");
        assertTrue(velocities.collect().stream().noneMatch(packet -> packet.entityId() == player.getEntityId()),
                "the client applies the boost itself");
    }

    @Test
    public void attachedRocketSendsPlainPlayersNoVelocityPackets(Env env) {
        var instance = env.createFlatInstance();
        var connection = env.createConnection();
        var player = connection.connect(instance, new Pos(8.0, 60.0, 8.0));
        player.setFlyingWithElytra(true);
        var rocket = new FireworkRocket(player, ItemStack.of(Material.FIREWORK_ROCKET));
        rocket.setInstance(instance, player.getPosition()).join();
        var velocities = connection.trackIncoming(EntityVelocityPacket.class);

        for (var tick = 0; tick < 5; tick++) env.tick();

        assertTrue(velocities.collect().stream().noneMatch(packet -> packet.entityId() == player.getEntityId()));
    }
}
