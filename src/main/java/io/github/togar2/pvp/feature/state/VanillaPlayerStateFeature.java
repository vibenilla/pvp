package io.github.togar2.pvp.feature.state;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Vanilla implementation of {@link PlayerStateFeature}
 */
public class VanillaPlayerStateFeature implements PlayerStateFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaPlayerStateFeature> DEFINED = new DefinedFeature<>(
			FeatureType.PLAYER_STATE, configuration -> new VanillaPlayerStateFeature()
	);

	public static final Tag<Block> LAST_CLIMBED_BLOCK = Tag.Transient("lastClimbedBlock");
	public static final Tag<Vec> MOVEMENT_THIS_TICK = Tag.Transient("movementThisTick");
	public static final Tag<Vec> KNOWN_MOVEMENT = Tag.Transient("knownMovement");

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerTickEvent.class, event -> {
			Player player = event.getPlayer();
			if (player.isOnGround() && player.hasTag(LAST_CLIMBED_BLOCK)) {
				// Make sure fall damage message still has the correct climbed block
				// Due to multithreading this can be triggered before the death message is computed
				player.scheduleNextTick(p -> p.removeTag(LAST_CLIMBED_BLOCK));
			}

			Vec movedThisTick = player.getTag(MOVEMENT_THIS_TICK);
			player.setTag(KNOWN_MOVEMENT, movedThisTick == null ? Vec.ZERO : movedThisTick);
			player.removeTag(MOVEMENT_THIS_TICK);
		});

		node.addListener(PlayerMoveEvent.class, event -> {
			Player player = event.getPlayer();
			if (this.isClimbing(player)) {
				player.setTag(LAST_CLIMBED_BLOCK, player.getInstance().getBlock(player.getPosition()));
			}

			Vec delta = event.getNewPosition().asVec().sub(player.getPosition().asVec());
			Vec movedThisTick = player.getTag(MOVEMENT_THIS_TICK);
			player.setTag(MOVEMENT_THIS_TICK, movedThisTick == null ? delta : movedThisTick.add(delta));
		});
	}

	@Override
	public Vec getKnownMovement(Entity entity) {
		Vec knownMovement = entity.getTag(KNOWN_MOVEMENT);
		if (knownMovement == null) return entity.getVelocity();

		return knownMovement.mul(ServerFlag.SERVER_TICKS_PER_SECOND);
	}

	@Override
	public boolean isClimbing(LivingEntity entity) {
		if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) return false;

		var tag = MinecraftServer.process().blocks().getTag(Key.key("minecraft:climbable"));
		assert tag != null;

		Block block = Objects.requireNonNull(entity.getInstance()).getBlock(entity.getPosition());
		var key = block.asKey();
		assert key != null;
		return tag.contains(key);
	}

	@Override
	public @Nullable Block getLastClimbedBlock(LivingEntity entity) {
		return entity.getTag(LAST_CLIMBED_BLOCK);
	}
}
