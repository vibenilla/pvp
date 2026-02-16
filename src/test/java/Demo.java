import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;
import net.minestom.server.world.attribute.EnvironmentAttribute;

void main() {
    var server = MinecraftServer.init();
    var instance = MinecraftServer.getInstanceManager().createInstanceContainer(createDimension());
    instance.setExplosionSupplier(CombatFeatures.modernVanilla().get(FeatureType.EXPLOSION).getExplosionSupplier());

    MinecraftServer.getGlobalEventHandler()
            .addChild(MinestomPvP.events())
            .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                var spawnPosition = new Pos(211.5D, 61.0D, 162.5D, -90.0F, 0.0F);
                event.setSpawningInstance(instance);
                event.getPlayer().setRespawnPoint(spawnPosition);
            })
            .addListener(PlayerSpawnEvent.class, event -> event.getPlayer().setGameMode(GameMode.CREATIVE));

    MinecraftServer.getCommandManager().register(new Command("gamemode") {{
        var argument = ArgumentType.Enum("mode", GameMode.class).setFormat(ArgumentEnum.Format.LOWER_CASED);

        this.addSyntax((sender, context) -> {
            var player = (Player) sender;
            player.setGameMode(context.get(argument));
        }, argument);
    }});

    MinestomPvP.init();
    server.start("0.0.0.0", 25565);
}

private static RegistryKey<DimensionType> createDimension() {
    var dimension = DimensionType.builder()
            .ambientLight(1.0F)
            .setAttribute(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS, true)
            .build();

    return MinecraftServer.getDimensionTypeRegistry().register(Key.key("vibenilla:pvp"), dimension);
}
