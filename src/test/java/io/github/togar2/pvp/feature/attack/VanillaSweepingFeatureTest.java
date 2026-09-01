package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaSweepingFeatureTest {
    @Test
    public void sweepReachesOneBlockAroundTheTarget(Env env) {
        CombatEnchantments.registerAll();
        var sweepingFeature = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_KNOCKBACK)
                .add(CombatFeatures.VANILLA_SWEEPING)
                .build()
                .get(FeatureType.SWEEPING);

        var instance = env.createFlatInstance();
        var attacker = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
        attacker.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
        var target = new LivingEntity(EntityType.ZOMBIE);
        target.setInstance(instance, new Pos(0.0, 40.0, 1.5)).join();
        var nearby = new LivingEntity(EntityType.ZOMBIE);
        nearby.setInstance(instance, new Pos(1.4, 40.0, 1.5)).join();

        var affected = sweepingFeature.applySweeping(attacker, target, 7.0F);

        assertTrue(affected.contains(nearby));
    }
}
