package io.github.togar2.pvp.damage;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.registry.RegistryKey;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class DamageTypeInfoTest {
    @Test
    public void difficultyScalingComesFromTheRegistry(Env env) {
        var instance = env.createFlatInstance();
        var zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setInstance(instance, new Pos(0.0, 40.0, 0.0)).join();
        var player = env.createPlayer(instance, new Pos(0.0, 40.0, 2.0));

        var mobAttack = DamageTypeInfo.of(DamageType.MOB_ATTACK);
        assertTrue(mobAttack.shouldScaleWithDifficulty(new Damage(DamageType.MOB_ATTACK, zombie, zombie, null, 1.0F)));
        assertFalse(mobAttack.shouldScaleWithDifficulty(new Damage(DamageType.MOB_ATTACK, player, player, null, 1.0F)));

        var arrow = DamageTypeInfo.of(DamageType.ARROW);
        assertTrue(arrow.shouldScaleWithDifficulty(new Damage(DamageType.ARROW, zombie, zombie, null, 1.0F)));

        var explosion = DamageTypeInfo.of(DamageType.EXPLOSION);
        assertTrue(explosion.shouldScaleWithDifficulty(new Damage(DamageType.EXPLOSION, null, null, null, 1.0F)));

        var playerAttack = DamageTypeInfo.of(DamageType.PLAYER_ATTACK);
        assertFalse(playerAttack.shouldScaleWithDifficulty(new Damage(DamageType.PLAYER_ATTACK, player, player, null, 1.0F)));

        assertTrue(DamageTypeInfo.of(RegistryKey.unsafeOf("minecraft:sulfur_cube_hot")).fire());
    }
}
