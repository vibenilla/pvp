package io.github.togar2.pvp.enchantment;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class EntityGroupTest {
    @Test
    public void groupsComeFromEntityTypeTags(Env env) {
        assertTrue(EntityGroup.UNDEAD.contains(EntityType.BOGGED));
        assertTrue(EntityGroup.UNDEAD.contains(EntityType.CAMEL_HUSK));
        assertTrue(EntityGroup.ARTHROPOD.contains(EntityType.BEE));
        assertTrue(EntityGroup.ILLAGER.contains(EntityType.PILLAGER));
        assertFalse(EntityGroup.UNDEAD.contains(EntityType.PLAYER));
        assertFalse(EntityGroup.AQUATIC.contains(EntityType.ZOMBIE));
        assertFalse(EntityGroup.UNDEAD.contains((Entity) null));
    }

    @Test
    public void entityCanBeInSeveralGroups(Env env) {
        assertTrue(EntityGroup.UNDEAD.contains(EntityType.ZOMBIE_NAUTILUS));
        assertTrue(EntityGroup.AQUATIC.contains(EntityType.ZOMBIE_NAUTILUS));
    }
}
