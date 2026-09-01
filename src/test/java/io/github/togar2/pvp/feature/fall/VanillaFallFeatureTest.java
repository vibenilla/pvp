package io.github.togar2.pvp.feature.fall;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientTeleportConfirmPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaFallFeatureTest {
    @Test
    public void playerTakesFallDamageFromPacketMovement(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 47.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            this.move(player, new Pos(0.0, 46.0, 0.0), false);
            this.move(player, new Pos(0.0, 44.0, 0.0), false);
            this.move(player, new Pos(0.0, 42.0, 0.0), false);
            this.move(player, new Pos(0.0, 40.0, 0.0), true);

            assertEquals(16.0F, player.getHealth());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void landingPacketMovementCountsTowardFallDamage(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 44.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            this.move(player, new Pos(0.0, 42.0, 0.0), false);
            this.move(player, new Pos(0.0, 40.0, 0.0), true);

            assertEquals(19.0F, player.getHealth());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void sneakingOnSlimeBlockDoesNotPreventFallDamage(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            instance.setBlock(0, 39, 0, Block.SLIME_BLOCK);
            var player = env.createPlayer(instance, new Pos(0.0, 47.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            player.refreshInput(false, false, false, false, false, true, false);
            this.confirmTeleport(player);

            this.move(player, new Pos(0.0, 46.0, 0.0), false);
            this.move(player, new Pos(0.0, 44.0, 0.0), false);
            this.move(player, new Pos(0.0, 42.0, 0.0), false);
            this.move(player, new Pos(0.0, 40.0, 0.0), true);

            assertEquals(16.0F, player.getHealth());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void slimeBlockPreventsFallDamageWhenNotSneaking(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            instance.setBlock(0, 39, 0, Block.SLIME_BLOCK);
            var player = env.createPlayer(instance, new Pos(0.0, 47.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            this.move(player, new Pos(0.0, 46.0, 0.0), false);
            this.move(player, new Pos(0.0, 44.0, 0.0), false);
            this.move(player, new Pos(0.0, 42.0, 0.0), false);
            this.move(player, new Pos(0.0, 40.0, 0.0), true);

            assertEquals(20.0F, player.getHealth());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void groundedMovementKeepsImpulseContext(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            var fallFeature = CombatFeatures.empty()
                    .add(CombatFeatures.VANILLA_FALL)
                    .build()
                    .get(FeatureType.FALL);
            fallFeature.setIgnoreFallDamageFromCurrentImpulse(player);

            this.move(player, new Pos(1.0, 40.0, 0.0), true);
            this.move(player, new Pos(2.0, 40.0, 0.0), true);

            assertTrue(player.hasTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void groundedMovementOnHoneyPlaysNoSlideEffects(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            instance.setBlock(0, 39, 0, Block.HONEY_BLOCK);
            instance.setBlock(1, 39, 0, Block.HONEY_BLOCK);
            var connection = env.createConnection();
            var player = connection.connect(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            var statuses = connection.trackIncoming(EntityStatusPacket.class);
            this.move(player, new Pos(0.5, 40.0, 0.0), true);
            this.move(player, new Pos(1.0, 40.0, 0.0), true);

            assertTrue(statuses.collect().stream().noneMatch(packet -> packet.status() == 54));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void impulseGraceTimeCountsServerTicks(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);

            var fallFeature = CombatFeatures.empty()
                    .add(CombatFeatures.VANILLA_FALL)
                    .build()
                    .get(FeatureType.FALL);
            fallFeature.setIgnoreFallDamageFromCurrentImpulse(player);

            for (var tick = 0; tick < 39; tick++) env.tick();
            assertEquals(1, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));

            env.tick();
            assertEquals(0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addFallFeature() {
        var node = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
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
