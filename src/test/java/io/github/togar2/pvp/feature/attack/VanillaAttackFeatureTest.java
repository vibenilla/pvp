package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.events.FinalAttackEvent;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaAttackFeatureTest {
    @Test
    public void sprintAttackRequiresClientSprintReleaseBeforeRestart(Env env) {
        var featureSet = CombatFeatures.legacyVanilla();
        var attackFeature = featureSet.get(FeatureType.ATTACK);
        var node = featureSet.createNode();
        var sprintAttacks = new ArrayList<Boolean>();
        var attackListener = EventNode.all("sprint-attack-listener")
                .addListener(FinalAttackEvent.class, event -> sprintAttacks.add(event.isSprint()));

        MinecraftServer.getGlobalEventHandler().addChild(node);
        MinecraftServer.getGlobalEventHandler().addChild(attackListener);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(instance, new Pos(0.0, 40.0, -1.0)).join();
            player.setGameMode(GameMode.SURVIVAL);
            player.setSprinting(true);

            attackFeature.performAttack(player, target);
            this.sprint(player, ClientEntityActionPacket.Action.START_SPRINTING);
            attackFeature.performAttack(player, target);

            assertFalse(player.isSprinting());

            this.sprint(player, ClientEntityActionPacket.Action.STOP_SPRINTING);
            this.sprint(player, ClientEntityActionPacket.Action.START_SPRINTING);
            attackFeature.performAttack(player, target);

            assertEquals(List.of(true, false, true), sprintAttacks);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(attackListener);
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void attackPacketIsIgnoredWithAPiercingWeapon(Env env) {
        var node = CombatFeatures.modernVanilla().createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);

        try {
            var instance = env.createFlatInstance();
            var player = env.createPlayer(instance, new Pos(0.0, 40.0, 0.0));
            player.setGameMode(GameMode.SURVIVAL);
            var target = new LivingEntity(EntityType.ZOMBIE);
            target.setInstance(instance, new Pos(0.0, 40.0, 1.5)).join();
            target.setHealth(20.0F);

            player.setItemInMainHand(ItemStack.of(Material.IRON_SPEAR));
            EventDispatcher.call(new EntityAttackEvent(player, target));
            assertEquals(20.0F, target.getHealth());

            player.setItemInMainHand(ItemStack.of(Material.IRON_SWORD));
            EventDispatcher.call(new EntityAttackEvent(player, target));
            assertTrue(target.getHealth() < 20.0F);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private void sprint(Player player, ClientEntityActionPacket.Action action) {
        player.addPacketToQueue(new ClientEntityActionPacket(
                player.getEntityId(),
                action,
                0
        ));
        player.interpretPacketQueue();
    }
}
