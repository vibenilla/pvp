package io.github.togar2.pvp.events;

import io.github.togar2.pvp.feature.knockback.KnockbackSettings;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an entity gets knocked back by another entity.
 * This event does not apply simply when {@code Entity.takeKnockback()} is called,
 * but only when an entity is attacked by another entity which causes the knockback.
 * <br><br>
 * You should be aware that when the attacker has a knockback weapon, this event will be called twice:
 * once for the default damage knockback, once for the extra knockback.
 * <br>
 * When the attack was a sweeping attack, this event is also called twice for the affected entities:
 * once for the extra sweeping knockback, once for the default knockback.
 * <br><br>
 * You can determine which type of knockback this is by using {@link #getKnockbackType()}.
 */
public class EntityKnockbackEvent implements EntityInstanceEvent, CancellableEvent {

    private final Entity entity;
    private final Entity attacker;
    private final KnockbackType knockbackType;
    private AnimationType animationType;
    private KnockbackSettings settings = KnockbackSettings.DEFAULT;

    private boolean cancelled;

    public EntityKnockbackEvent(@NotNull Entity entity, @NotNull Entity attacker,
                                @NotNull KnockbackType knockbackType, @NotNull AnimationType animationType) {
        this.entity = entity;
        this.attacker = attacker;
        this.knockbackType = knockbackType;
        this.animationType = animationType;
    }

    @NotNull
    @Override
    public Entity getEntity() {
        return this.entity;
    }

    @NotNull
    public Entity getAttacker() {
        return this.attacker;
    }

    public KnockbackType getKnockbackType() {
        return this.knockbackType;
    }

    public AnimationType getAnimationType() {
        return this.animationType;
    }

    public void setAnimationType(@NotNull AnimationType animationType) {
        this.animationType = animationType;
    }

    public KnockbackSettings getSettings() {
        return this.settings;
    }

    public void setSettings(KnockbackSettings settings) {
        this.settings = settings;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    public enum KnockbackType {
        DAMAGE,
        BLOCKED_DAMAGE,
        ATTACK,
        SWEEPING
    }

    public enum AnimationType {
        DIRECTIONAL,
        FIXED
    }
}
