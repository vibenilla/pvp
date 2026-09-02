package io.github.togar2.pvp.feature.config;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CombatFeatureRegistry {
    private static final Tag<Boolean> INITIALIZED = Tag.Transient("combatFeaturesInitialized");

    private static final EventNode<Event> initNode = EventNode.all("combat-feature-init");
    private static final List<DefinedFeature<?>> features = new CopyOnWriteArrayList<>();
    private static @Nullable GlobalEventHandler attachedHandler;

    static {
        initNode.addListener(AddEntityToInstanceEvent.class, event -> {
            if (!(event.getEntity() instanceof Player player)) return;

            var reason = player.hasTag(INITIALIZED) ? PlayerInitReason.INSTANCE_CHANGE : PlayerInitReason.JOIN;
            player.setTag(INITIALIZED, true);
            initPlayer(player, reason);
        });
        initNode.addListener(PlayerRespawnEvent.class, event -> initPlayer(event.getPlayer(), PlayerInitReason.RESPAWN));
    }

    public static synchronized void init(DefinedFeature<?> feature) {
        attach();

        if (!features.contains(feature)) features.add(feature);
    }

    public static synchronized void attach() {
        if (MinecraftServer.process() == null) {
            throw new IllegalStateException("MinecraftServer.init() must run before combat features are used");
        }

        var globalEventHandler = MinecraftServer.getGlobalEventHandler();
        if (attachedHandler == globalEventHandler) return;

        if (attachedHandler != null) attachedHandler.removeChild(initNode);
        globalEventHandler.addChild(initNode);
        attachedHandler = globalEventHandler;
    }

    private static void initPlayer(Player player, PlayerInitReason reason) {
        for (var feature : features) {
            var playerInit = feature.playerInit();
            if (playerInit != null) playerInit.init(player, reason);
        }
    }
}
