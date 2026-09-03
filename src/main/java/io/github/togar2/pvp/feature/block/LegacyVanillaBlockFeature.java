package io.github.togar2.pvp.feature.block;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.config.PlayerInitReason;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

/**
 * Vanilla implementation of {@link LegacyBlockFeature}
 */
public class LegacyVanillaBlockFeature extends VanillaBlockFeature
        implements LegacyBlockFeature, RegistrableFeature {
    public static final DefinedFeature<LegacyVanillaBlockFeature> SHIELD = new DefinedFeature<>(
            FeatureType.LEGACY_BLOCK, configuration -> new LegacyVanillaBlockFeature(configuration, ItemStack.of(Material.SHIELD)),
            LegacyVanillaBlockFeature::initPlayer,
            FeatureType.ITEM_DAMAGE
    );

    public static final Tag<Long> LAST_SWING_TIME = Tag.Long("lastSwingTime");
    public static final Tag<Boolean> BLOCKING_SWORD = Tag.Boolean("blockingSword");
    public static final Tag<ItemStack> BLOCK_REPLACEMENT_ITEM = Tag.ItemStack("blockReplacementItem");

    private final ItemStack blockingItem;

    public LegacyVanillaBlockFeature(FeatureConfiguration configuration, ItemStack blockingItem) {
        super(configuration.add(FeatureType.VERSION, CombatVersion.LEGACY));
        this.blockingItem = blockingItem;
    }

    public static void initPlayer(Player player, PlayerInitReason reason) {
        if (reason == PlayerInitReason.INSTANCE_CHANGE) return;

        player.setTag(LAST_SWING_TIME, 0L);
        player.setTag(BLOCKING_SWORD, false);
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerUseItemEvent.class, this::handleUseItem);
        node.addListener(PlayerFinishItemUseEvent.class, this::handleUpdateState);
        node.addListener(PlayerSwapItemEvent.class, this::handleSwapItem);
        node.addListener(PlayerChangeHeldSlotEvent.class, this::handleChangeSlot);

        node.addListener(PlayerHandAnimationEvent.class, event -> {
            if (event.getHand() == PlayerHand.MAIN)
                event.getPlayer().setTag(LAST_SWING_TIME, System.currentTimeMillis());
        });
    }

    @Override
    public boolean isBlocking(Player player) {
        return player.getTag(BLOCKING_SWORD);
    }

    @Override
    public void block(Player player) {
        if (!this.isBlocking(player)) {
            player.setTag(BLOCK_REPLACEMENT_ITEM, player.getItemInOffHand());
            player.setTag(BLOCKING_SWORD, true);

            player.setItemInOffHand(this.blockingItem);
            player.refreshActiveHand(true, true, false);
            player.sendPacketToViewersAndSelf(player.getMetadataPacket());
        }
    }

    @Override
    public void unblock(Player player) {
        if (this.isBlocking(player)) {
            player.setTag(BLOCKING_SWORD, false);
            player.setItemInOffHand(player.getTag(BLOCK_REPLACEMENT_ITEM));
            player.removeTag(BLOCK_REPLACEMENT_ITEM);
        }
    }

    private void handleUseItem(PlayerUseItemEvent event) {
        var player = event.getPlayer();

        if (event.getHand() == PlayerHand.MAIN && !this.isBlocking(player) && this.canBlockWith(player, event.getItemStack())) {
            var elapsedSwingTime = System.currentTimeMillis() - player.getTag(LAST_SWING_TIME);
            if (elapsedSwingTime < 50) {
                return;
            }

            this.block(player);
        }
    }

    protected void handleUpdateState(PlayerFinishItemUseEvent event) {
        if (event.getHand() == PlayerHand.OFF && event.getItemStack().isSimilar(this.blockingItem))
            this.unblock(event.getPlayer());
    }

    protected void handleSwapItem(PlayerSwapItemEvent event) {
        var player = event.getPlayer();
        if (player.getItemInOffHand().isSimilar(this.blockingItem) && this.isBlocking(player))
            event.setCancelled(true);
    }

    protected void handleChangeSlot(PlayerChangeHeldSlotEvent event) {
        var player = event.getPlayer();
        if (player.getItemInOffHand().isSimilar(this.blockingItem) && this.isBlocking(player))
            this.unblock(player);
    }

    @Override
    public boolean canBlockWith(Player player, ItemStack stack) {
        return stack.material().key().value().contains("sword");
    }
}
