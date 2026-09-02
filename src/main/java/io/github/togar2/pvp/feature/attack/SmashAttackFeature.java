package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.LivingEntity;

/**
 * Combat feature which handles the mace smash attack mechanic.
 */
public interface SmashAttackFeature extends CombatFeature {
	SmashAttackFeature NO_OP = new SmashAttackFeature() {
		@Override
		public boolean canSmashAttack(LivingEntity attacker) {
			return false;
		}

		@Override
		public float getDamageBonus(LivingEntity attacker, LivingEntity target) {
			return 0.0F;
		}

		@Override
		public void applySmashAttack(LivingEntity attacker, LivingEntity target) {}

		@Override
		public void applyWindBurst(LivingEntity attacker) {}
	};

	/**
	 * Whether the attacker is currently performing a smash attack with the mace.
	 *
	 * @param attacker the attacking entity
	 * @return whether the attacker is doing a smash attack
	 */
	boolean canSmashAttack(LivingEntity attacker);

	/**
	 * Returns the bonus damage that the smash attack adds based on the attacker's fall distance.
	 *
	 * @param attacker the attacking entity
	 * @param target the target entity
	 * @return the bonus damage amount
	 */
	float getDamageBonus(LivingEntity attacker, LivingEntity target);

	/**
	 * Applies the smash attack effects: knockback ring, sounds, fall reset.
	 *
	 * @param attacker the attacking entity
	 * @param target the target entity
	 */
	void applySmashAttack(LivingEntity attacker, LivingEntity target);

	void applyWindBurst(LivingEntity attacker);
}
