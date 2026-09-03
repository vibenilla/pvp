package io.github.togar2.pvp.feature.knockback;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;

/**
 * Combat feature which handles different types of knockback.
 */
public interface KnockbackFeature extends CombatFeature {
    KnockbackFeature NO_OP = new KnockbackFeature() {
        @Override
        public boolean applyDamageKnockback(Damage damage, LivingEntity target) {
            return false;
        }

        @Override
        public boolean applyAttackKnockback(LivingEntity attacker, LivingEntity target, double knockback) {
            return false;
        }

        @Override
        public boolean applySweepingKnockback(LivingEntity attacker, LivingEntity target) {
            return false;
        }
    };

    boolean applyDamageKnockback(Damage damage, LivingEntity target);

    default boolean applyDamageKnockback(Damage damage, LivingEntity target, boolean blocked) {
        return this.applyDamageKnockback(damage, target);
    }

    boolean applyAttackKnockback(LivingEntity attacker, LivingEntity target, double knockback);

    boolean applySweepingKnockback(LivingEntity attacker, LivingEntity target);
}
