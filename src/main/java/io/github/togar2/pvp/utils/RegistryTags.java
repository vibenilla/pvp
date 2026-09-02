package io.github.togar2.pvp.utils;

import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.Material;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

public final class RegistryTags {
	public static final @Nullable RegistryTag<Block> FALL_DAMAGE_RESETTING = block("minecraft:fall_damage_resetting");
	public static final @Nullable RegistryTag<Block> FENCES = block("minecraft:fences");
	public static final @Nullable RegistryTag<Block> WALLS = block("minecraft:walls");
	public static final @Nullable RegistryTag<Block> FENCE_GATES = block("minecraft:fence_gates");
	public static final @Nullable RegistryTag<Block> LEAVES = block("minecraft:leaves");
	public static final @Nullable RegistryTag<Block> TRAPDOORS = block("minecraft:trapdoors");

	public static final @Nullable RegistryTag<Material> SWORDS = material("minecraft:swords");
	public static final @Nullable RegistryTag<Material> ENCHANTABLE_ARMOR = material("minecraft:enchantable/armor");
	public static final @Nullable RegistryTag<Material> FREEZE_IMMUNE_WEARABLES = material("minecraft:freeze_immune_wearables");

	public static final @Nullable RegistryTag<EntityType> FALL_DAMAGE_IMMUNE = entityType("minecraft:fall_damage_immune");
	public static final @Nullable RegistryTag<EntityType> FREEZE_IMMUNE_ENTITY_TYPES = entityType("minecraft:freeze_immune_entity_types");
	public static final @Nullable RegistryTag<EntityType> FREEZE_HURTS_EXTRA_TYPES = entityType("minecraft:freeze_hurts_extra_types");
	public static final @Nullable RegistryTag<EntityType> CAN_BREATHE_UNDER_WATER = entityType("minecraft:can_breathe_under_water");
	public static final @Nullable RegistryTag<EntityType> SENSITIVE_TO_SMITE = entityType("minecraft:sensitive_to_smite");
	public static final @Nullable RegistryTag<EntityType> SENSITIVE_TO_BANE_OF_ARTHROPODS = entityType("minecraft:sensitive_to_bane_of_arthropods");
	public static final @Nullable RegistryTag<EntityType> SENSITIVE_TO_IMPALING = entityType("minecraft:sensitive_to_impaling");
	public static final @Nullable RegistryTag<EntityType> ILLAGER = entityType("minecraft:illager");
	public static final @Nullable RegistryTag<EntityType> IGNORES_POISON_AND_REGEN = entityType("minecraft:ignores_poison_and_regen");
	public static final @Nullable RegistryTag<EntityType> INVERTED_HEALING_AND_HARM = entityType("minecraft:inverted_healing_and_harm");

	private RegistryTags() {
	}

	public static <T> boolean contains(@Nullable RegistryTag<T> tag, RegistryKey<T> key) {
		return tag != null && tag.contains(key);
	}

	private static @Nullable RegistryTag<Block> block(String key) {
		return Block.staticRegistry().getTag(Key.key(key));
	}

	private static @Nullable RegistryTag<Material> material(String key) {
		return Material.staticRegistry().getTag(Key.key(key));
	}

	private static @Nullable RegistryTag<EntityType> entityType(String key) {
		return EntityType.staticRegistry().getTag(Key.key(key));
	}
}
