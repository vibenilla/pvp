package io.github.togar2.pvp.feature.cooldown;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;

/**
 * Combat feature used to manage a players attack cooldown.
 */
public interface AttackCooldownFeature extends CombatFeature {
    AttackCooldownFeature NO_OP = new AttackCooldownFeature() {
        @Override
        public void resetCooldownProgress(Player player) {
        }

        @Override
        public double getAttackCooldownProgress(Player player) {
            return 1.0;
        }

        @Override
        public double getAttackCooldownProgress(Player player, double adjustTicks) {
            return 1.0;
        }
    };

    void resetCooldownProgress(Player player);

    double getAttackCooldownProgress(Player player);

    double getAttackCooldownProgress(Player player, double adjustTicks);

    default boolean cannotAttackWith(Player player, ItemStack stack, double toleranceTicks) {
        var requiredCharge = stack.get(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
        if (requiredCharge <= 0.0F) return false;

        return this.getAttackCooldownProgress(player, toleranceTicks) < requiredCharge;
    }
}
