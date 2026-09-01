package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.fall.VanillaFallFeature;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaSmashAttackFeatureTest {
    @Test
    public void windBurstKeepsImpactPositionAndEndsGraceTime(Env env) {
        CombatEnchantments.registerAll();
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_FALL)
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_SMASH_ATTACK)
                .build();
        var smashAttackFeature = featureSet.get(FeatureType.SMASH_ATTACK);

        var instance = env.createFlatInstance();
        var attacker = env.createPlayer(instance, new Pos(0.0, 41.0, 0.0));
        attacker.setGameMode(GameMode.SURVIVAL);
        attacker.setItemInMainHand(ItemStack.of(Material.MACE).with(
                DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.WIND_BURST, 1)
        ));
        attacker.setTag(VanillaFallFeature.FALL_DISTANCE, 3.0);

        var target = new LivingEntity(EntityType.ZOMBIE);
        target.setInstance(instance, new Pos(0.0, 40.0, 1.0)).join();

        smashAttackFeature.applySmashAttack(attacker, target);

        assertTrue(attacker.hasTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        assertEquals(41.0, attacker.getTag(VanillaFallFeature.CURRENT_IMPULSE_IMPACT_Y));
        assertEquals(0, attacker.getTag(VanillaFallFeature.CURRENT_IMPULSE_CONTEXT_RESET_GRACE_TIME));
        assertEquals(0.0, attacker.getTag(VanillaFallFeature.FALL_DISTANCE));
    }
}
