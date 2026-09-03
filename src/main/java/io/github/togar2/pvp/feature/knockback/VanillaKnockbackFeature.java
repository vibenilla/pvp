package io.github.togar2.pvp.feature.knockback;

import io.github.togar2.pvp.events.EntityKnockbackEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.network.packet.server.play.HitAnimationPacket;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link KnockbackFeature}
 */
public class VanillaKnockbackFeature implements KnockbackFeature {
    public static final DefinedFeature<VanillaKnockbackFeature> DEFINED = new DefinedFeature<>(
            FeatureType.KNOCKBACK, VanillaKnockbackFeature::new,
            FeatureType.VERSION
    );

    private final FeatureConfiguration configuration;

    private CombatVersion version;

    public VanillaKnockbackFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.version = this.configuration.get(FeatureType.VERSION);
    }

    @Override
    public boolean applyDamageKnockback(Damage damage, LivingEntity target) {
        return this.applyDamageKnockback(damage, target, false);
    }

    @Override
    public boolean applyDamageKnockback(Damage damage, LivingEntity target, boolean blocked) {
        var attacker = damage.getAttacker();
        var source = damage.getSource();

        var dx = attacker.getPosition().x() - target.getPosition().x();
        var dz = attacker.getPosition().z() - target.getPosition().z();

        var random = ThreadLocalRandom.current();
        while (dx * dx + dz * dz < 1.0E-5) {
            dx = random.nextDouble(-1, 1) * 0.01;
            dz = random.nextDouble(-1, 1) * 0.01;
        }

        return this.applyKnockback(
                target, attacker, source,
                blocked ? EntityKnockbackEvent.KnockbackType.BLOCKED_DAMAGE : EntityKnockbackEvent.KnockbackType.DAMAGE, 0,
                dx, dz, this.version.legacy()
        );
    }

    @Override
    public boolean applyAttackKnockback(LivingEntity attacker, LivingEntity target, double knockback) {
        if (knockback <= 0.0) return false;

        if (this.version.legacy() && attacker instanceof CombatPlayer custom)
            custom.afterSprintAttack();

        var dx = Math.sin(Math.toRadians(attacker.getPosition().yaw()));
        var dz = -Math.cos(Math.toRadians(attacker.getPosition().yaw()));

        if (!this.applyKnockback(
                target, attacker, attacker,
                EntityKnockbackEvent.KnockbackType.ATTACK, knockback,
                dx, dz, this.version.legacy()
        )) return false;

        if (this.version.modern() && attacker instanceof CombatPlayer custom)
            custom.afterSprintAttack();

        attacker.setSprinting(false);
        return true;
    }

    @Override
    public boolean applySweepingKnockback(LivingEntity attacker, LivingEntity target) {
        var dx = Math.sin(Math.toRadians(attacker.getPosition().yaw()));
        var dz = -Math.cos(Math.toRadians(attacker.getPosition().yaw()));

        return this.applyKnockback(
                target, attacker, null,
                EntityKnockbackEvent.KnockbackType.SWEEPING, 0,
                dx, dz, this.version.legacy()
        );
    }

    public record KnockbackValues(
            Vec horizontalModifier,
            double vertical, double verticalLimit,
            EntityKnockbackEvent.AnimationType animationType
    ) {}

    protected @Nullable KnockbackValues prepareKnockback(LivingEntity target, Entity attacker, @Nullable Entity source,
                                                         EntityKnockbackEvent.KnockbackType type, double extraKnockback,
                                                         double dx, double dz, boolean legacy) {
        var animationType = legacy
                ? EntityKnockbackEvent.AnimationType.FIXED
                : type == EntityKnockbackEvent.KnockbackType.DAMAGE
                        ? EntityKnockbackEvent.AnimationType.DIRECTIONAL
                        : EntityKnockbackEvent.AnimationType.FIXED;
        var knockbackEvent = new EntityKnockbackEvent(target, source == null ? attacker : source, type, animationType);
        EventDispatcher.call(knockbackEvent);
        if (knockbackEvent.isCancelled()) return null;

        var settings = knockbackEvent.getSettings();

        var kbResistance = target.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
        double horizontal, vertical;
        if (extraKnockback <= 0.0) {
            horizontal = settings.horizontal();
            vertical = settings.vertical();
        } else if (legacy) {
            horizontal = settings.extraHorizontal() * extraKnockback;
            vertical = settings.extraVertical() * extraKnockback;
        } else {
            horizontal = extraKnockback * ServerFlag.SERVER_TICKS_PER_SECOND;
            vertical = extraKnockback * ServerFlag.SERVER_TICKS_PER_SECOND;
        }

        horizontal *= (1 - kbResistance);
        vertical *= (1 - kbResistance);
        if (horizontal <= 0 && vertical <= 0) return null;

        var horizontalModifier = new Vec(dx, dz).normalize().mul(horizontal);
        return new KnockbackValues(horizontalModifier, vertical, settings.verticalLimit(), knockbackEvent.getAnimationType());
    }

    protected boolean applyKnockback(LivingEntity target, Entity attacker, @Nullable Entity source,
                                     EntityKnockbackEvent.KnockbackType type, double extraKnockback,
                                     double dx, double dz, boolean legacy) {
        var values = this.prepareKnockback(target, attacker, source, type, extraKnockback, dx, dz, legacy);
        if (values == null) return false;

        var velocity = target.getVelocity();
        if (legacy && type == EntityKnockbackEvent.KnockbackType.ATTACK) {
            target.setVelocity(velocity.add(
                    -values.horizontalModifier().x(),
                    values.vertical(),
                    -values.horizontalModifier().z()
            ));
        } else {
            target.setVelocity(new Vec(
                    velocity.x() / 2 - values.horizontalModifier().x(),
                    target.isOnGround() ? Math.min(values.verticalLimit(), velocity.y() / 2 + values.vertical()) : velocity.y(),
                    velocity.z() / 2 - values.horizontalModifier().z()
            ));
        }

        if (values.animationType() == EntityKnockbackEvent.AnimationType.DIRECTIONAL) {
            if (target instanceof Player player) {
                this.sendDirectionalEvent(player, dx, dz);
            }
        }

        return true;
    }

    protected void sendDirectionalEvent(Player player, double dx, double dz) {
        var hurtDir = (float) (Math.toDegrees(Math.atan2(dz, dx)) - player.getPosition().yaw());
        player.sendPacket(new HitAnimationPacket(player.getEntityId(), hurtDir));
    }
}
