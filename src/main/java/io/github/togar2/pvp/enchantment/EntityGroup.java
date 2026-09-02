package io.github.togar2.pvp.enchantment;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import org.jetbrains.annotations.Nullable;

public enum EntityGroup {
	UNDEAD("minecraft:sensitive_to_smite"),
	ARTHROPOD("minecraft:sensitive_to_bane_of_arthropods"),
	ILLAGER("minecraft:illager"),
	AQUATIC("minecraft:sensitive_to_impaling");

	private static final Key IGNORES_POISON_AND_REGEN = Key.key("minecraft:ignores_poison_and_regen");
	private static final Key INVERTED_HEALING_AND_HARM = Key.key("minecraft:inverted_healing_and_harm");

	private final Key tagKey;

	EntityGroup(String tagKey) {
		this.tagKey = Key.key(tagKey);
	}

	public boolean contains(@Nullable Entity entity) {
		return entity != null && this.contains(entity.getEntityType());
	}

	public boolean contains(EntityType entityType) {
		return isInTag(entityType, this.tagKey);
	}

	public static boolean ignoresPoisonAndRegen(Entity entity) {
		return isInTag(entity.getEntityType(), IGNORES_POISON_AND_REGEN);
	}

	public static boolean hasInvertedHealingAndHarm(Entity entity) {
		return isInTag(entity.getEntityType(), INVERTED_HEALING_AND_HARM);
	}

	private static boolean isInTag(EntityType entityType, Key tagKey) {
		var tag = MinecraftServer.process().entityType().getTag(tagKey);

		return tag != null && tag.contains(entityType);
	}
}
