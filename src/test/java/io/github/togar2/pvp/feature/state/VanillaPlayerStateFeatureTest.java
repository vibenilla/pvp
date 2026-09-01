package io.github.togar2.pvp.feature.state;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaPlayerStateFeatureTest {
    @Test
    public void knownMovementFollowsTheClientMovementPackets(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_PLAYER_STATE)
                .add(CombatFeatures.VANILLA_MISC_PROJECTILE)
                .build();
        var node = featureSet.createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);
            var tps = ServerFlag.SERVER_TICKS_PER_SECOND;

            for (var tick = 1; tick <= 3; tick++) {
                this.move(player, new Pos(8.0 + 0.28 * tick, 40.0, 8.0), true);
                env.tick();
            }

            var knownMovement = featureSet.get(FeatureType.PLAYER_STATE).getKnownMovement(player);
            assertEquals(0.28 * tps, knownMovement.x(), 1.0E-6);
            assertEquals(0.0, knownMovement.z(), 1.0E-6);

            player.setItemInMainHand(ItemStack.of(Material.ENDER_PEARL));
            EventDispatcher.call(new PlayerUseItemEvent(player, PlayerHand.MAIN, player.getItemInMainHand(), 0L));

            var pearl = instance.getEntities().stream()
                    .filter(entity -> entity.getEntityType() == EntityType.ENDER_PEARL)
                    .findFirst().orElseThrow();
            assertTrue(pearl.getVelocity().x() > 0.28 * tps - 1.5, "pearl velocity " + pearl.getVelocity());

            env.tick();
            assertEquals(0.0, featureSet.get(FeatureType.PLAYER_STATE).getKnownMovement(player).x(), 1.0E-6);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
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
