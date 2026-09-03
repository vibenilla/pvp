package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.entity.projectile.AbstractArrow;
import io.github.togar2.pvp.entity.projectile.Arrow;
import io.github.togar2.pvp.entity.projectile.SpectralArrow;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.effect.EffectFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemAnimation;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link BowFeature}
 */
public class VanillaBowFeature implements BowFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaBowFeature> DEFINED = new DefinedFeature<>(
            FeatureType.BOW, VanillaBowFeature::new,
            FeatureType.ITEM_DAMAGE, FeatureType.EFFECT, FeatureType.ENCHANTMENT, FeatureType.PROJECTILE_ITEM,
            FeatureType.PLAYER_STATE
    );

    private final FeatureConfiguration configuration;

    private PlayerStateFeature playerStateFeature;

    private ItemDamageFeature itemDamageFeature;
    private EffectFeature effectFeature;
    private EnchantmentFeature enchantmentFeature;
    private ProjectileItemFeature projectileItemFeature;

    public VanillaBowFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
        this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
        this.effectFeature = this.configuration.get(FeatureType.EFFECT);
        this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
        this.projectileItemFeature = this.configuration.get(FeatureType.PROJECTILE_ITEM);
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerBeginItemUseEvent.class, event -> {
            if (event.getAnimation() == ItemAnimation.BOW) {
                if (event.getPlayer().getGameMode() != GameMode.CREATIVE
                        && this.projectileItemFeature.getBowProjectile(event.getPlayer()) == null) {
                    event.setCancelled(true);
                }
            }
        });

        node.addListener(PlayerCancelItemUseEvent.class, event -> {
            var player = event.getPlayer();
            var stack = event.getItemStack();
            if (stack.material() != Material.BOW) return;

            ItemStack projectileItem;
            int projectileSlot;

            ProjectileItemFeature.ProjectileItem projectile = this.projectileItemFeature.getBowProjectile(player);
            if (projectile == null) {
                if (player.getGameMode() != GameMode.CREATIVE) return;

                projectileItem = Arrow.DEFAULT_ARROW;
                projectileSlot = -1;
            } else {
                projectileItem = projectile.stack();
                projectileSlot = projectile.slot();
            }

            var useTicks = player.getCurrentItemUseTime();
            var power = this.getBowPower(useTicks);
            if (power < 0.1) return;

            var ammoUse = projectileItem.material() == Material.ARROW
                    ? (int) this.enchantmentFeature.modifyConditionalValue(
                            stack, EffectComponent.AMMO_USE, 1.0F, true)
                    : 1;
            if (player.getGameMode() != GameMode.CREATIVE && ammoUse > projectileItem.amount()) return;

            var arrow = this.createArrow(projectileItem.withAmount(1), player);
            arrow.setWeaponItem(stack);

            if (power >= 1) arrow.setCritical(true);

            var arrowDamage = this.enchantmentFeature.modifyConditionalValue(
                    stack, EffectComponent.DAMAGE, (float) arrow.getBaseDamage(), true
            );
            arrow.setBaseDamage(arrowDamage);

            var punchKnockback = this.enchantmentFeature.modifyConditionalValue(
                    stack, EffectComponent.KNOCKBACK, 0.0F, true
            );
            if (punchKnockback > 0.0F) arrow.setKnockback(punchKnockback);

            var projectileFireTicks = this.enchantmentFeature.getProjectileIgniteTicks(stack);
            if (projectileFireTicks > 0) {
                arrow.setFireTicksLeft(projectileFireTicks);
            }

            this.itemDamageFeature.damageEquipment(player, event.getHand() == PlayerHand.MAIN ?
                    EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, 1);

            var reallyInfinite = player.getGameMode() == GameMode.CREATIVE || ammoUse <= 0;
            if (reallyInfinite || player.getGameMode() == GameMode.CREATIVE
                    && (projectileItem.material() == Material.SPECTRAL_ARROW
                    || projectileItem.material() == Material.TIPPED_ARROW)) {
                arrow.setPickupMode(AbstractArrow.PickupMode.CREATIVE_ONLY);
            }

            var position = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
            arrow.shootFromRotation(position.pitch(), position.yaw(), 0 , power * 3, 1.0);
            var playerVel = this.playerStateFeature.getKnownMovement(player);
            arrow.setVelocity(arrow.getVelocity().add(playerVel.x(),
                    player.isOnGround() ? 0.0 : playerVel.y(), playerVel.z()));
            arrow.setInstance(Objects.requireNonNull(player.getInstance()), position.withView(arrow.getPosition()));

            var random = ThreadLocalRandom.current();
            ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
                    SoundEvent.ENTITY_ARROW_SHOOT, Sound.Source.PLAYER,
                    1.0F, 1.0F / (random.nextFloat() * 0.4F + 1.2F) + (float) power * 0.5F
            ), player);

            if (!reallyInfinite && player.getGameMode() != GameMode.CREATIVE && projectileSlot >= 0) {
                player.getInventory().setItemStack(projectileSlot,
                        projectileItem.withAmount(projectileItem.amount() - ammoUse));
            }
        });
    }

    protected double getBowPower(long ticks) {
        var seconds = ticks / (double) ServerFlag.SERVER_TICKS_PER_SECOND;
        var power = (seconds * seconds + seconds * 2.0) / 3.0;
        if (power > 1) {
            power = 1;
        }

        return power;
    }

    protected AbstractArrow createArrow(ItemStack stack, @Nullable Entity shooter) {
        if (stack.material() == Material.SPECTRAL_ARROW) {
            return new SpectralArrow(shooter, this.enchantmentFeature);
        } else {
            var arrow = new Arrow(shooter, this.effectFeature, this.enchantmentFeature);
            arrow.setItemStack(stack);
            return arrow;
        }
    }
}
