package io.github.togar2.pvp.enchantment;

import io.github.togar2.pvp.utils.RegistryTags;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

public enum EntityGroup {
	UNDEAD(RegistryTags.SENSITIVE_TO_SMITE),
	ARTHROPOD(RegistryTags.SENSITIVE_TO_BANE_OF_ARTHROPODS),
	ILLAGER(RegistryTags.ILLAGER),
	AQUATIC(RegistryTags.SENSITIVE_TO_IMPALING);

	private final @Nullable RegistryTag<EntityType> tag;

	EntityGroup(@Nullable RegistryTag<EntityType> tag) {
		this.tag = tag;
	}

	public boolean contains(@Nullable Entity entity) {
		return entity != null && this.contains(entity.getEntityType());
	}

	public boolean contains(EntityType entityType) {
		return RegistryTags.contains(this.tag, entityType);
	}

	public static boolean ignoresPoisonAndRegen(Entity entity) {
		return RegistryTags.contains(RegistryTags.IGNORES_POISON_AND_REGEN, entity.getEntityType());
	}

	public static boolean hasInvertedHealingAndHarm(Entity entity) {
		return RegistryTags.contains(RegistryTags.INVERTED_HEALING_AND_HARM, entity.getEntityType());
	}
}
