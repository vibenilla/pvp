package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.coordinate.Pos;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaExplosionSupplierTest {
    @Test
    public void explosionInUnloadedChunksDoesNotThrow(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());

        assertDoesNotThrow(() -> instance.explode(1000.0F, 40.0F, 1000.0F, 4.0F));
    }

    @Test
    public void explosionEndsImpulseGraceTimeButKeepsImpactPosition(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();
        var fallFeature = featureSet.get(FeatureType.FALL);

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
        player.setGameMode(GameMode.SURVIVAL);
        fallFeature.setIgnoreFallDamageFromCurrentImpulse(player, 64.0);

        instance.explode(2.0F, 40.0F, 0.0F, 4.0F);

        assertEquals(64.0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        assertEquals(0, player.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));
    }

    @Test
    public void plainPlayerGetsKnockbackThroughTheExplosionPacket(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());
        var connection = env.createConnection();
        var player = connection.connect(instance, new Pos(0.0, 40.0, 0.0));
        player.setGameMode(GameMode.SURVIVAL);
        var explosions = connection.trackIncoming(ExplosionPacket.class);
        var velocities = connection.trackIncoming(EntityVelocityPacket.class);

        instance.explode(2.0F, 40.0F, 0.0F, 4.0F);

        var knockback = explosions.collect().stream()
                .map(ExplosionPacket::playerKnockback)
                .filter(Objects::nonNull)
                .findFirst();
        assertTrue(knockback.isPresent());
        assertTrue(knockback.get().x() < 0.0);
        assertTrue(velocities.collect().isEmpty());
    }

    @Test
    public void anchorTouchingWaterBreaksNoBlocks(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());
        instance.loadChunk(0, 0).join();
        instance.setBlock(1, 40, 0, Block.STONE);
        instance.setBlock(0, 40, 1, Block.STONE);

        instance.explode(0.5F, 40.5F, 0.5F, 5.0F, CompoundBinaryTag.builder()
                .putBoolean("anchor", true)
                .putBoolean("anchorWater", true)
                .build());

        assertTrue(instance.getBlock(1, 40, 0).compare(Block.STONE));
        assertTrue(instance.getBlock(0, 40, 1).compare(Block.STONE));
    }

    @Test
    public void explosionDropsBlockItemsWhenAsked(Env env) {
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_EXPLOSION)
                .build();

        var instance = env.createFlatInstance();
        instance.setExplosionSupplier(featureSet.get(FeatureType.EXPLOSION).getExplosionSupplier());
        instance.loadChunk(0, 0).join();
        instance.setBlock(1, 40, 0, Block.STONE);

        instance.explode(0.5F, 40.5F, 0.5F, 4.0F, CompoundBinaryTag.builder()
                .putBoolean("dropBlocks", true)
                .putBoolean("dropDecay", false)
                .build());

        assertTrue(instance.getBlock(1, 40, 0).air());
        assertTrue(instance.getEntities().stream()
                .anyMatch(entity -> entity instanceof ItemEntity item && item.getItemStack().material() == Material.STONE));
    }
}
