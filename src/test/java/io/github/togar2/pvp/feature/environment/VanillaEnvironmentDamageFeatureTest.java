package io.github.togar2.pvp.feature.environment;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.utils.PotionFlags;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnvTest
public final class VanillaEnvironmentDamageFeatureTest {
    @Test
    public void turtleHelmetAppliesWaterBreathingAboveWater(Env env) {
        var node = this.addEnvironmentFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.TURTLE_HELMET));

            env.tick();

            var waterBreathing = player.getEffect(PotionEffect.WATER_BREATHING);
            assertNotNull(waterBreathing);
            assertEquals(200, waterBreathing.potion().duration());
            assertEquals(0, waterBreathing.potion().amplifier());
            assertEquals(PotionFlags.create(false, false, true), waterBreathing.potion().flags());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void turtleHelmetDoesNotApplyWaterBreathingUnderwater(Env env) {
        var node = this.addEnvironmentFeature();

        try {
            var instance = env.createFlatInstance();
            instance.setBlock(0, 41, 0, Block.WATER);

            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setEquipment(EquipmentSlot.HELMET, ItemStack.of(Material.TURTLE_HELMET));

            env.tick();

            assertFalse(player.hasEffect(PotionEffect.WATER_BREATHING));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void burningDealsDamageEverySecondFromIgnition(Env env) {
        var node = this.addEnvironmentFeature();

        try {
            var instance = env.createFlatInstance();
            var entity = new LivingEntity(EntityType.ZOMBIE);
            entity.setInstance(instance, new Pos(8.0, 41.0, 8.0)).join();
            var damages = env.trackEvent(EntityDamageEvent.class, EventFilter.ENTITY, entity);

            entity.setHealth(20.0F);
            entity.setFireTicks(80);
            for (var tick = 0; tick < 80; tick++) env.tick();

            damages.assertCount(4);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addEnvironmentFeature() {
        var node = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENVIRONMENT_DAMAGE)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }
}
