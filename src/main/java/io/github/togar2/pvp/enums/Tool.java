package io.github.togar2.pvp.enums;

import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public enum Tool {
	WOODEN_SWORD(Kind.SWORD),
	STONE_SWORD(Kind.SWORD),
	COPPER_SWORD(Kind.SWORD),
	IRON_SWORD(Kind.SWORD),
	DIAMOND_SWORD(Kind.SWORD),
	GOLDEN_SWORD(Kind.SWORD),
	NETHERITE_SWORD(Kind.SWORD),

	WOODEN_SHOVEL(Kind.OTHER),
	STONE_SHOVEL(Kind.OTHER),
	COPPER_SHOVEL(Kind.OTHER),
	IRON_SHOVEL(Kind.OTHER),
	DIAMOND_SHOVEL(Kind.OTHER),
	GOLDEN_SHOVEL(Kind.OTHER),
	NETHERITE_SHOVEL(Kind.OTHER),

	WOODEN_PICKAXE(Kind.OTHER),
	STONE_PICKAXE(Kind.OTHER),
	COPPER_PICKAXE(Kind.OTHER),
	IRON_PICKAXE(Kind.OTHER),
	DIAMOND_PICKAXE(Kind.OTHER),
	GOLDEN_PICKAXE(Kind.OTHER),
	NETHERITE_PICKAXE(Kind.OTHER),

	WOODEN_AXE(Kind.AXE),
	STONE_AXE(Kind.AXE),
	COPPER_AXE(Kind.AXE),
	IRON_AXE(Kind.AXE),
	DIAMOND_AXE(Kind.AXE),
	GOLDEN_AXE(Kind.AXE),
	NETHERITE_AXE(Kind.AXE),

	WOODEN_HOE(Kind.OTHER),
	STONE_HOE(Kind.OTHER),
	COPPER_HOE(Kind.OTHER),
	IRON_HOE(Kind.OTHER),
	DIAMOND_HOE(Kind.OTHER),
	GOLDEN_HOE(Kind.OTHER),
	NETHERITE_HOE(Kind.OTHER),

	TRIDENT(Kind.OTHER),

	MACE(Kind.MACE),

	WOODEN_SPEAR(Kind.SPEAR),
	STONE_SPEAR(Kind.SPEAR),
	COPPER_SPEAR(Kind.SPEAR),
	IRON_SPEAR(Kind.SPEAR),
	GOLDEN_SPEAR(Kind.SPEAR),
	DIAMOND_SPEAR(Kind.SPEAR),
	NETHERITE_SPEAR(Kind.SPEAR);

	private static final Map<Material, Tool> BY_MATERIAL = new HashMap<>();

	private final Material material;
	private final Kind kind;

	Tool(Kind kind) {
		this.material = Material.fromKey(this.name().toLowerCase());
		this.kind = kind;
	}

	public boolean isAxe() {
		return this.kind == Kind.AXE;
	}

	public boolean isSword() {
		return this.kind == Kind.SWORD;
	}

	public boolean isMace() {
		return this.kind == Kind.MACE;
	}

	public boolean isSpear() {
		return this.kind == Kind.SPEAR;
	}

	public static @Nullable Tool fromMaterial(Material material) {
		return BY_MATERIAL.get(material);
	}

	private enum Kind {
		SWORD, AXE, MACE, SPEAR, OTHER
	}

	static {
		for (Tool tool : values()) {
			BY_MATERIAL.put(tool.material, tool);
		}
	}
}
