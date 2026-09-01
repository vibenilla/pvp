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

	/**
	 * Reset the attack cooldown progress of the player, so it can attack again.
	 *
	 * @param player the player to reset the attack cooldown progress from
	 */
	void resetCooldownProgress(Player player);

	/**
	 * Get the attack cooldown progress of the player,
	 * a value between 0.0 and 1.0 where higher values mean more attack damage.
	 *
	 * @param player the player to get the attack cooldown progress from
	 * @return the attack cooldown progress of the player
	 */
	double getAttackCooldownProgress(Player player);

	double getAttackCooldownProgress(Player player, double adjustTicks);

	default boolean cannotAttackWith(Player player, ItemStack stack, double toleranceTicks) {
		float requiredCharge = stack.get(DataComponents.MINIMUM_ATTACK_CHARGE, 0.0F);
		if (requiredCharge <= 0.0F) return false;

		return this.getAttackCooldownProgress(player, toleranceTicks) < requiredCharge;
	}
}
