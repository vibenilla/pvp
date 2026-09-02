package io.github.togar2.pvp.entity;

import io.github.togar2.pvp.feature.effect.EffectFeature;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.color.AlphaColor;
import net.minestom.server.color.Color;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.other.AreaEffectCloudMeta;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.particle.Particle;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class AreaEffectCloud extends Entity {
    private static final float MAX_RADIUS = 32.0F;
    private static final float MIN_RADIUS = 0.5F;
    private static final Particle.EntityEffect DEFAULT_PARTICLE = Particle.ENTITY_EFFECT.withColor(new AlphaColor(-1));

    private final AreaEffectCloudMeta meta;
    private final Map<LivingEntity, Integer> victims = new HashMap<>();
    private final Set<LivingEntity> nearbyVictims = new HashSet<>();
    private final PotionContents potionContents;
    private final @Nullable Entity owner;
    private final EffectFeature effectFeature;

    private int age;
    private int duration;
    private int waitTime;
    private int reapplicationDelay;
    private int durationOnUse;
    private float radiusOnUse;
    private float radiusPerTick;

    public AreaEffectCloud(PotionContents potionContents, @Nullable Entity owner, EffectFeature effectFeature) {
        super(EntityType.AREA_EFFECT_CLOUD);

        this.meta = (AreaEffectCloudMeta) this.getEntityMeta();
        this.potionContents = potionContents;
        this.owner = owner;
        this.effectFeature = effectFeature;
        this.age = 0;
        this.duration = 600;
        this.waitTime = 10;
        this.reapplicationDelay = 20;
        this.durationOnUse = 0;
        this.radiusOnUse = -0.5F;
        this.radiusPerTick = -3.0F / this.duration;

        this.meta.setRadius(3.0F);
        this.meta.setWaiting(true);
        this.meta.setParticle(DEFAULT_PARTICLE);
        this.updateColor();
        this.setNoGravity(true);
        this.setAerodynamics(new Aerodynamics(0.0, 0.0, 0.0));
    }

    @Override
    public void tick(long time) {
        super.tick(time);

        if (this.isRemoved()) return;

        this.age++;

        if (this.age >= this.waitTime + this.duration) {
            this.discard();
            return;
        }

        var waiting = this.age < this.waitTime;
        this.meta.setWaiting(waiting);

        if (waiting) return;

        if (this.radiusPerTick != 0.0F) {
            var radius = this.meta.getRadius() + this.radiusPerTick;

            if (radius < MIN_RADIUS) {
                this.discard();
                return;
            }

            this.meta.setRadius(Math.min(MAX_RADIUS, radius));
        }

        if (this.age % 5 != 0) return;

        this.victims.entrySet().removeIf(entry -> this.age >= entry.getValue());

        if (this.effectFeature.getAllPotions(this.potionContents).isEmpty()) {
            this.victims.clear();
            return;
        }

        var instance = this.getInstance();

        if (instance == null) return;

        this.nearbyVictims.clear();
        var radius = this.meta.getRadius();
        var radiusSquared = radius * radius;
        var cloudBox = new BoundingBox(radius * 2.0, 0.5, radius * 2.0);
        var chunkRange = (int) Math.ceil((radius + 8.0) / 16.0);

        instance.getEntityTracker().nearbyEntitiesByChunkRange(
                this.position,
                chunkRange,
                EntityTracker.Target.ENTITIES,
                entity -> {

                    if (!(entity instanceof LivingEntity livingEntity)) {
                        return;
                    }

                    if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }

                    if (!cloudBox.intersectEntity(this.position, entity)) {
                        return;
                    }

                    if (this.getHorizontalDistanceSquared(entity) <= radiusSquared) {
                        this.nearbyVictims.add(livingEntity);
                    }
                }
        );

        for (var entity : this.nearbyVictims) {

            if (this.victims.containsKey(entity)) {
                continue;
            }

            this.effectFeature.addLingeringPotionEffects(entity, this.potionContents, this, this.owner);
            this.victims.put(entity, this.age + this.reapplicationDelay);

            if (this.radiusOnUse != 0.0F) {
                radius = this.meta.getRadius() + this.radiusOnUse;

                if (radius < MIN_RADIUS) {
                    this.discard();
                    return;
                }

                this.meta.setRadius(Math.min(MAX_RADIUS, radius));
            }

            if (this.durationOnUse != 0) {
                this.duration += this.durationOnUse;

                if (this.duration <= 0) {
                    this.discard();
                    return;
                }
            }
        }
    }

    private double getHorizontalDistanceSquared(Entity entity) {
        var deltaX = this.position.x() - entity.getPosition().x();
        var deltaZ = this.position.z() - entity.getPosition().z();

        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private void updateColor() {
        var color = new Color(this.effectFeature.getPotionColor(this.potionContents));
        this.meta.setParticle(DEFAULT_PARTICLE.withColor(255, color));
    }

    private void discard() {
        this.scheduler().scheduleNextProcess(this::remove);
    }
}
