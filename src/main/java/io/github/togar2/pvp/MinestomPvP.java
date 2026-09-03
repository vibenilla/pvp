package io.github.togar2.pvp;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.player.CombatPlayerImpl;
import io.github.togar2.pvp.potion.effect.CombatPotionEffects;
import io.github.togar2.pvp.potion.item.CombatPotionTypes;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeInstance;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.EntityInstanceEvent;

/**
 * The main class of MinestomPvP, which contains the {@link MinestomPvP#init()} method.
 * <p>
 * It can also be used to set legacy attack for a player, see {@link MinestomPvP#setLegacyAttack(Player, boolean)}.
 */
public class MinestomPvP {
    public static EventNode<EntityInstanceEvent> events() {
        return CombatFeatures.modernVanilla().createNode();
    }

    public static EventNode<EntityInstanceEvent> legacyEvents() {
        return CombatFeatures.legacyVanilla().createNode();
    }

    public static void setLegacyAttack(Player player, boolean legacyAttack) {
        var speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (legacyAttack) {
            speed.setBaseValue(100);
        } else {
            speed.setBaseValue(speed.attribute().defaultValue());
        }
    }

    public static void init() {
        init(true);
    }

    public static void init(boolean player) {
        CombatEnchantments.registerAll();
        CombatPotionEffects.registerAll();
        CombatPotionTypes.registerAll();

        CombatPlayer.init(MinecraftServer.getGlobalEventHandler());

        if (player) {
            MinecraftServer.getConnectionManager().setPlayerProvider(CombatPlayerImpl::new);
        }
    }

    @Deprecated(forRemoval = true)
    public static void init(boolean player, boolean keepAlive) {
        init(player);
    }
}
