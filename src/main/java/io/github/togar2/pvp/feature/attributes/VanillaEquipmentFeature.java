package io.github.togar2.pvp.feature.attributes;

import io.github.togar2.pvp.enchantment.EnchantmentAttributes;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.EntityEquipEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Vanilla implementation of {@link EquipmentFeature}
 */
public class VanillaEquipmentFeature implements EquipmentFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaEquipmentFeature> DEFINED = new DefinedFeature<>(
            FeatureType.EQUIPMENT, VanillaEquipmentFeature::new
    );

    public VanillaEquipmentFeature(FeatureConfiguration configuration) {
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(InventoryPreClickEvent.class, this::onInventoryPreClick);
        node.addListener(EntityEquipEvent.class, this::onEquip);
        node.addListener(PlayerChangeHeldSlotEvent.class, event -> {
            var entity = event.getPlayer();
            var oldItem = entity.getEquipment(EquipmentSlot.MAIN_HAND);
            var newItem = event.getPlayer().getInventory().getItemStack(event.getNewSlot());
            EnchantmentAttributes.updateEquipmentAttributes(entity, oldItem, newItem, EquipmentSlot.MAIN_HAND);
        });
    }

    private void onInventoryPreClick(InventoryPreClickEvent event) {
        var player = event.getPlayer();

        if (event.getInventory() != player.getInventory()) return;

        if (this.shouldCancelInvalidArmorPlacement(player, event.getClick())) {
            event.setCancelled(true);
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (this.shouldCancelArmorRemoval(player, event.getClick())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancelInvalidArmorPlacement(Player player, Click click) {
        if (click instanceof Click.Left || click instanceof Click.Right) {
            return this.shouldCancelInvalidArmorSlotPlacement(
                    click.slot(), player.getInventory().getCursorItem()
            );
        }

        if (click instanceof Click.HotbarSwap hotbarSwap) {
            var incomingItem = player.getInventory().getItemStack(hotbarSwap.hotbarSlot());
            return this.shouldCancelInvalidArmorSlotPlacement(hotbarSwap.slot(), incomingItem);
        }

        if (click instanceof Click.OffhandSwap offhandSwap) {
            var incomingItem = player.getInventory().getItemStack(PlayerInventoryUtils.OFFHAND_SLOT);
            return this.shouldCancelInvalidArmorSlotPlacement(offhandSwap.slot(), incomingItem);
        }

        if (click instanceof Click.Drag drag) {
            return this.shouldCancelInvalidArmorSlotDrag(player, drag.slots());
        }

        return false;
    }

    private boolean shouldCancelInvalidArmorSlotPlacement(int slot, ItemStack incomingItem) {
        var armorSlot = this.getArmorSlot(slot);

        if (armorSlot == null) {
            return false;
        }

        return !incomingItem.isAir() && !this.canPlaceInArmorSlot(incomingItem, armorSlot);
    }

    private boolean shouldCancelInvalidArmorSlotDrag(Player player, List<Integer> slots) {
        var incomingItem = player.getInventory().getCursorItem();

        if (incomingItem.isAir()) {
            return false;
        }

        for (var slot : slots) {
            var armorSlot = this.getArmorSlot(slot);

            if (armorSlot == null) {
                continue;
            }

            if (!this.canPlaceInArmorSlot(incomingItem, armorSlot)) {
                return true;
            }
        }

        return false;
    }

    private boolean canPlaceInArmorSlot(ItemStack itemStack, EquipmentSlot slot) {
        var equippable = itemStack.get(DataComponents.EQUIPPABLE);

        if (equippable == null) {
            return false;
        }

        var allowedEntities = equippable.allowedEntities();
        return equippable.slot() == slot
                && (allowedEntities == null || allowedEntities.contains(EntityType.PLAYER));
    }

    private boolean shouldCancelArmorRemoval(Player player, Click click) {
        var armorSlot = this.getArmorSlot(click.slot());

        if (armorSlot == null) {
            return false;
        }

        var clickedItem = player.getInventory().getItemStack(click.slot());
        return clickedItem.has(EffectComponent.PREVENT_ARMOR_CHANGE);
    }

    private @Nullable EquipmentSlot getArmorSlot(int slot) {
        return switch (slot) {
            case PlayerInventoryUtils.HELMET_SLOT -> EquipmentSlot.HELMET;
            case PlayerInventoryUtils.CHESTPLATE_SLOT -> EquipmentSlot.CHESTPLATE;
            case PlayerInventoryUtils.LEGGINGS_SLOT -> EquipmentSlot.LEGGINGS;
            case PlayerInventoryUtils.BOOTS_SLOT -> EquipmentSlot.BOOTS;
            default -> null;
        };
    }

    protected void onEquip(EntityEquipEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        var slot = event.getSlot();
        EnchantmentAttributes.updateEquipmentAttributes(entity, entity.getEquipment(slot), event.getEquippedItem(), slot);

        this.playEquipSound(entity, entity.getEquipment(slot), event.getEquippedItem(), slot);
    }

    private void playEquipSound(LivingEntity entity, ItemStack oldStack, ItemStack newStack, EquipmentSlot slot) {
        if (entity.isSilent()) return;
        if (entity.getAliveTicks() <= 0) return;
        if (newStack.without(DataComponents.DAMAGE).isSimilar(oldStack.without(DataComponents.DAMAGE))) return;

        var equippable = newStack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return;
        if (equippable.slot() != slot) return;

        ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
                equippable.equipSound(), Sound.Source.PLAYER,
                1.0F, 1.0F
        ), entity);
    }
}
