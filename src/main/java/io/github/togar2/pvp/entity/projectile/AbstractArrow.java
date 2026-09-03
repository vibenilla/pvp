package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.events.PickupEntityEvent;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.enchantment.VanillaEnchantmentFeature;
import io.github.togar2.pvp.utils.EntityUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.CollectItemPacket;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractArrow extends CustomEntityProjectile {
    private static final double ARROW_BASE_DAMAGE = 2.0;

    protected int pickupDelay;
    protected int stuckTime;
    protected PickupMode pickupMode = PickupMode.DISALLOWED;
    protected int ticks;
    private double baseDamage = ARROW_BASE_DAMAGE;
    private double damageBonus = 0.0;
    private double knockback;
    private ItemStack weaponItem = ItemStack.AIR;
    private SoundEvent soundEvent = getDefaultSound();

    private final Set<Integer> piercingIgnore = new HashSet<>();
    private int fireTicksLeft = 0;

    protected final EnchantmentFeature enchantmentFeature;

    public AbstractArrow(@Nullable Entity shooter, @NotNull EntityType entityType,
                         EnchantmentFeature enchantmentFeature) {
        super(shooter, entityType);
        this.enchantmentFeature = enchantmentFeature;

        if (shooter instanceof Player) {
            pickupMode = ((Player) shooter).getGameMode() == GameMode.CREATIVE ? PickupMode.CREATIVE_ONLY : PickupMode.ALLOWED;
        }
    }

    @Override
    public void update(long time) {
        if (this.isStuck()) {
            this.stuckTime++;
        } else {
            this.stuckTime = 0;
        }

        if (this.pickupDelay > 0) {
            this.pickupDelay--;
        }

        if (this.fireTicksLeft > 0) {
            if (this.entityMeta.isOnFire()) {
                this.fireTicksLeft--;
                if (this.fireTicksLeft == 0) {
                    this.entityMeta.setOnFire(false);
                }
            } else {
                this.fireTicksLeft = 0;
            }
        }

        // Pickup
        if (canBePickedUp(null)) {
            instance.getEntityTracker().nearbyEntities(position, 5, EntityTracker.Target.PLAYERS,
                    player -> {
                        if (!player.canPickupItem()) return;

                        // Do not pickup if not visible
                        if (!isViewer(player))
                            return;

                        if (isRemoved() || !canBePickedUp(player))
                            return;

                        if (player.getBoundingBox().expand(1, 0.5F, 1)
                                .intersectEntity(player.getPosition(), this)) {
                            var event = new PickupEntityEvent(player, this);
                            EventDispatcher.callCancellable(event, () -> {
                                if (pickup(player)) {
                                    player.sendPacketToViewersAndSelf(new CollectItemPacket(
                                            getEntityId(), player.getEntityId(), 1
                                    ));
                                    remove();
                                }
                            });
                        }
                    });
        }

        if (this.isOnFire()) {
            var currentInstance = this.getInstance();

            if (currentInstance != null && (FluidUtil.isTouchingWater(this) || FluidUtil.isInRain(this))) {
                this.fireTicksLeft = 0;
                this.entityMeta.setOnFire(false);
            }
        }

        if (this.isStuck()) this.tickRemoval();
    }

    public void setFireTicksLeft(int fireTicksLeft) {
        this.fireTicksLeft = fireTicksLeft;
        if (fireTicksLeft > 0) entityMeta.setOnFire(true);
    }

    protected void tickRemoval() {
        ticks++;
        if (ticks >= 1200) {
            remove();
        }
    }

    @Override
    public void onUnstuck() {
        ((AbstractArrowMeta) getEntityMeta()).setInGround(false);
        var random = ThreadLocalRandom.current();
        setVelocity(velocity.mul(
                random.nextDouble() * 0.2,
                random.nextDouble() * 0.2,
                random.nextDouble() * 0.2
        ));
        ticks = 0;
    }

    @Override
    protected boolean canHit(Entity entity) {
        return (super.canHit(entity) || entity instanceof CrystalEntity) && !piercingIgnore.contains(entity.getEntityId());
    }

    @Override
    public boolean onHit(@NotNull Entity entity) {
        if (piercingIgnore.contains(entity.getEntityId())) return false;

        var random = ThreadLocalRandom.current();

        var movementSpeed = getVelocity().length() / ServerFlag.SERVER_TICKS_PER_SECOND;
        var damage = (int) Math.ceil(Math.clamp(
                movementSpeed * baseDamage, 0.0, 2.147483647E9));

        if (getPiercingLevel() > 0) {
            if (piercingIgnore.size() >= getPiercingLevel() + 1) {
                return true;
            }

            piercingIgnore.add(entity.getEntityId());
        }

        if (isCritical()) {
            var randomDamage = random.nextInt(damage / 2 + 2);
            damage = (int) Math.min(randomDamage + damage, 2147483647L);
        }

        damage = (int) Math.min(damage + this.damageBonus, 2.147483647E9);

        var shooter = getShooter();
        var damageObj = new Damage(
                DamageType.ARROW,
                this, Objects.requireNonNullElse(shooter, this),
                null, damage
        );
        var position = this.getPosition();

        if (entity instanceof CrystalEntity crystal) {
            if (crystal.damage(damageObj)) {
                if (!isSilent()) {
                    getViewersAsAudience().playSound(Sound.sound(
                            getSound(), Sound.Source.NEUTRAL,
                            1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F)
                    ), position.x(), position.y(), position.z());
                }

                return getPiercingLevel() <= 0;
            } else {
                setVelocity(getVelocity().mul(-0.5 * 0.2));

                return false;
            }
        }

        if (!(entity instanceof LivingEntity living)) return false;

        if (living.damage(damageObj)) {
            if (entity.getEntityType() == EntityType.ENDERMAN) return false;

            if (isOnFire()) {
                this.setFireTicks(living, 5 * ServerFlag.SERVER_TICKS_PER_SECOND);
            }

            if (getPiercingLevel() <= 0) {
                living.setArrowCount(living.getArrowCount() + 1);
            }

            if (knockback > 0) {
                var knockbackResistance = Math.max(0.0, 1.0 - living.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE));
                var horizontal = this.getVelocity()
                        .mul(1, 0, 1)
                        .normalize().mul(knockback * 0.6 * knockbackResistance);

                if (horizontal.lengthSquared() > 0) {
                    var knockbackVec = new Vec(horizontal.x(), 0.1, horizontal.z())
                            .mul(ServerFlag.SERVER_TICKS_PER_SECOND);
                    living.setVelocity(living.getVelocity().add(knockbackVec));
                }
            }

            if (shooter instanceof LivingEntity livingShooter) {
                enchantmentFeature.onUserDamaged(living, livingShooter);
                enchantmentFeature.onTargetDamaged(livingShooter, living, this.weaponItem);
            }

            onHurt(living);

            if (living != shooter && living instanceof Player
                    && shooter instanceof Player shooterPlayer && !isSilent()) {
                shooterPlayer.sendPacket(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.ARROW_HIT_PLAYER, 0.0F));
            }

            if (!isSilent()) {
                getViewersAsAudience().playSound(Sound.sound(
                        getSound(), Sound.Source.NEUTRAL,
                        1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F)
                ), position.x(), position.y(), position.z());
            }

            return getPiercingLevel() <= 0;
        } else {
            setVelocity(getVelocity().mul(-0.1));
            refreshPosition(position.withYaw(position.yaw() + 170.0F + 20.0F * ThreadLocalRandom.current().nextFloat()));

            if (getVelocity().lengthSquared() < 1.0E-7) {
                if (pickupMode == PickupMode.ALLOWED) {
                    EntityUtil.spawnItemAtLocation(this, getPickupItem(), 0.1);
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onStuck() {
        if (!isSilent()) {
            var random = ThreadLocalRandom.current();
            var position = this.getPosition();
            getViewersAsAudience().playSound(Sound.sound(
                    getSound(), Sound.Source.NEUTRAL,
                    1.0F, 1.2F / (random.nextFloat() * 0.2F + 0.9F)
            ), position.x(), position.y(), position.z());
        }

        pickupDelay = 7;
        ((AbstractArrowMeta) getEntityMeta()).setInGround(true);
        setCritical(false);
        setPiercingLevel((byte) 0);
        setSound(SoundEvent.ENTITY_ARROW_HIT);
        piercingIgnore.clear();

        return false;
    }

    public boolean canBePickedUp(@Nullable Player player) {
        if (!(onGround || hasNoGravity()) || pickupDelay > 0) {
            return false;
        }

        return switch (pickupMode) {
            case ALLOWED -> true;
            case CREATIVE_ONLY -> player == null || player.getGameMode() == GameMode.CREATIVE;
            default -> false;
        };
    }

    public boolean pickup(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getInventory().addItemStack(getPickupItem());
    }

    protected abstract ItemStack getPickupItem();

    protected void onHurt(LivingEntity entity) {
    }

    public SoundEvent getSound() {
        return soundEvent;
    }

    public void setSound(SoundEvent soundEvent) {
        this.soundEvent = soundEvent;
    }

    protected SoundEvent getDefaultSound() {
        return SoundEvent.ENTITY_ARROW_HIT;
    }

    @Override
    protected double getWaterInertia() {
        return 0.6;
    }

    public double getKnockback() {
        return knockback;
    }

    public void setKnockback(double knockback) {
        this.knockback = knockback;
    }

    public ItemStack getWeaponItem() {
        return this.weaponItem;
    }

    public void setWeaponItem(ItemStack weaponItem) {
        this.weaponItem = weaponItem;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public double getDamageBonus() {
        return this.damageBonus;
    }

    public void setDamageBonus(double damageBonus) {
        this.damageBonus = damageBonus;
    }

    public boolean isCritical() {
        return ((AbstractArrowMeta) getEntityMeta()).isCritical();
    }

    public void setCritical(boolean critical) {
        ((AbstractArrowMeta) getEntityMeta()).setCritical(critical);
    }

    public byte getPiercingLevel() {
        return ((AbstractArrowMeta) getEntityMeta()).getPiercingLevel();
    }

    public void setPiercingLevel(byte piercingLevel) {
        ((AbstractArrowMeta) getEntityMeta()).setPiercingLevel(piercingLevel);
    }

    public boolean isNoClip() {
        return ((AbstractArrowMeta) getEntityMeta()).isNoClip();
    }

    public void setNoClip(boolean noClip) {
        ((AbstractArrowMeta) getEntityMeta()).setNoClip(noClip);
        super.hasPhysics = !noClip;
        super.noClip = noClip;
    }

    public PickupMode getPickupMode() {
        return pickupMode;
    }

    public void setPickupMode(PickupMode pickupMode) {
        this.pickupMode = pickupMode;
    }

    public enum PickupMode {
        DISALLOWED,
        ALLOWED,
        CREATIVE_ONLY
    }

    protected void setFireTicks(LivingEntity entity, int fireTicks) {
        var adjustedFireTicks = this.enchantmentFeature.getFireDuration(entity, fireTicks);

        if (entity.getFireTicks() < adjustedFireTicks) {
            entity.setTag(VanillaEnchantmentFeature.FIRE_DURATION_ALREADY_SCALED, true);
            entity.setFireTicks(adjustedFireTicks);
        }
    }
}
