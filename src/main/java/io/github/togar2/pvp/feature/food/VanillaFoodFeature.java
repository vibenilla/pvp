package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.ItemCooldownFeature;
import io.github.togar2.pvp.utils.PotionFlags;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerBeginItemUseEvent;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.Consumable;
import net.minestom.server.item.component.ConsumeEffect;
import net.minestom.server.item.component.ConsumeEffect.*;
import net.minestom.server.item.component.Food;
import net.minestom.server.item.component.SuspiciousStewEffects;
import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.registry.RegistryTag;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link FoodFeature}
 * <p>
 * This also includes eating of food items.
 */
public class VanillaFoodFeature implements FoodFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaFoodFeature> DEFINED = new DefinedFeature<>(
            FeatureType.FOOD, VanillaFoodFeature::new,
            FeatureType.ITEM_COOLDOWN
    );

    private final FeatureConfiguration configuration;

    private ItemCooldownFeature itemCooldownFeature;

    public VanillaFoodFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.itemCooldownFeature = this.configuration.get(FeatureType.ITEM_COOLDOWN);
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerBeginItemUseEvent.class, event -> {
            if (!event.getItemStack().has(DataComponents.CONSUMABLE))
                return;
            @Nullable Food foodComponent = event.getItemStack().get(DataComponents.FOOD);
            @Nullable Consumable consumableComponent = event.getItemStack().get(DataComponents.CONSUMABLE);

            var alwaysEat = foodComponent == null || foodComponent.canAlwaysEat();
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE
                    && !alwaysEat && event.getPlayer().getFood() == 20) {
                event.setCancelled(true);
                return;
            }

            if (consumableComponent != null) event.setItemUseDuration(consumableComponent.consumeTicks());
        });

        node.addListener(PlayerFinishItemUseEvent.class, event -> {
            if (event.getItemStack().material() != Material.MILK_BUCKET
                    && !(event.getItemStack().has(DataComponents.FOOD) || event.getItemStack().has(DataComponents.CONSUMABLE)))
                return;

            this.onFinishEating(event.getPlayer(), event.getItemStack(), event.getHand());
        });

        node.addListener(PlayerTickEvent.class, event -> {
            var player = event.getPlayer();
            if (player.isSilent() || !player.isEating()) return;

            this.tickEatingSounds(player);
        });
    }

    protected void onFinishEating(Player player, ItemStack stack, PlayerHand hand) {
        this.eat(player, stack);

        if (stack.has(DataComponents.USE_COOLDOWN)) {
            this.itemCooldownFeature.setCooldown(player, stack);
        }

        var food = stack.get(DataComponents.FOOD);
        var consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        var random = ThreadLocalRandom.current();

        this.triggerEatingSound(player, consumable);

        if (food != null) {
            ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
                    SoundEvent.ENTITY_PLAYER_BURP, Sound.Source.PLAYER,
                    0.5F, random.nextFloat() * 0.1F + 0.9F
            ), player);
        }

        var effectList = consumable.effects();

        for (var effect : effectList) {
            applyConsumeEffect(player, effect, random);
        }

        if (stack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
            var effects = stack.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
            assert effects != null;
            for (var effect : effects.effects()) {
                player.addEffect(new Potion(effect.id(), (byte) 0, effect.durationTicks(), PotionFlags.defaultFlags()));
            }
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            var remainder = stack.get(DataComponents.USE_REMAINDER);

            if (remainder != null && !remainder.isAir()) {
                if (stack.amount() == 1) {
                    player.setItemInHand(hand, remainder);
                } else {
                    player.setItemInHand(hand, stack.withAmount(stack.amount() - 1));
                    player.getInventory().addItemStack(remainder);
                }
            } else {
                player.setItemInHand(hand, stack.withAmount(stack.amount() - 1));
            }
        }
    }

    @Override
    public void addFood(Player player, int food, float saturation) {
        player.setFood(Math.min(food + player.getFood(), 20));
        player.setFoodSaturation(Math.min(player.getFoodSaturation() + saturation, player.getFood()));
    }

    @Override
    public void eat(Player player, int food, float saturationModifier) {
        this.addFood(player, food, (float) food * saturationModifier * 2.0F);
    }

    @Override
    public void eat(Player player, ItemStack stack) {
        var foodComponent = stack.get(DataComponents.FOOD);
        if (foodComponent == null) return;
        this.addFood(player, foodComponent.nutrition(), foodComponent.saturationModifier());
    }

    @Override
    public void applySaturationEffect(Player player, int amplifier) {
        this.eat(player, amplifier + 1, 1.0F);
    }

    protected void tickEatingSounds(Player player) {
        var stack = player.getItemInHand(Objects.requireNonNull(player.getItemUseHand()));

        var component = stack.get(DataComponents.CONSUMABLE);
        if (component == null) return;

        var useTime = component.consumeTicks();
        var usedTicks = player.getCurrentItemUseTime();
        var remainingUseTicks = useTime - usedTicks;

        var canTrigger = component.consumeTicks() < 32 || remainingUseTicks <= useTime - 7;
        var shouldTrigger = canTrigger && remainingUseTicks % 4 == 0;
        if (!shouldTrigger) return;

        this.triggerEatingSound(player, component);
    }

    protected void triggerEatingSound(Player player, Consumable consumable) {
        var random = ThreadLocalRandom.current();
        player.getViewersAsAudience().playSound(Sound.sound(
                consumable.sound(), Sound.Source.PLAYER,
                0.5F + 0.5F * random.nextInt(2),
                (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F
        ), player);
    }

    public static void applyConsumeEffect(Entity entity, ConsumeEffect effect, Random random) {
        switch (effect) {
            case ApplyEffects(List<CustomPotionEffect> effects, float probability) -> {
                if (random.nextFloat() >= probability) return;
                for (var potionEffect : effects) {
                    entity.addEffect(new Potion(
                            potionEffect.id(), (byte)potionEffect.amplifier(),
                            potionEffect.duration(),
                            PotionFlags.create(
                                    potionEffect.isAmbient(),
                                    potionEffect.showParticles(),
                                    potionEffect.showIcon()
                            )
                    ));
                }
            }
            case RemoveEffects(RegistryTag<PotionEffect> potionEffects) -> entity.getActiveEffects().stream()
                    .map(TimedPotion::potion)
                    .map(Potion::effect)
                    .filter(potionEffects::contains)
                    .forEach(entity::removeEffect);
            case ClearAllEffects ignored -> entity.clearEffects();
            case TeleportRandomly(float diameter) -> ChorusFruitUtil.tryChorusTeleport(entity, diameter);
            case PlaySound(SoundEvent sound) -> ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
                    sound, Sound.Source.PLAYER,
                    1.0F, 1.0F
            ), entity);
            default -> throw new IllegalArgumentException("Unexpected value: " + effect);
        }
    }
}
