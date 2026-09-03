package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.entity.projectile.WindCharge;
import io.github.togar2.pvp.events.FinalAttackEvent;
import io.github.togar2.pvp.events.PrepareAttackEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.AttackCooldownFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.enchantment.VanillaEnchantmentFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.ViewUtil;
import java.util.ArrayList;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Vanilla implementation of {@link AttackFeature}
 * <p>
 * Listens on {@link EntityAttackEvent}
 */
public class VanillaAttackFeature implements AttackFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaAttackFeature> DEFINED = new DefinedFeature<>(
        FeatureType.ATTACK, VanillaAttackFeature::new,
        FeatureType.ATTACK_COOLDOWN, FeatureType.EXHAUSTION, FeatureType.ITEM_DAMAGE,
        FeatureType.ENCHANTMENT, FeatureType.CRITICAL, FeatureType.SWEEPING, FeatureType.KNOCKBACK,
        FeatureType.SMASH_ATTACK, FeatureType.VERSION
    );

    private static final double ATTACK_RANGE_MARGIN = 3.0;
    private static final double ATTACK_CHARGE_TOLERANCE_TICKS = 5.0;
    private static final Tag<Boolean> SPRINT_ATTACK_RESET = Tag.Boolean("sprintAttackReset");

    private final FeatureConfiguration configuration;

    private AttackCooldownFeature cooldownFeature;
    private ExhaustionFeature exhaustionFeature;
    private ItemDamageFeature itemDamageFeature;
    private EnchantmentFeature enchantmentFeature;

    private CriticalFeature criticalFeature;
    private SweepingFeature sweepingFeature;
    private KnockbackFeature knockbackFeature;
    private SmashAttackFeature smashAttackFeature;

    private CombatVersion version;

    public VanillaAttackFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.cooldownFeature = this.configuration.get(FeatureType.ATTACK_COOLDOWN);
        this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
        this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
        this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
        this.criticalFeature = this.configuration.get(FeatureType.CRITICAL);
        this.sweepingFeature = this.configuration.get(FeatureType.SWEEPING);
        this.knockbackFeature = this.configuration.get(FeatureType.KNOCKBACK);
        this.smashAttackFeature = this.configuration.get(FeatureType.SMASH_ATTACK);
        this.version = this.configuration.get(FeatureType.VERSION);
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerPacketEvent.class, this::handleSprintAction);
        node.addListener(EntityAttackEvent.class, event -> {
            if (event.getEntity() instanceof Player player && player.getGameMode() != GameMode.SPECTATOR && !player.isDead()) {
                var mainHand = player.getItemInMainHand();
                if (mainHand.has(DataComponents.PIERCING_WEAPON)) return;
                if (this.cooldownFeature.cannotAttackWith(player, mainHand, ATTACK_CHARGE_TOLERANCE_TICKS)) return;

                var target = event.getTarget();
                var maxDistanceSquared = Math.pow(player.getAttributeValue(Attribute.ENTITY_INTERACTION_RANGE) + ATTACK_RANGE_MARGIN, 2);
                var eyePosition = player.getPosition().add(0.0, player.getEyeHeight(), 0.0);
                if (this.distanceSquaredToBox(eyePosition, target.getBoundingBox(), target.getPosition()) < maxDistanceSquared)
                    this.performAttack(player, target);
            }
        });
    }

    private void handleSprintAction(PlayerPacketEvent event) {
        if (!(event.getPacket() instanceof ClientEntityActionPacket packet)) {
            return;
        }

        if (packet.action() == ClientEntityActionPacket.Action.STOP_SPRINTING) {
            event.getPlayer().removeTag(SPRINT_ATTACK_RESET);
            return;
        }

        if (packet.action() != ClientEntityActionPacket.Action.START_SPRINTING) {
            return;
        }

        if (!Boolean.TRUE.equals(event.getPlayer().getTag(SPRINT_ATTACK_RESET))) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().setSprinting(false);
    }

    private double distanceSquaredToBox(Point point, BoundingBox boundingBox, Point position) {
        var minX = position.x() + boundingBox.minX();
        var maxX = position.x() + boundingBox.maxX();
        var minY = position.y() + boundingBox.minY();
        var maxY = position.y() + boundingBox.maxY();
        var minZ = position.z() + boundingBox.minZ();
        var maxZ = position.z() + boundingBox.maxZ();
        var distanceX = point.x() < minX ? minX - point.x() : Math.max(point.x() - maxX, 0.0);
        var distanceY = point.y() < minY ? minY - point.y() : Math.max(point.y() - maxY, 0.0);
        var distanceZ = point.z() < minZ ? minZ - point.z() : Math.max(point.z() - maxZ, 0.0);

        return distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ;
    }

    @Override
    public boolean performAttack(LivingEntity attacker, Entity target) {
        var prepareAttackEvent = new PrepareAttackEvent(attacker, target);
        EventDispatcher.call(prepareAttackEvent);
        if (prepareAttackEvent.isCancelled()) return false;
        AttackValues.Final attack = this.prepareAttack(attacker, target);
        if (attack == null) return false;

        if (target instanceof WindCharge windCharge && windCharge.deflect(attacker)) {
            if (attack.sounds() && attack.playSoundsOnFail()) {
                ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
                        SoundEvent.ENTITY_PLAYER_ATTACK_NODAMAGE, Sound.Source.PLAYER,
                        1.0F, 1.0F
                ), attacker);
            }

            return true;
        }

        var smashAttack = target instanceof LivingEntity
                && this.smashAttackFeature.canSmashAttack(attacker);

        var originalHealth = 0.0F;
        var damageSucceeded = false;
        if (target instanceof CrystalEntity crystal) {
            damageSucceeded = crystal.damage(new Damage(
                    attacker instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK,
                    attacker, attacker,
                    null, attack.damage()
            ));
        } else if (target instanceof LivingEntity livingTarget) {
            originalHealth = livingTarget.getHealth();
            damageSucceeded = livingTarget.damage(new Damage(
                smashAttack ? DamageType.MACE_SMASH :
                        (attacker instanceof Player ? DamageType.PLAYER_ATTACK : DamageType.MOB_ATTACK),
                attacker, attacker,
                null, attack.damage()
            ));
        }

        if (!damageSucceeded) {
            if (attack.sounds() && attack.playSoundsOnFail()) {
                ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
                    SoundEvent.ENTITY_PLAYER_ATTACK_NODAMAGE, Sound.Source.PLAYER,
                    1.0F, 1.0F
                ), attacker);
            }
            return false;
        }

        if (target instanceof CrystalEntity) {
            this.playAttackSounds(attacker, attack);

            if (attacker instanceof Player player)
                this.exhaustionFeature.addAttackExhaustion(player);

            return true;
        }

        var living = (LivingEntity) target;
        Collection<LivingEntity> affectedEntities = new ArrayList<>(List.of(living));

        var appliedKnockback = this.knockbackFeature.applyAttackKnockback(attacker, living, attack.knockback());

        if (appliedKnockback && attack.sprint() && attacker instanceof Player player) {
            player.setTag(SPRINT_ATTACK_RESET, true);
        }

        if (attack.sweeping()) {
            affectedEntities = this.sweepingFeature.applySweeping(
                    attacker, living, attack.baseDamage(), attack.cooldownProgress());
            affectedEntities.add(living);
        }

        if (smashAttack) {
            this.smashAttackFeature.applySmashAttack(attacker, living);
        } else {
            this.smashAttackFeature.applyWindBurst(attacker);
        }

        if (target instanceof CombatPlayer custom)
            custom.sendImmediateVelocityUpdate();

        this.playAttackSounds(attacker, attack);

        if (attack.critical()) attacker.sendPacketToViewersAndSelf(new EntityAnimationPacket(
            target.getEntityId(),
            EntityAnimationPacket.Animation.CRITICAL_EFFECT
        ));
        if (attack.magical()) attacker.sendPacketToViewersAndSelf(new EntityAnimationPacket(
            target.getEntityId(),
            EntityAnimationPacket.Animation.MAGICAL_CRITICAL_EFFECT
        ));

        for (var affectedEntity : affectedEntities) {
            this.enchantmentFeature.onUserDamaged(affectedEntity, attacker);
            this.enchantmentFeature.onTargetDamaged(attacker, affectedEntity);

            if (attack.fireAspect() > 0) {
                var fireTicks = attack.fireAspect() * 4 * ServerFlag.SERVER_TICKS_PER_SECOND;
                var adjustedFireTicks = this.enchantmentFeature.getFireDuration(affectedEntity, fireTicks);

                if (affectedEntity.getFireTicks() < adjustedFireTicks) {
                    affectedEntity.setTag(VanillaEnchantmentFeature.FIRE_DURATION_ALREADY_SCALED, true);
                    affectedEntity.setFireTicks(adjustedFireTicks);
                }
            }
        }

        var weapon = attacker.getItemInMainHand().get(DataComponents.WEAPON);
        if (weapon != null) {
            this.itemDamageFeature.damageEquipment(attacker, EquipmentSlot.MAIN_HAND, weapon.itemDamagePerAttack());
        }

        var damageDone = originalHealth - living.getHealth();
        if (damageDone > 2) {
            var particleCount = (int) (damageDone * 0.5);
            var targetPosition = target.getPosition();
            target.sendPacketToViewersAndSelf(new ParticlePacket(
                Particle.DAMAGE_INDICATOR, false, false,
                targetPosition.x(), targetPosition.y() + target.getBoundingBox().height() * 0.5, targetPosition.z(),
                0.1F, 0, 0.1F,
                0.2F, particleCount
            ));
        }

        if (attacker instanceof Player player)
            this.exhaustionFeature.addAttackExhaustion(player);

        return true;
    }

    private void playAttackSounds(LivingEntity attacker, AttackValues.Final attack) {
        if (!attack.sounds()) return;

        var audience = attacker.getViewersAsAudience();
        if (attacker instanceof Player player)
            audience = Audience.audience(audience, player);

        if (attack.sprint()) audience.playSound(Sound.sound(
                SoundEvent.ENTITY_PLAYER_ATTACK_KNOCKBACK, Sound.Source.PLAYER,
                1.0F, 1.0F
        ), attacker);

        if (attack.sweeping()) audience.playSound(Sound.sound(
                SoundEvent.ENTITY_PLAYER_ATTACK_SWEEP, Sound.Source.PLAYER,
                1.0F, 1.0F
        ), attacker);

        if (attack.critical()) audience.playSound(Sound.sound(
                SoundEvent.ENTITY_PLAYER_ATTACK_CRIT, Sound.Source.PLAYER,
                1.0F, 1.0F
        ), attacker);

        if (!attack.critical() && !attack.sweeping()) audience.playSound(Sound.sound(
                attack.strong() ?
                        SoundEvent.ENTITY_PLAYER_ATTACK_STRONG :
                        SoundEvent.ENTITY_PLAYER_ATTACK_WEAK,
                Sound.Source.PLAYER, 1.0F, 1.0F
        ), attacker);
    }

    protected @Nullable AttackValues.Final prepareAttack(LivingEntity attacker, Entity target) {
        var damage = (float) attacker.getAttributeValue(Attribute.ATTACK_DAMAGE);
        var magicalDamage = this.enchantmentFeature.getAttackDamage(attacker.getItemInMainHand(), target);

        var cooldownProgress = 1.0;
        if (attacker instanceof Player player) {
            cooldownProgress = this.cooldownFeature.getAttackCooldownProgress(player);
            this.cooldownFeature.resetCooldownProgress(player);
        }

        damage *= (float) (0.2 + cooldownProgress * cooldownProgress * 0.8);
        magicalDamage *= (float) cooldownProgress;

        if (target instanceof LivingEntity livingTarget) {
            damage += this.smashAttackFeature.getDamageBonus(attacker, livingTarget);
        }

        var strongAttack = cooldownProgress > 0.9;
        var sprintAttack = attacker.isSprinting() && strongAttack && !this.isSprintAttackReset(attacker);
        var knockback = this.enchantmentFeature.getKnockback(attacker);
        var fireAspect = this.enchantmentFeature.getFireAspect(attacker);

        AttackValues.PreCritical preCritical = new AttackValues.PreCritical(
            damage, magicalDamage, cooldownProgress,
            strongAttack, sprintAttack, knockback, fireAspect
        );
        AttackValues.PreSweeping preSweeping = preCritical.withCritical(
                target instanceof LivingEntity && this.criticalFeature.shouldCrit(attacker, preCritical)
        );
        AttackValues.PreSounds preSounds = preSweeping.withSweeping(this.sweepingFeature.shouldSweep(attacker, preSweeping));

        var critical = preSounds.critical();
        var sweeping = preSounds.sweeping();

        var sounds = this.version.modern();

        var finalAttackEvent = new FinalAttackEvent(
            attacker, target, sprintAttack, critical, sweeping, damage,
            magicalDamage, sounds, sounds
        );
        EventDispatcher.call(finalAttackEvent);
        if (finalAttackEvent.isCancelled()) return null;

        sprintAttack = finalAttackEvent.isSprint();
        critical = finalAttackEvent.isCritical();
        sweeping = finalAttackEvent.isSweeping();
        damage = finalAttackEvent.getBaseDamage();
        magicalDamage = finalAttackEvent.getEnchantsExtraDamage();

        if (critical) damage = this.criticalFeature.applyToDamage(damage);
        var baseDamage = damage;
        damage += magicalDamage;

        if (sprintAttack) knockback += this.version.legacy() ? 1.0 : 0.5;

        return new AttackValues.Final(
            damage, baseDamage, cooldownProgress, strongAttack, sprintAttack, knockback, critical,
            magicalDamage > 0, fireAspect, sweeping,
            finalAttackEvent.hasAttackSounds(),
            finalAttackEvent.playSoundsOnFail()
        );
    }

    private boolean isSprintAttackReset(LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return false;
        }

        return Boolean.TRUE.equals(player.getTag(SPRINT_ATTACK_RESET));
    }
}
