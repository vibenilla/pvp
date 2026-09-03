package io.github.togar2.pvp.feature.fall;

import io.github.togar2.pvp.feature.CombatFeature;
import net.minestom.server.entity.LivingEntity;

/**
 * Combat feature which manages the fall distance and fall damage of entities.
 * It may also apply this damage when needed.
 */
public interface FallFeature extends CombatFeature {
    FallFeature NO_OP = new FallFeature() {
        @Override
        public int getFallDamage(LivingEntity entity, double fallDistance) {
            return 0;
        }

        @Override
        public double getFallDistance(LivingEntity entity) {
            return 0;
        }

        @Override
        public void resetFallDistance(LivingEntity entity) {}

        @Override
        public void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles) {}

        @Override
        public void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity) {}

        @Override
        public void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity, double impactY) {}

        @Override
        public void clearCurrentImpulseContext(LivingEntity entity) {}

        @Override
        public void resetPostImpulseGraceTime(LivingEntity entity) {}

        @Override
        public void applyPostImpulseGraceTime(LivingEntity entity, int ticks) {}
    };

    int getFallDamage(LivingEntity entity, double fallDistance);

    double getFallDistance(LivingEntity entity);

    void resetFallDistance(LivingEntity entity);

    void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles);

    void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity);

    void setIgnoreFallDamageFromCurrentImpulse(LivingEntity entity, double impactY);

    void clearCurrentImpulseContext(LivingEntity entity);

    void resetPostImpulseGraceTime(LivingEntity entity);

    void applyPostImpulseGraceTime(LivingEntity entity, int ticks);
}
