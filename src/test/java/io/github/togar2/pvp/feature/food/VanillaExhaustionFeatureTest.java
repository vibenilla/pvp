package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class VanillaExhaustionFeatureTest {
    @Test
    public void walkingUpwardOnGroundIsNotAJump(Env env) {
        var node = this.addExhaustionFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            this.move(player, new Pos(0.0, 40.0, 0.0), true);
            this.move(player, new Pos(0.3, 40.5, 0.0), true);
            assertEquals(0.0F, player.getTag(VanillaExhaustionFeature.EXHAUSTION), 1.0E-6);

            this.move(player, new Pos(0.6, 40.9, 0.0), false);
            assertEquals(0.05F, player.getTag(VanillaExhaustionFeature.EXHAUSTION), 1.0E-6);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addExhaustionFeature() {
        var node = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_EXHAUSTION)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }

    private void confirmTeleport(Player player) {
        player.addPacketToQueue(new ClientTeleportConfirmPacket(player.getLastSentTeleportId()));
        player.interpretPacketQueue();
    }

    private void move(Player player, Pos position, boolean onGround) {
        player.addPacketToQueue(new ClientPlayerPositionPacket(position, onGround, false));
        player.interpretPacketQueue();
    }
}
