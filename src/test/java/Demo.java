import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

void main() {
    var server = MinecraftServer.init();
    var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
    instance.setChunkSupplier(LightingChunk::new);
    instance.setGenerator(unit -> unit.modifier().fillHeight(-64, 0, Block.STONE));
    instance.setExplosionSupplier(CombatFeatures.modernVanilla().get(FeatureType.EXPLOSION).getExplosionSupplier());

    MinecraftServer.getGlobalEventHandler()
            .addChild(MinestomPvP.events())
            .addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(instance))
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
