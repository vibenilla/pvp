package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.events.AnchorChargeEvent;
import io.github.togar2.pvp.events.AnchorExplodeEvent;
import io.github.togar2.pvp.events.BedExplodeEvent;
import io.github.togar2.pvp.events.CrystalPlaceEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.explosion.ExplosionFeature.IgnitionCause.ByPlayer;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.FluidUtil;
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
import net.minestom.server.world.attribute.BedRule;
import net.minestom.server.world.attribute.EnvironmentAttribute;
import net.minestom.server.world.attribute.EnvironmentAttribute.Modifier;
import net.minestom.server.world.attribute.EnvironmentAttributeMap;
import net.minestom.server.world.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link ExplosiveFeature}
 */
public class VanillaExplosiveFeature implements ExplosiveFeature, RegistrableFeature {
    private static final Set<String> WOOD_TYPES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "cherry",
            "dark_oak", "pale_oak", "mangrove", "bamboo"
    );
    private static final Set<String> LOG_WOOD_TYPES = Set.of(
            "oak", "spruce", "birch", "jungle", "acacia", "cherry",
            "dark_oak", "pale_oak", "mangrove"
    );
    private static final Set<String> COLORS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"
    );
    private static final Set<String> FLAMMABLE_BLOCKS = Set.of(
            "mangrove_roots", "bookshelf", "tnt", "short_grass", "fern",
            "dead_bush", "short_dry_grass", "tall_dry_grass", "sunflower",
            "lilac", "rose_bush", "peony", "tall_grass", "large_fern",
            "dandelion", "golden_dandelion", "poppy", "open_eyeblossom",
            "closed_eyeblossom", "blue_orchid", "allium", "azure_bluet",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy", "cornflower", "lily_of_the_valley", "torchflower",
            "pitcher_plant", "wither_rose", "pink_petals", "wildflowers",
            "leaf_litter", "cactus_flower", "vine", "coal_block", "hay_block",
            "target", "pale_moss_block", "pale_moss_carpet", "pale_hanging_moss",
            "dried_kelp_block", "bamboo", "bamboo_block", "stripped_bamboo_block",
            "bamboo_mosaic", "bamboo_mosaic_slab", "bamboo_mosaic_stairs",
            "scaffolding", "lectern", "composter", "sweet_berry_bush",
            "beehive", "bee_nest", "azalea_leaves", "flowering_azalea_leaves",
            "cave_vines", "cave_vines_plant", "spore_blossom", "azalea",
            "flowering_azalea", "big_dripleaf", "big_dripleaf_stem",
            "small_dripleaf", "hanging_roots", "glow_lichen", "firefly_bush",
            "bush"
    );

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
		node.addListener(PlayerUseItemOnBlockEvent.class, this::handleIgnitionItem);

		node.addListener(PlayerUseItemOnBlockEvent.class, event -> {
			if (event.getItemStack().material() != Material.END_CRYSTAL) return;
			Instance instance = event.getInstance();
			Block block = instance.getBlock(event.getPosition());
			if (!block.compare(Block.OBSIDIAN) && !block.compare(Block.BEDROCK)) return;

			this.installExplosionSupplier(instance);

			Point above = event.getPosition().add(0, 1, 0);
			if (!instance.getBlock(above).air()) return;

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

            EnvironmentAttributeMap dimension = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType()).attributes();
			var respawnAnchorEntry = dimension.entries().get(EnvironmentAttribute.RESPAWN_ANCHOR_WORKS);

			boolean worksInDimension = respawnAnchorEntry != null
					? (Boolean) respawnAnchorEntry.argument()
					: EnvironmentAttribute.RESPAWN_ANCHOR_WORKS.defaultValue();

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
									.putBoolean("anchorWater", this.isAnchorInWater(instance, event.getBlockPosition()))
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

		node.addListener(PlayerBlockInteractEvent.class, event -> {
			if (event.getHand() != PlayerHand.MAIN) return;

			Instance instance = event.getInstance();
			Point clickedPosition = event.getBlockPosition();
			Block clickedBlock = instance.getBlock(clickedPosition);
			if (!this.isBed(clickedBlock)) return;

			Player player = event.getPlayer();
			if (this.shouldSuppressBedUse(player)) return;

			BedRule bedRule = this.resolveBedRule(instance, clickedPosition);
			if (bedRule == null || !bedRule.explodes()) return;

			Point headPosition = clickedPosition;
			Block headBlock = clickedBlock;
			if (!"head".equals(clickedBlock.getProperty("part"))) {
				headPosition = this.offsetByFacing(clickedPosition, clickedBlock.getProperty("facing"));
				headBlock = instance.getBlock(headPosition);
				if (!this.isBed(headBlock)) return;
			}

			this.installExplosionSupplier(instance);
			if (instance.getExplosionSupplier() == null) return;

			Point finalHeadPosition = headPosition;
			Block finalHeadBlock = headBlock;
			var bedExplodeEvent = new BedExplodeEvent(player, finalHeadPosition);
			EventDispatcher.callCancellable(bedExplodeEvent, () -> {
				instance.setBlock(finalHeadPosition, Block.AIR);

				String facing = finalHeadBlock.getProperty("facing");
				Point footPosition = this.offsetByFacing(finalHeadPosition, this.opposite(facing));
				if (this.isBed(instance.getBlock(footPosition))) {
					instance.setBlock(footPosition, Block.AIR);
				}

				instance.explode(
						(float) (finalHeadPosition.x() + 0.5),
						(float) (finalHeadPosition.y() + 0.5),
						(float) (finalHeadPosition.z() + 0.5),
						5.0F,
						CompoundBinaryTag.builder()
								.putBoolean("fire", true)
								.putBoolean("anchor", true)
								.build()
				);
			});

			event.setBlockingItemUse(true);
		});
	}

	private boolean isBed(Block block) {
		return block.key().value().endsWith("_bed");
	}

    private void handleIgnitionItem(PlayerUseItemOnBlockEvent event) {
        var stack = event.getItemStack();
        if (!this.isIgnitionItem(stack.material())) return;

        var instance = event.getInstance();
        var position = event.getPosition();
        var player = event.getPlayer();
        var block = instance.getBlock(position);

        if (block.compare(Block.TNT)) {
            this.installExplosionSupplier(instance);
            this.explosionFeature.primeExplosive(instance, position, new ByPlayer(player), 80);
            instance.setBlock(position, Block.AIR);
            this.useIgnitionItem(player, event.getHand(), stack);
            return;
        }

        if (this.lightBlock(instance, position, block, stack.material(), player)) {
            this.useIgnitionItem(player, event.getHand(), stack);
            return;
        }

        var firePosition = position.relative(event.getBlockFace());
        if (!this.canPlaceFireAt(instance, firePosition)) return;

        this.playIgnitionSound(player, stack.material(), firePosition);
        instance.setBlock(firePosition, this.createFireBlock(instance, firePosition));
        this.useIgnitionItem(player, event.getHand(), stack);
    }

    private boolean isIgnitionItem(Material material) {
        return material == Material.FLINT_AND_STEEL || material == Material.FIRE_CHARGE;
    }

    private boolean lightBlock(Instance instance, Point position, Block block, Material material, Player player) {
        if (!this.canLight(block)) return false;

        this.playIgnitionSound(player, material, position);
        instance.setBlock(position, block.withProperty("lit", "true"));
        return true;
    }

    private boolean canLight(Block block) {
        var lit = block.getProperty("lit");
        if (!"false".equals(lit)) return false;

        var blockKey = block.key().value();
        if (blockKey.endsWith("campfire")) {
            return "false".equals(block.getProperty("waterlogged"));
        }

        if (this.isCandle(blockKey)) {
            return "false".equals(block.getProperty("waterlogged"));
        }

        return this.isCandleCake(blockKey);
    }

    private boolean isCandle(String blockKey) {
        return "candle".equals(blockKey) || blockKey.endsWith("_candle") && !blockKey.endsWith("_candle_cake");
    }

    private boolean isCandleCake(String blockKey) {
        return "candle_cake".equals(blockKey) || blockKey.endsWith("_candle_cake");
    }

    private boolean canPlaceFireAt(Instance instance, Point position) {
        if (!instance.getBlock(position).air()) return false;

        var belowBlock = instance.getBlock(position.add(0, -1, 0));
        if (this.isSoulFireBase(belowBlock)) return true;

        return belowBlock.solid() || this.hasFlammableNeighbor(instance, position);
    }

    private Block createFireBlock(Instance instance, Point position) {
        var belowBlock = instance.getBlock(position.add(0, -1, 0));
        if (this.isSoulFireBase(belowBlock)) return Block.SOUL_FIRE;

        var fire = Block.FIRE.withProperty("age", "0");
        if (belowBlock.solid() || this.isFlammable(belowBlock)) return fire;

        return fire
                .withProperty("north", String.valueOf(this.isFlammable(instance.getBlock(position.add(0, 0, -1)))))
                .withProperty("east", String.valueOf(this.isFlammable(instance.getBlock(position.add(1, 0, 0)))))
                .withProperty("south", String.valueOf(this.isFlammable(instance.getBlock(position.add(0, 0, 1)))))
                .withProperty("west", String.valueOf(this.isFlammable(instance.getBlock(position.add(-1, 0, 0)))))
                .withProperty("up", String.valueOf(this.isFlammable(instance.getBlock(position.add(0, 1, 0)))));
    }

    private boolean hasFlammableNeighbor(Instance instance, Point position) {
        return this.isFlammable(instance.getBlock(position.add(0, -1, 0)))
                || this.isFlammable(instance.getBlock(position.add(0, 1, 0)))
                || this.isFlammable(instance.getBlock(position.add(0, 0, -1)))
                || this.isFlammable(instance.getBlock(position.add(0, 0, 1)))
                || this.isFlammable(instance.getBlock(position.add(-1, 0, 0)))
                || this.isFlammable(instance.getBlock(position.add(1, 0, 0)));
    }

    private boolean isSoulFireBase(Block block) {
        return block.compare(Block.SOUL_SAND) || block.compare(Block.SOUL_SOIL);
    }

    private boolean isFlammable(Block block) {
        var blockKey = block.key().value();
        if (FLAMMABLE_BLOCKS.contains(blockKey)) return true;

        for (var woodType : WOOD_TYPES) {

            if (blockKey.equals(woodType + "_planks")
                    || blockKey.equals(woodType + "_slab")
                    || blockKey.equals(woodType + "_fence_gate")
                    || blockKey.equals(woodType + "_fence")
                    || blockKey.equals(woodType + "_stairs")
                    || blockKey.equals(woodType + "_shelf")) {
                return true;
            }
        }

        for (var woodType : LOG_WOOD_TYPES) {

            if (blockKey.equals(woodType + "_log")
                    || blockKey.equals("stripped_" + woodType + "_log")
                    || blockKey.equals(woodType + "_wood")
                    || blockKey.equals("stripped_" + woodType + "_wood")
                    || blockKey.equals(woodType + "_leaves")) {
                return true;
            }
        }

        for (var color : COLORS) {

            if (blockKey.equals(color + "_wool") || blockKey.equals(color + "_carpet")) {
                return true;
            }
        }

        return false;
    }

    private void useIgnitionItem(Player player, PlayerHand hand, ItemStack stack) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        if (stack.material() == Material.FLINT_AND_STEEL) {
            this.itemDamageFeature.damageEquipment(player, hand == PlayerHand.MAIN
                    ? EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, 1);
        } else {
            player.setItemInHand(hand, stack.consume(1));
        }
    }

    private void playIgnitionSound(Player player, Material material, Point position) {
        var random = ThreadLocalRandom.current();
        var soundEvent = material == Material.FLINT_AND_STEEL
                ? SoundEvent.ITEM_FLINTANDSTEEL_USE : SoundEvent.ITEM_FIRECHARGE_USE;
        var pitch = material == Material.FLINT_AND_STEEL
                ? random.nextFloat() * 0.4F + 0.8F : (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;

        ViewUtil.packetGroup(player).playSound(Sound.sound(
                soundEvent, Source.BLOCK,
                1.0F, pitch
        ), position);
    }

	private boolean shouldSuppressBedUse(Player player) {
		return (player.isSneaking() || player.inputs().shift())
				&& (!player.getItemInMainHand().isAir() || !player.getItemInOffHand().isAir());
	}

	private @Nullable BedRule resolveBedRule(Instance instance, Point position) {
		EnvironmentAttributeMap dim = MinecraftServer.getDimensionTypeRegistry().get(instance.getDimensionType()).attributes();
		var dimensionEntry = dim.entries().get(EnvironmentAttribute.BED_RULE);
		if (dimensionEntry == null) return null;

		BedRule dimensionBedRule = (BedRule) dimensionEntry.argument();

		Biome biome = MinecraftServer.getBiomeRegistry().get(instance.getChunkAt(position).getBiome(position));
		if (biome.attributes().entries().containsKey(EnvironmentAttribute.BED_RULE)) {
			//noinspection unchecked
			return ((Modifier<BedRule, BedRule>) biome.attributes().entries()
					.get(EnvironmentAttribute.BED_RULE).modifier())
					.modify(dimensionBedRule, (BedRule) biome.attributes().entries()
							.get(EnvironmentAttribute.BED_RULE).argument());
		}

		return dimensionBedRule;
	}

	private Point offsetByFacing(Point position, String facing) {
		if (facing == null) return position;

		return switch (facing) {
			case "north" -> position.add(0, 0, -1);
			case "south" -> position.add(0, 0, 1);
			case "east" -> position.add(1, 0, 0);
			case "west" -> position.add(-1, 0, 0);
			default -> position;
		};
	}

	private String opposite(String facing) {
		if (facing == null) return null;

		return switch (facing) {
			case "north" -> "south";
			case "south" -> "north";
			case "east" -> "west";
			case "west" -> "east";
			default -> facing;
		};
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

	private boolean isAnchorInWater(Instance instance, Point position) {
		if (FluidUtil.isWater(instance.getBlock(position.add(0, 1, 0)))) return true;

		return this.isWaterThatWouldFlow(instance, position.add(1, 0, 0))
				|| this.isWaterThatWouldFlow(instance, position.add(-1, 0, 0))
				|| this.isWaterThatWouldFlow(instance, position.add(0, 0, 1))
				|| this.isWaterThatWouldFlow(instance, position.add(0, 0, -1));
	}

	private boolean isWaterThatWouldFlow(Instance instance, Point position) {
		var block = instance.getBlock(position);
		if (!FluidUtil.isWater(block)) return false;

		var level = block.getProperty("level");
		if (level == null || "0".equals(level)) return true;

		var amount = "8".equals(level) ? 8 : 8 - Integer.parseInt(level);
		if (amount < 2) return false;

		return !FluidUtil.isWater(instance.getBlock(position.sub(0, 1, 0)));
	}
}
