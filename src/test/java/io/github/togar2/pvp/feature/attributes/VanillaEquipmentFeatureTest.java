package io.github.togar2.pvp.feature.attributes;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.utils.CombatVersion;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventNode;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.AttributeList;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.client.play.ClientClickWindowPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaEquipmentFeatureTest {
    @Test
    public void rejectsMismatchedArmorSlotLeftClick(Env env) {
        var node = this.addEquipmentFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);

            var leggings = ItemStack.of(Material.DIAMOND_LEGGINGS);
            player.getInventory().setItemStack(0, leggings);

            this.leftClick(player, 0);
            assertEquals(leggings, player.getInventory().getCursorItem());
            assertEquals(ItemStack.AIR, player.getInventory().getItemStack(0));

            this.leftClick(player, PlayerInventoryUtils.CHESTPLATE_SLOT);
            assertEquals(leggings, player.getInventory().getCursorItem());
            assertEquals(ItemStack.AIR, player.getInventory().getItemStack(PlayerInventoryUtils.CHESTPLATE_SLOT));

            this.leftClick(player, PlayerInventoryUtils.LEGGINGS_SLOT);
            assertEquals(ItemStack.AIR, player.getInventory().getCursorItem());
            assertEquals(leggings, player.getInventory().getItemStack(PlayerInventoryUtils.LEGGINGS_SLOT));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void rejectsMismatchedArmorSlotHotbarSwap(Env env) {
        var node = this.addEquipmentFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);

            var leggings = ItemStack.of(Material.DIAMOND_LEGGINGS);
            player.getInventory().setItemStack(0, leggings);

            this.hotbarSwap(player, PlayerInventoryUtils.CHESTPLATE_SLOT, 0);
            assertEquals(leggings, player.getInventory().getItemStack(0));
            assertEquals(ItemStack.AIR, player.getInventory().getItemStack(PlayerInventoryUtils.CHESTPLATE_SLOT));

            this.hotbarSwap(player, PlayerInventoryUtils.LEGGINGS_SLOT, 0);
            assertEquals(ItemStack.AIR, player.getInventory().getItemStack(0));
            assertEquals(leggings, player.getInventory().getItemStack(PlayerInventoryUtils.LEGGINGS_SLOT));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void enchantmentAttributeEffectsFollowEquipment(Env env) {
        var node = this.addEquipmentFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));

            player.setEquipment(EquipmentSlot.BOOTS, ItemStack.of(Material.DIAMOND_BOOTS).with(
                    DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.DEPTH_STRIDER, 3)
            ));
            player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.DIAMOND_CHESTPLATE).with(
                    DataComponents.ENCHANTMENTS, EnchantmentList.EMPTY.with(Enchantment.BLAST_PROTECTION, 4)
            ));

            assertEquals(1.0, player.getAttributeValue(Attribute.WATER_MOVEMENT_EFFICIENCY), 1.0E-5);
            assertEquals(0.6, player.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE), 1.0E-5);
            assertTrue(player.getAttribute(Attribute.WATER_MOVEMENT_EFFICIENCY).modifiers().stream()
                    .anyMatch(modifier -> modifier.id().equals(Key.key("minecraft:enchantment.depth_strider/feet"))));

            player.setEquipment(EquipmentSlot.BOOTS, ItemStack.AIR);
            player.setEquipment(EquipmentSlot.CHESTPLATE, ItemStack.of(Material.IRON_CHESTPLATE));

            assertEquals(0.0, player.getAttributeValue(Attribute.WATER_MOVEMENT_EFFICIENCY), 1.0E-5);
            assertEquals(0.0, player.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE), 1.0E-5);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void itemAttributeModifiersComeFromTheItemOnly(Env env) {
        var node = this.addEquipmentFeature();

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));

            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD));
            assertEquals(7.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1.0E-5);

            player.setItemInMainHand(ItemStack.of(Material.DIAMOND_SWORD).with(DataComponents.ATTRIBUTE_MODIFIERS, AttributeList.EMPTY));
            assertEquals(1.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1.0E-5);

            player.setItemInMainHand(ItemStack.AIR);
            assertEquals(1.0, player.getAttributeValue(Attribute.ATTACK_DAMAGE), 1.0E-5);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addEquipmentFeature() {
        var node = CombatFeatures.empty()
                .version(CombatVersion.MODERN)
                .add(CombatFeatures.VANILLA_EQUIPMENT)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }

    private void leftClick(Player player, int slot) {
        this.click(player, slot, 0, ClientClickWindowPacket.ClickType.PICKUP);
    }

    private void hotbarSwap(Player player, int slot, int hotbarSlot) {
        this.click(player, slot, hotbarSlot, ClientClickWindowPacket.ClickType.SWAP);
    }

    private void click(Player player, int slot, int button, ClientClickWindowPacket.ClickType clickType) {
        var windowSlot = PlayerInventoryUtils.convertMinestomSlotToWindowSlot(slot);
        player.addPacketToQueue(new ClientClickWindowPacket(
                (byte) 0, 0, (short) windowSlot, (byte) button, clickType, Map.of(), ItemStack.Hash.AIR
        ));
        player.interpretPacketQueue();
    }
}
