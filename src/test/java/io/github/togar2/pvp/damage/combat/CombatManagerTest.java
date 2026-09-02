package io.github.togar2.pvp.damage.combat;

import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnvTest
public final class CombatManagerTest {
    @Test
    public void lastPlayerThatHurtTheVictimGetsTheKillCredit(Env env) {
        var instance = env.createFlatInstance();
        var victim = env.createPlayer(instance, new Pos(8.0, 41.0, 8.0));
        var attacker = env.createPlayer(instance, new Pos(9.0, 41.0, 8.0));
        var zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setInstance(instance, new Pos(8.0, 41.0, 9.0)).join();
        var manager = new CombatManager(victim);

        this.record(manager, new Damage(DamageType.PLAYER_ATTACK, attacker, attacker, null, 2.0F));
        this.record(manager, new Damage(DamageType.MOB_ATTACK, zombie, zombie, null, 9.0F));
        this.record(manager, new Damage(DamageType.GENERIC, null, null, null, 20.0F));

        var message = assertInstanceOf(TranslatableComponent.class, manager.getDeathMessage());
        assertEquals("death.attack.generic.player", message.key());
        var hover = message.arguments().get(1).asComponent().hoverEvent();
        assertNotNull(hover);
        assertEquals(attacker.getUuid(), assertInstanceOf(HoverEvent.ShowEntity.class, hover.value()).id());
    }

    @Test
    public void lastMobGetsTheCreditWithoutAPlayer(Env env) {
        var instance = env.createFlatInstance();
        var victim = env.createPlayer(instance, new Pos(8.0, 41.0, 8.0));
        var zombie = new LivingEntity(EntityType.ZOMBIE);
        zombie.setInstance(instance, new Pos(8.0, 41.0, 9.0)).join();
        var manager = new CombatManager(victim);

        this.record(manager, new Damage(DamageType.MOB_ATTACK, zombie, zombie, null, 9.0F));
        this.record(manager, new Damage(DamageType.GENERIC, null, null, null, 20.0F));

        var message = assertInstanceOf(TranslatableComponent.class, manager.getDeathMessage());
        assertEquals("death.attack.generic.player", message.key());
    }

    private void record(CombatManager manager, Damage damage) {
        var attacker = damage.getAttacker();
        manager.recordDamage(attacker == null ? -1 : attacker.getEntityId(), damage, FallFeature.NO_OP, PlayerStateFeature.NO_OP);
    }
}
