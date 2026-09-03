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

    boolean canSmashAttack(LivingEntity attacker);

    float getDamageBonus(LivingEntity attacker, LivingEntity target);

    void applySmashAttack(LivingEntity attacker, LivingEntity target);

    void applyWindBurst(LivingEntity attacker);
}
