package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.utils.RegistryTags;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Vanilla implementation of {@link SweepingFeature}
 */
public class VanillaSweepingFeature implements SweepingFeature {
    public static final DefinedFeature<VanillaSweepingFeature> DEFINED = new DefinedFeature<>(
            FeatureType.SWEEPING, VanillaSweepingFeature::new,
            FeatureType.ENCHANTMENT, FeatureType.KNOCKBACK
    );

    private final FeatureConfiguration configuration;

    private EnchantmentFeature enchantmentFeature;
    private KnockbackFeature knockbackFeature;

    public VanillaSweepingFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
        this.knockbackFeature = this.configuration.get(FeatureType.KNOCKBACK);
    }

    @Override
    public boolean shouldSweep(LivingEntity attacker, AttackValues.PreSweeping values) {
        if (!values.strong() || values.critical() || values.sprint() || !attacker.isOnGround()) return false;

        var previousPosition = attacker.getPreviousPosition();
        var currentPosition = attacker.getPosition();
        var movementX = currentPosition.x() - previousPosition.x();
        var movementZ = currentPosition.z() - previousPosition.z();
        var horizontalMovementSquared = movementX * movementX + movementZ * movementZ;
        var maxMovement = attacker.getAttributeValue(Attribute.MOVEMENT_SPEED) * 2.5;
        if (horizontalMovementSquared >= maxMovement * maxMovement) return false;

        return RegistryTags.contains(RegistryTags.SWORDS, attacker.getItemInMainHand().material());
    }

    @Override
    public float getSweepingDamage(LivingEntity attacker, float damage) {
        return 1.0F + (float) attacker.getAttributeValue(Attribute.SWEEPING_DAMAGE_RATIO) * damage;
    }

    @Override
    public Collection<LivingEntity> applySweeping(LivingEntity attacker, LivingEntity target, float damage) {
        return this.applySweeping(attacker, target, damage, 1.0);
    }

    @Override
    public Collection<LivingEntity> applySweeping(LivingEntity attacker, LivingEntity target, float damage,
                                                  double cooldownProgress) {
        var sweepingDamage = this.getSweepingDamage(attacker, damage);

        var affectedEntities = new ArrayList<LivingEntity>();
        var boundingBox = target.getBoundingBox().growSymmetrically(1.0, 0.25, 1.0);
        assert target.getInstance() != null;
        for (var nearbyEntity : target.getInstance().getNearbyEntities(target.getPosition(), 3)) {
            var affectedEntity = this.applySweepingToEntity(attacker, target, sweepingDamage, cooldownProgress,
                    boundingBox, nearbyEntity);

            if (affectedEntity != null) {
                affectedEntities.add(affectedEntity);
            }
        }

        var position = attacker.getPosition();
        var x = -Math.sin(Math.toRadians(position.yaw()));
        var z = Math.cos(Math.toRadians(position.yaw()));

        attacker.sendPacketToViewersAndSelf(new ParticlePacket(
                Particle.SWEEP_ATTACK, false,false,
                position.x() + x, position.y() + attacker.getBoundingBox().height() * 0.5, position.z() + z,
                (float) x, 0, (float) z,
                0, 0
        ));

        return affectedEntities;
    }

    private LivingEntity applySweepingToEntity(LivingEntity attacker, LivingEntity target, float sweepingDamage,
                                               double cooldownProgress, BoundingBox boundingBox, Entity nearbyEntity) {
        if (nearbyEntity == target || nearbyEntity == attacker) return null;
        if (!(nearbyEntity instanceof LivingEntity living)) return null;
        if (this.isMarkerArmorStand(nearbyEntity)) return null;
        if (!boundingBox.intersectEntity(target.getPosition(), nearbyEntity)) return null;
        if (attacker.getPosition().distanceSquared(nearbyEntity.getPosition()) >= 9.0) return null;

        var currentDamage = (sweepingDamage + this.enchantmentFeature.getAttackDamage(
                attacker.getItemInMainHand(), living)) * (float) cooldownProgress;

        var damaged = living.damage(new Damage(
                attacker instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK,
                attacker, attacker,
                null, currentDamage
        ));

        if (!damaged) return null;

        this.knockbackFeature.applySweepingKnockback(attacker, living);

        return living;
    }

    private boolean isMarkerArmorStand(Entity entity) {
        return entity.getEntityType() == EntityType.ARMOR_STAND
                && entity.getEntityMeta() instanceof ArmorStandMeta armorStandMeta
                && armorStandMeta.isMarker();
    }
}
