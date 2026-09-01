package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.fall.FallFeature;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.RelativeFlags;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class ThrownEnderpearlTest {
    @Test
    public void teleportKeepsOwnerMomentumAndView(Env env) {
        var instance = env.createFlatInstance();
        var connection = env.createConnection();
        var player = connection.connect(instance, new Pos(0.0, 42.0, 0.0, 90.0F, 10.0F));
        var velocity = new Vec(4.0, 2.0, -1.0);
        player.setVelocity(velocity);

        var pearl = new ThrownEnderpearl(player, FallFeature.NO_OP);
        pearl.setInstance(instance, new Pos(3.0, 42.0, 3.0)).join();

        var packets = connection.trackIncoming(PlayerPositionAndLookPacket.class);
        pearl.onStuck();

        packets.assertSingle(packet -> {
            assertEquals(RelativeFlags.VIEW | RelativeFlags.DELTA_COORD, packet.flags());
            assertEquals(Vec.ZERO, packet.delta());
            assertEquals(0.0F, packet.yaw());
            assertEquals(0.0F, packet.pitch());
        });
        assertEquals(velocity, player.getVelocity());
        assertEquals(90.0F, player.getPosition().yaw());
        assertEquals(10.0F, player.getPosition().pitch());
    }
}
