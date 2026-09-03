package io.github.togar2.pvp.feature.damage;

import io.github.togar2.pvp.feature.CombatFeatures;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.BlocksAttacks;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.sound.SoundEvent;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaDamageFeatureTest {
    @Test
    public void strongerDamageWithinHurtWindowIsSuccessful(Env env) {
        var node = this.addDamageFeature();

        try {
            var instance = this.createFlatInstance(env);
            var target = this.createEntity(instance, new Pos(0.0, 40.0, 0.0));
            var attacker = this.createEntity(instance, new Pos(0.0, 40.0, 1.0));

            assertTrue(target.damage(this.createDamage(attacker, 10.0F)));
            assertFalse(target.damage(this.createDamage(attacker, 5.0F)));
            assertTrue(target.damage(this.createDamage(attacker, 15.0F)));
            assertEquals(5.0F, target.getHealth());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void hurtWindowExpiresAfterTenTicks(Env env) {
        var node = this.addDamageFeature();

        try {
            var instance = this.createFlatInstance(env);
            var target = this.createEntity(instance, new Pos(0.0, 40.0, 0.0));
            var attacker = this.createEntity(instance, new Pos(0.0, 40.0, 1.0));

            assertTrue(target.damage(this.createDamage(attacker, 10.0F)));
            for (var tick = 0; tick < 10; tick++) {
                env.tick();
            }

            assertTrue(target.damage(this.createDamage(attacker, 5.0F)));
            assertTrue(target.getHealth() < 10.0F);
            assertTrue(target.getHealth() > 0.0F);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void fullyBlockedDamageClearsPreviousHurtAmount(Env env) {
        var node = this.addDamageFeature();

        try {
            var instance = this.createFlatInstance(env);
            var target = this.createEntity(instance, new Pos(0.0, 40.0, 0.0));
            var attacker = this.createEntity(instance, new Pos(0.0, 40.0, 1.0));

            assertTrue(target.damage(this.createDamage(attacker, 10.0F)));
            var hurtWindowEnd = target.getAliveTicks() + 10;
            while (target.getAliveTicks() < hurtWindowEnd) {
                env.tick();
            }

            target.setItemInMainHand(ItemStack.of(Material.SHIELD).with(DataComponents.BLOCKS_ATTACKS,
                    new BlocksAttacks(
                            0.0F,
                            1.0F,
                            List.of(BlocksAttacks.DamageReduction.DEFAULT),
                            BlocksAttacks.ItemDamageFunction.DEFAULT,
                            null,
                            null,
                            null
                    )));
            var targetMeta = (LivingEntityMeta) target.getEntityMeta();
            targetMeta.setActiveHand(PlayerHand.MAIN);
            targetMeta.setHandActive(true);

            assertFalse(target.damage(this.createDamage(attacker, 5.0F)));
            targetMeta.setHandActive(false);
            assertTrue(target.damage(this.createDamage(attacker, 5.0F)));
            assertTrue(target.getHealth() < 10.0F);
            assertTrue(target.getHealth() > 0.0F);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void damageEmitsOneHurtSound(Env env) {
        var node = this.addDamageFeature();

        try {
            var instance = this.createFlatInstance(env);
            var connection = env.createConnection();
            var target = connection.connect(instance, new Pos(0.0, 40.0, 0.0));
            var attacker = this.createEntity(instance, new Pos(0.0, 40.0, 1.0));
            var sounds = connection.trackIncoming(SoundEffectPacket.class);

            assertTrue(target.damage(this.createDamage(attacker, 1.0F)));
            assertEquals(1, sounds.collect().stream()
                    .filter(packet -> packet.soundEvent() == SoundEvent.ENTITY_PLAYER_HURT)
                    .count());
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addDamageFeature() {
        var node = CombatFeatures.modernVanilla().createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }

    private Instance createFlatInstance(Env env) {
        var instance = env.createFlatInstance();

        for (var chunkX = -1; chunkX <= 1; chunkX++) {
            for (var chunkZ = -1; chunkZ <= 1; chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }

        return instance;
    }

    private LivingEntity createEntity(Instance instance, Pos position) {
        var entity = new LivingEntity(EntityType.ZOMBIE);
        entity.setInstance(instance, position).join();
        entity.setHealth(20.0F);
        return entity;
    }

    private Damage createDamage(LivingEntity attacker, float amount) {
        return new Damage(DamageType.GENERIC, attacker, attacker, null, amount);
    }
}
