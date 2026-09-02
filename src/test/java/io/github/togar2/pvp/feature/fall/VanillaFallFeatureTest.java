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
import net.minestom.server.network.packet.server.play.EntitySoundEffectPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    public void fallDamageEndsImpulseContext(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 47.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            var fallFeature = CombatFeatures.empty()
                    .add(CombatFeatures.VANILLA_FALL)
                    .build()
                    .get(FeatureType.FALL);
            fallFeature.setIgnoreFallDamageFromCurrentImpulse(player, 60.0);

            this.move(player, new Pos(0.0, 44.0, 0.0), false);
            this.move(player, new Pos(0.0, 40.0, 0.0), true);

            assertEquals(16.0F, player.getHealth());
            assertFalse(player.hasTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void damagingFallPlaysTheLandingBlockSound(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            var faller = env.createPlayer(instance, new Pos(8.0, 47.0, 8.0));
            faller.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(faller);
            var viewerConnection = env.createConnection();
            viewerConnection.connect(instance, new Pos(10.0, 40.0, 8.0));
            var expectedSound = instance.getBlock(8, 39, 8).blockSoundType().fallSound().key();

            var sounds = viewerConnection.trackIncoming(EntitySoundEffectPacket.class);
            this.move(faller, new Pos(8.0, 44.0, 8.0), false);
            this.move(faller, new Pos(8.0, 40.0, 8.0), true);

            assertTrue(sounds.collect().stream().anyMatch(packet -> packet.soundEvent().key().equals(expectedSound)));
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

    @Test
    public void cobwebResetsFallDistance(Env env) {
        var node = this.addFallFeature();

        try {
            var instance = env.createFlatInstance();
            instance.setBlock(8, 42, 8, Block.COBWEB);
            var player = env.createPlayer(instance, new Pos(8.0, 50.0, 8.0));
            player.setGameMode(GameMode.SURVIVAL);
            this.confirmTeleport(player);

            this.move(player, new Pos(8.0, 48.0, 8.0), false);
            this.move(player, new Pos(8.0, 46.0, 8.0), false);
            this.move(player, new Pos(8.0, 44.0, 8.0), false);
            this.move(player, new Pos(8.0, 42.5, 8.0), false);
            this.move(player, new Pos(8.0, 40.0, 8.0), true);

            assertEquals(20.0F, player.getHealth());
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
