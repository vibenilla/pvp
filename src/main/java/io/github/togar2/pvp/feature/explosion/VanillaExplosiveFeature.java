package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.events.AnchorChargeEvent;
import io.github.togar2.pvp.events.AnchorExplodeEvent;
import io.github.togar2.pvp.events.CrystalPlaceEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.explosion.ExplosionFeature.IgnitionCause.ByPlayer;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.Sound.Source;
import net.minestom.server.MinecraftServer;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.world.attribute.EnvironmentAttribute.Modifier;
import net.minestom.server.world.attribute.EnvironmentAttributeMap;
import net.minestom.server.world.biome.Biome;

/**
 * Vanilla implementation of {@link ExplosiveFeature}
 */
public class VanillaExplosiveFeature implements ExplosiveFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaExplosiveFeature> DEFINED = new DefinedFeature<>(
			FeatureType.EXPLOSIVE, VanillaExplosiveFeature::new,
			FeatureType.EXPLOSION, FeatureType.ITEM_DAMAGE
	);

	private final FeatureConfiguration configuration;

	private ExplosionFeature explosionFeature;
	private ItemDamageFeature itemDamageFeature;

	public VanillaExplosiveFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.explosionFeature = this.configuration.get(FeatureType.EXPLOSION);
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
			ItemStack stack = event.getItemStack();
			Instance instance = event.getInstance();
			Point position = event.getPosition();
			Player player = event.getPlayer();

			if (stack.material() != Material.FLINT_AND_STEEL && stack.material() != Material.FIRE_CHARGE) return;
			Block block = instance.getBlock(position);
			if (!block.compare(Block.TNT)) return;

			this.installExplosionSupplier(instance);
            this.explosionFeature.primeExplosive(instance, position, new ByPlayer(player), 80);
			instance.setBlock(position, Block.AIR);

			if (player.getGameMode() != GameMode.CREATIVE) {
				if (stack.material() == Material.FLINT_AND_STEEL) {
                    this.itemDamageFeature.damageEquipment(player, event.getHand() == PlayerHand.MAIN
							? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, 1);
				} else {
					player.setItemInHand(event.getHand(), stack.consume(1));
				}
			}
		});

		node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
			if (event.getItemStack().material() != Material.END_CRYSTAL) return;
			Instance instance = event.getInstance();
			Block block = instance.getBlock(event.getPosition());
			if (!block.compare(Block.OBSIDIAN) && !block.compare(Block.BEDROCK)) return;

			this.installExplosionSupplier(instance);

			Point above = event.getPosition().add(0, 1, 0);
			if (!instance.getBlock(above).isAir()) return;

			Point spawnPosition = above.add(0.5, 0, 0.5);
			BoundingBox checkIntersect = new BoundingBox(1, 2, 1);
			for (Entity entity : instance.getNearbyEntities(above, 3)) {
				if (entity instanceof Player) continue;

				if (checkIntersect.intersectEntity(spawnPosition, entity)) return;
			}
			for (var player : instance.getPlayers()) {
				if (player.getGameMode() == GameMode.SPECTATOR) continue;

				if (checkIntersect.intersectEntity(spawnPosition, player)) return;
			}

			var crystalPlaceEvent = new CrystalPlaceEvent(event.getPlayer(), spawnPosition);

			EventDispatcher.callCancellable(crystalPlaceEvent, () -> {
				CrystalEntity entity = new CrystalEntity();
				entity.setInstance(instance, crystalPlaceEvent.getSpawnPosition());

				if (event.getPlayer().getGameMode() != GameMode.CREATIVE)
					event.getPlayer().setItemInHand(event.getHand(), event.getItemStack().consume(1));
			});
		});

		node.addListener(PlayerBlockInteractEvent.class, event -> {
			Instance instance = event.getInstance();
			Block block = instance.getBlock(event.getBlockPosition());
			Player player = event.getPlayer();
			if (!block.compare(Block.RESPAWN_ANCHOR)) return;

			// Exit if offhand has glowstone but current hand is main, to prevent exploding when it should be charged instead
			if (event.getHand() == PlayerHand.MAIN
					&& player.getItemInMainHand().material() != Material.GLOWSTONE
					&& player.getItemInOffHand().material() == Material.GLOWSTONE)
				return;

			ItemStack stack = player.getItemInHand(event.getHand());
			if (this.shouldSuppressAnchorUse(player)) return;

			int charges = Integer.parseInt(block.getProperty("charges"));
			if (stack.material() == Material.GLOWSTONE && charges < 4) {
				var anchorChargeEvent = new AnchorChargeEvent(player, event.getBlockPosition());
				EventDispatcher.call(anchorChargeEvent);

				if (!anchorChargeEvent.isCancelled()) {
					instance.setBlock(event.getBlockPosition(),
							block.withProperty("charges", String.valueOf(charges + 1)));
					ViewUtil.packetGroup(player).playSound(Sound.sound(
							SoundEvent.BLOCK_RESPAWN_ANCHOR_CHARGE, Source.BLOCK,
							1.0f, 1.0f
					), event.getBlockPosition().add(0.5, 0.5, 0.5));

					if (player.getGameMode() != GameMode.CREATIVE)
						player.setItemInHand(event.getHand(), player.getItemInHand(event.getHand()).consume(1));

					event.setBlockingItemUse(true);
					return;
				}
			}

			if (charges == 0) return;

            EnvironmentAttributeMap dim = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType()).attributes();

			boolean worksInDimension = (Boolean) dim.entries().get(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS).argument();

			Biome biome = MinecraftServer.getBiomeRegistry().get(instance.getChunkAt(event.getBlockPosition())
                .getBiome(event.getBlockPosition()));

			boolean respawnAnchorWorks;
			 if (biome.attributes().entries().containsKey(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS)) {
                 //noinspection unchecked
				 respawnAnchorWorks = ((Modifier<Boolean, Boolean>) biome.attributes().entries()
                     .get(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS).modifier())
                     .modify(worksInDimension, (Boolean) biome.attributes().entries()
                         .get(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS).argument());

			 } else {
                 respawnAnchorWorks = worksInDimension;
             }

			this.installExplosionSupplier(instance);

			if (instance.getExplosionSupplier() != null && !respawnAnchorWorks) {
				var anchorExplodeEvent = new AnchorExplodeEvent(player, event.getBlockPosition());
				EventDispatcher.callCancellable(anchorExplodeEvent, () -> {
					instance.setBlock(event.getBlockPosition(), Block.AIR);
					instance.explode(
							(float) (event.getBlockPosition().x() + 0.5),
							(float) (event.getBlockPosition().y() + 0.5),
							(float) (event.getBlockPosition().z() + 0.5),
							5.0f,
							CompoundBinaryTag.builder()
									.putBoolean("fire", true)
									.putBoolean("anchor", true)
									.build()
					);
					if (event.getHand() == PlayerHand.MAIN) {
						player.swingMainHand();
					} else {
						player.swingOffHand();
					}
				});
			}

			event.setBlockingItemUse(true);
		});
	}

	private void installExplosionSupplier(Instance instance) {
		if (instance.getExplosionSupplier() != null) return;

		var explosionSupplier = this.explosionFeature.getExplosionSupplier();
		if (explosionSupplier == null) return;

		instance.setExplosionSupplier(explosionSupplier);
	}

	private boolean shouldSuppressAnchorUse(Player player) {
		return (player.isSneaking() || player.inputs().shift())
				&& (!player.getItemInMainHand().isAir() || !player.getItemInOffHand().isAir());
	}
}
