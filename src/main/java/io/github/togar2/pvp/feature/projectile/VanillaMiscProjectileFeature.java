package io.github.togar2.pvp.feature.projectile;

import io.github.togar2.pvp.entity.projectile.*;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.ItemCooldownFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link MiscProjectileFeature}
 */
public class VanillaMiscProjectileFeature implements MiscProjectileFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaMiscProjectileFeature> DEFINED = new DefinedFeature<>(
            FeatureType.MISC_PROJECTILE, VanillaMiscProjectileFeature::new,
            FeatureType.ITEM_COOLDOWN, FeatureType.FALL, FeatureType.PLAYER_STATE
    );

    private final FeatureConfiguration configuration;

    private PlayerStateFeature playerStateFeature;

    private ItemCooldownFeature itemCooldownFeature;
    private FallFeature fallFeature;

    public VanillaMiscProjectileFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
        this.itemCooldownFeature = this.configuration.get(FeatureType.ITEM_COOLDOWN);
        this.fallFeature = this.configuration.get(FeatureType.FALL);
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
            if (event.getItemStack().material() != Material.FIREWORK_ROCKET) return;
            if (event.getPlayer().isFlyingWithElytra()) return;

            this.useFireworkRocketOnBlock(event);
        });

        node.addListener(PlayerUseItemEvent.class, event -> {
            if (event.getItemStack().material() == Material.FIREWORK_ROCKET
                    && event.getPlayer().isFlyingWithElytra()) {
                this.useFireworkRocket(event);
                return;
            }

            if (event.getItemStack().material() != Material.SNOWBALL
                    && event.getItemStack().material() != Material.EGG
                    && event.getItemStack().material() != Material.ENDER_PEARL
                    && event.getItemStack().material() != Material.WIND_CHARGE)
                return;

            var player = event.getPlayer();
            var stack = event.getItemStack();

            var snowball = stack.material() == Material.SNOWBALL;
            var enderpearl = stack.material() == Material.ENDER_PEARL;
            var windCharge = stack.material() == Material.WIND_CHARGE;

            SoundEvent soundEvent;
            CustomEntityProjectile projectile;
            if (snowball) {
                soundEvent = SoundEvent.ENTITY_SNOWBALL_THROW;
                projectile = new Snowball(player);
            } else if (enderpearl) {
                soundEvent = SoundEvent.ENTITY_ENDER_PEARL_THROW;
                projectile = new ThrownEnderpearl(player, this.fallFeature);
            } else if (windCharge) {
                soundEvent = SoundEvent.ENTITY_WIND_CHARGE_THROW;
                projectile = new WindCharge(player, this.fallFeature);
            } else {
                soundEvent = SoundEvent.ENTITY_EGG_THROW;
                projectile = new ThrownEgg(player);
            }

            if (projectile instanceof ItemHoldingProjectile itemHoldingProjectile) {
                itemHoldingProjectile.setItem(stack);
            }

            var random = ThreadLocalRandom.current();
            ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
                    soundEvent,
                    snowball || enderpearl || windCharge ? Sound.Source.NEUTRAL : Sound.Source.PLAYER,
                    0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F)
            ), player);

            if (enderpearl || windCharge) {
                this.itemCooldownFeature.setCooldown(player, stack);
            }

            var position = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
            projectile.shootFromRotation(position.pitch(), position.yaw(), 0, 1.5, 1.0);

            var playerVel = this.playerStateFeature.getKnownMovement(player);
            projectile.setVelocity(projectile.getVelocity().add(playerVel.x(),
                    player.isOnGround() ? 0.0 : playerVel.y(), playerVel.z()));
            projectile.setInstance(Objects.requireNonNull(player.getInstance()), position.withView(projectile.getPosition()))
                    .thenRun(() -> projectile.setVelocity(projectile.getVelocity()));

            if (player.getGameMode() != GameMode.CREATIVE) {
                player.setItemInHand(event.getHand(), stack.withAmount(stack.amount() - 1));
            }
        });
    }

    private void useFireworkRocket(PlayerUseItemEvent event) {
        var player = event.getPlayer();
        var stack = event.getItemStack();
        var rocket = new FireworkRocket(player, stack);

        rocket.setInstance(Objects.requireNonNull(player.getInstance()), player.getPosition());

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setItemInHand(event.getHand(), stack.withAmount(stack.amount() - 1));
        }
    }

    private void useFireworkRocketOnBlock(PlayerUseItemOnBlockEvent event) {
        var player = event.getPlayer();
        var stack = event.getItemStack();
        var direction = event.getBlockFace().toDirection();
        var clickPosition = event.getPosition().add(event.getCursorPosition());
        var rocketPosition = clickPosition.add(direction.mul(0.15));
        var rocket = new FireworkRocket(player, stack, false);

        rocket.setInstance(Objects.requireNonNull(player.getInstance()), rocketPosition);

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setItemInHand(event.getHand(), stack.withAmount(stack.amount() - 1));
        }
    }
}
