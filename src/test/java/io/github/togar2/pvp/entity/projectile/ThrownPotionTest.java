package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.potion.effect.CombatPotionEffects;
import io.github.togar2.pvp.potion.item.CombatPotionTypes;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.potion.PotionType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnvTest
public final class ThrownPotionTest {
    @Test
    public void splashStrengthUsesTheGapToTheHitbox(Env env) {
        CombatPotionEffects.registerAll();
        CombatPotionTypes.registerAll();
        var effectFeature = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_EFFECT)
                .build()
                .get(FeatureType.EFFECT);

        var instance = env.createFlatInstance();
        var player = env.createPlayer(instance, new Pos(8.0, 40.0, 8.0));
        player.setGameMode(GameMode.SURVIVAL);

        var potion = new ThrownPotion(null, effectFeature, false);
        potion.setItem(ItemStack.of(Material.SPLASH_POTION).with(
                DataComponents.POTION_CONTENTS, new PotionContents(PotionType.HARMING)
        ));
        potion.setInstance(instance, new Pos(8.0, 43.3, 8.0)).join();
        potion.splash(null);

        assertEquals(16.0F, player.getHealth());
    }
}
