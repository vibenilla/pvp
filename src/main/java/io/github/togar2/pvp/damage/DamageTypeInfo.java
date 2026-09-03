package io.github.togar2.pvp.damage;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.registry.RegistryKey;

import java.util.HashMap;
import java.util.Map;

public record DamageTypeInfo(boolean damagesHelmet, boolean bypassesArmor, boolean outOfWorld,
                             boolean unblockable, boolean bypassesResistance, boolean bypassesEnchantments,
                             boolean fire,
                             boolean magic, boolean explosive, boolean fall, boolean thorns, boolean projectile,
                             boolean freeze) {
    private static final DamageTypeInfo DEFAULT = new DamageTypeInfo();

    public DamageTypeInfo() {
        this(
                false, false, false,
                false, false, false,
                false,
                false, false, false, false, false, false
        );
    }

    public static DamageTypeInfo of(RegistryKey<DamageType> type) {
        return INFO_MAP.getOrDefault(type, DEFAULT);
    }

    public static final Map<RegistryKey<DamageType>, DamageTypeInfo> INFO_MAP = new HashMap<>() {
        {
            this.put(DamageType.IN_FIRE, new DamageTypeInfo().fire(true));
            this.put(DamageType.ON_FIRE, new DamageTypeInfo().bypassesArmor(true).fire(true));
            this.put(DamageType.LAVA, new DamageTypeInfo().fire(true));
            this.put(DamageType.HOT_FLOOR, new DamageTypeInfo().fire(true));
            this.put(RegistryKey.unsafeOf("minecraft:sulfur_cube_hot"), new DamageTypeInfo().fire(true));
            this.put(DamageType.IN_WALL, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.CRAMMING, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.DROWN, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.STARVE, new DamageTypeInfo().bypassesArmor(true).unblockable(true));
            this.put(DamageType.FALL, new DamageTypeInfo().bypassesArmor(true).fall(true));
            this.put(DamageType.ENDER_PEARL, new DamageTypeInfo().bypassesArmor(true).fall(true));
            this.put(DamageType.FLY_INTO_WALL, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.OUT_OF_WORLD, new DamageTypeInfo().bypassesArmor(true).outOfWorld(true).bypassesResistance(true));
            this.put(DamageType.GENERIC, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.GENERIC_KILL, new DamageTypeInfo().bypassesArmor(true).bypassesResistance(true));
            this.put(DamageType.MAGIC, new DamageTypeInfo().bypassesArmor(true).magic(true));
            this.put(DamageType.INDIRECT_MAGIC, new DamageTypeInfo().bypassesArmor(true).magic(true));
            this.put(DamageType.WITHER, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.FALLING_ANVIL, new DamageTypeInfo().damagesHelmet(true));
            this.put(DamageType.FALLING_BLOCK, new DamageTypeInfo().damagesHelmet(true));
            this.put(DamageType.DRAGON_BREATH, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.DRY_OUT, new DamageTypeInfo());
            this.put(DamageType.FREEZE, new DamageTypeInfo().freeze(true).bypassesArmor(true));
            this.put(DamageType.FALLING_STALACTITE, new DamageTypeInfo().damagesHelmet(true));
            this.put(DamageType.STALAGMITE, new DamageTypeInfo().bypassesArmor(true).fall(true));
            this.put(DamageType.THORNS, new DamageTypeInfo().magic(true).thorns(true));
            this.put(DamageType.EXPLOSION, new DamageTypeInfo().explosive(true));
            this.put(DamageType.PLAYER_EXPLOSION, new DamageTypeInfo().explosive(true));
            this.put(DamageType.BAD_RESPAWN_POINT, new DamageTypeInfo().explosive(true));
            this.put(DamageType.FIREBALL, new DamageTypeInfo().projectile(true).fire(true));
            this.put(DamageType.UNATTRIBUTED_FIREBALL, new DamageTypeInfo().projectile(true).fire(true));
            this.put(DamageType.ARROW, new DamageTypeInfo().projectile(true));
            this.put(DamageType.WITHER_SKULL, new DamageTypeInfo().projectile(true));
            this.put(DamageType.THROWN, new DamageTypeInfo().projectile(true));
            this.put(DamageType.STING, new DamageTypeInfo());
            this.put(DamageType.MOB_ATTACK, new DamageTypeInfo());
            this.put(DamageType.MOB_ATTACK_NO_AGGRO, new DamageTypeInfo());
            this.put(DamageType.MOB_PROJECTILE, new DamageTypeInfo().projectile(true));
            this.put(DamageType.SPIT, new DamageTypeInfo());
            this.put(DamageType.PLAYER_ATTACK, new DamageTypeInfo());
            this.put(DamageType.TRIDENT, new DamageTypeInfo().projectile(true));
            this.put(DamageType.MACE_SMASH, new DamageTypeInfo());
            this.put(DamageType.SPEAR, new DamageTypeInfo());
            this.put(DamageType.WIND_CHARGE, new DamageTypeInfo().projectile(true));
            this.put(DamageType.FIREWORKS, new DamageTypeInfo().explosive(true));
            this.put(DamageType.SONIC_BOOM, new DamageTypeInfo().bypassesArmor(true).bypassesEnchantments(true));
            this.put(DamageType.OUTSIDE_BORDER, new DamageTypeInfo().bypassesArmor(true));
            this.put(DamageType.CAMPFIRE, new DamageTypeInfo().fire(true));
            this.put(DamageType.LIGHTNING_BOLT, new DamageTypeInfo());
            this.put(DamageType.CACTUS, new DamageTypeInfo());
            this.put(DamageType.SWEET_BERRY_BUSH, new DamageTypeInfo());
        }
    };

    public DamageTypeInfo damagesHelmet(boolean damagesHelmet) {
        return new DamageTypeInfo(
                damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo bypassesArmor(boolean bypassesArmor) {
        return new DamageTypeInfo(
                this.damagesHelmet, bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo outOfWorld(boolean outOfWorld) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo unblockable(boolean unblockable) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo bypassesResistance(boolean bypassesResistance) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo bypassesEnchantments(boolean bypassesEnchantments) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo fire(boolean fire) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo magic(boolean magic) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                magic, this.explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo explosive(boolean explosive) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, explosive, this.fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo fall(boolean fall) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, fall, this.thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo thorns(boolean thorns) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, thorns, this.projectile,
                this.freeze
        );
    }

    public DamageTypeInfo projectile(boolean projectile) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, projectile,
                this.freeze
        );
    }

    public DamageTypeInfo freeze(boolean freeze) {
        return new DamageTypeInfo(
                this.damagesHelmet, this.bypassesArmor, this.outOfWorld,
                this.unblockable, this.bypassesResistance, this.bypassesEnchantments,
                this.fire,
                this.magic, this.explosive, this.fall, this.thorns, this.projectile,
                freeze
        );
    }

    public boolean shouldScaleWithDifficulty(Damage damage) {
        var damageType = MinecraftServer.getDamageTypeRegistry().get(damage.getType());
        if (damageType == null) return false;

        return switch (damageType.scaling()) {
            case "always" -> true;
            case "when_caused_by_living_non_player" ->
                    damage.getAttacker() instanceof LivingEntity living && !(living instanceof Player);
            default -> false;
        };
    }
}
