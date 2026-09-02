package io.github.togar2.pvp.damage.combat;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.EntityUtil;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.RegistryTags;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.EndCombatEventPacket;
import net.minestom.server.network.packet.server.play.EnterCombatEventPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CombatManager {
	private static final Component BAD_RESPAWN_POINT_MESSAGE = Component.text("[")
			.append(Component.translatable("death.attack.badRespawnPoint.link")
					.clickEvent(ClickEvent.openUrl("https://bugs.mojang.com/browse/MCPE-28723"))
					.hoverEvent(HoverEvent.showText(Component.text("MCPE-28723"))))
			.append(Component.text("]"));

	private static final int HURT_MEMORY_TICKS = 100;

	private final List<CombatEntry> entries = new ArrayList<>();
	private final Player player;
	private int lastHurtByPlayer = -1;
	private int lastHurtByPlayerMemoryTicks;
	private int lastHurtByMob = -1;
	private long lastHurtByMobTick;
	private long lastDamageTick;
	private long combatStartTick;
	private long combatEndTick;
	private boolean inCombat;
	private boolean takingDamage;

	public CombatManager(Player player) {
		this.player = player;
	}

	public @Nullable String getFallLocation(PlayerStateFeature playerStateFeature) {
		Block lastClimbedBlock = playerStateFeature.getLastClimbedBlock(this.player);
		if (lastClimbedBlock == null) {
			if (FluidUtil.isTouchingWater(this.player)) return "water";

			return null;
		}

		if (lastClimbedBlock.compare(Block.LADDER) || this.isTrapdoor(lastClimbedBlock)) {
			return "ladder";
		}

		if (lastClimbedBlock.compare(Block.VINE)) {
			return "vines";
		}

		if (lastClimbedBlock.compare(Block.WEEPING_VINES) || lastClimbedBlock.compare(Block.WEEPING_VINES_PLANT)) {
			return "weeping_vines";
		}

		if (lastClimbedBlock.compare(Block.TWISTING_VINES) || lastClimbedBlock.compare(Block.TWISTING_VINES_PLANT)) {
			return "twisting_vines";
		}

		if (lastClimbedBlock.compare(Block.SCAFFOLDING)) {
			return "scaffolding";
		}

		return "other_climbable";
	}

	private boolean isTrapdoor(Block block) {
		return RegistryTags.contains(RegistryTags.TRAPDOORS, block);
	}

	public void recordDamage(int attackerId, Damage damage,
	                         FallFeature fallFeature, PlayerStateFeature playerStateFeature) {
        this.recheckStatus();

		CombatEntry entry = new CombatEntry(damage, this.getFallLocation(playerStateFeature), fallFeature.getFallDistance(this.player));
        this.entries.add(entry);

		long now = this.player.getAliveTicks();
		if (damage.getAttacker() instanceof LivingEntity attacker) {
			this.lastHurtByMob = attackerId;
			this.lastHurtByMobTick = now;
			if (attacker instanceof Player) {
				this.lastHurtByPlayer = attackerId;
				this.lastHurtByPlayerMemoryTicks = HURT_MEMORY_TICKS;
			}
		}
        this.lastDamageTick = now;
        this.takingDamage = true;

		if (entry.isCombat() && !this.inCombat && !this.player.isDead()) {
            this.inCombat = true;
            this.combatStartTick = now;
            this.combatEndTick = now;

            this.onEnterCombat();
		}
	}

	public Component getDeathMessage() {
		if (this.entries.isEmpty()) {
			return Component.translatable("death.attack.generic", this.getEntityName());
		}

		CombatEntry heaviestFall = null;
		CombatEntry lastEntry = this.entries.getLast();
		DamageTypeInfo lastInfo = DamageTypeInfo.of(lastEntry.damage().getType());

		boolean fall = false;
		if (lastInfo.fall()) {
			heaviestFall = this.getHeaviestFall();
			fall = heaviestFall != null;
		}

		if (!fall) return this.getAttackDeathMessage(lastEntry.damage());

		DamageTypeInfo heaviestFallInfo = DamageTypeInfo.of(heaviestFall.damage().getType());
		if (heaviestFallInfo.fall() || heaviestFallInfo.outOfWorld()) {
			return Component.translatable("death.fell.accident." + heaviestFall.getMessageFallLocation(), this.getEntityName());
		}

		Entity firstAttacker = heaviestFall.getAttacker();
		Entity lastAttacker = lastEntry.getAttacker();

		if (firstAttacker != null && firstAttacker != lastAttacker) {
			ItemStack weapon = firstAttacker instanceof LivingEntity ? ((LivingEntity) firstAttacker).getItemInMainHand() : ItemStack.AIR;
			if (!weapon.isAir() && weapon.has(DataComponents.CUSTOM_NAME)) {
				return Component.translatable("death.fell.assist.item", this.getEntityName(), EntityUtil.getName(firstAttacker), weapon.get(DataComponents.CUSTOM_NAME));
			} else {
				return Component.translatable("death.fell.assist", this.getEntityName(), EntityUtil.getName(firstAttacker));
			}
		} else if (lastAttacker != null) {
			ItemStack weapon = lastAttacker instanceof LivingEntity ? ((LivingEntity) lastAttacker).getItemInMainHand() : ItemStack.AIR;
			if (!weapon.isAir() && weapon.has(DataComponents.CUSTOM_NAME)) {
				return Component.translatable("death.fell.finish.item", this.getEntityName(), EntityUtil.getName(lastAttacker), weapon.get(DataComponents.CUSTOM_NAME));
			} else {
				return Component.translatable("death.fell.finish", this.getEntityName(), EntityUtil.getName(lastAttacker));
			}
		} else {
			return Component.translatable("death.fell.killer", this.getEntityName());
		}
	}

	private Component getAttackDeathMessage(@NotNull Damage damage) {
		if (damage.getType() == DamageType.BAD_RESPAWN_POINT) {
			return Component.translatable("death.attack.badRespawnPoint.message", this.player.getName(), BAD_RESPAWN_POINT_MESSAGE);
		}

		DamageType damageType = MinecraftServer.getDamageTypeRegistry().get(damage.getType());
		if (damageType == null) return Component.empty();
		String id = "death.attack." + damageType.messageId();

		Entity source = damage.getSource();
		Entity attacker = damage.getAttacker();

		if (source != null) {
			Component ownerName = attacker == null ? EntityUtil.getName(source) : EntityUtil.getName(attacker);
			ItemStack weapon = source instanceof LivingEntity living ? living.getItemInMainHand() : ItemStack.AIR;
			if (!weapon.isAir() && weapon.has(DataComponents.CUSTOM_NAME)) {
				return Component.translatable(id + ".item", EntityUtil.getName(this.player), ownerName, weapon.get(DataComponents.CUSTOM_NAME));
			} else {
				return Component.translatable(id, EntityUtil.getName(this.player), ownerName);
			}
		} else {
			LivingEntity killer = this.getKillCredit();
			if (killer == null) {
				return Component.translatable(id, EntityUtil.getName(this.player));
			} else {
				return Component.translatable(id + ".player", EntityUtil.getName(this.player),
						EntityUtil.getName(killer));
			}
		}
	}

	private @Nullable LivingEntity getKillCredit() {
		LivingEntity player = this.getLivingEntity(this.lastHurtByPlayer);
		if (player != null) return player;

		return this.getLivingEntity(this.lastHurtByMob);
	}

	private @Nullable LivingEntity getLivingEntity(int entityId) {
		if (entityId == -1) return null;

		var instance = this.player.getInstance();
		if (instance == null) return null;

		return instance.getEntityById(entityId) instanceof LivingEntity living ? living : null;
	}

	public @Nullable CombatEntry getHeaviestFall() {
		CombatEntry mostDamageEntry = null;
		CombatEntry highestFallEntry = null;
		float mostDamage = 0.0F;
		double highestFall = 0.0F;

		for (int i = 0; i < this.entries.size(); i++) {
			CombatEntry entry = this.entries.get(i);
			DamageTypeInfo info = DamageTypeInfo.of(entry.damage().getType());

			if ((info.fall() || info.outOfWorld())
					&& entry.getFallDistance() > 0.0 && (mostDamageEntry == null || entry.getFallDistance() > highestFall)) {
				if (i > 0) {
					mostDamageEntry = this.entries.get(i - 1);
				} else {
					mostDamageEntry = entry;
				}

				highestFall = entry.getFallDistance();
			}

			if (entry.fallLocation() != null && (highestFallEntry == null || entry.damage().getAmount() > mostDamage)) {
				highestFallEntry = entry;
				mostDamage = entry.damage().getAmount();
			}
		}

		if (highestFall > 5.0 && mostDamageEntry != null) {
			return mostDamageEntry;
		} else if (mostDamage > 5.0F) {
			return highestFallEntry;
		} else {
			return null;
		}
	}

	public long getCombatDuration() {
		return this.inCombat ? this.player.getAliveTicks() - this.combatStartTick : this.combatEndTick - this.combatStartTick;
	}

	public void tick() {
		if (this.player.isDead() || this.player.getAliveTicks() % 20 == 0)
            this.recheckStatus();

		if (this.lastHurtByPlayer != -1) {
			var lastPlayer = this.getLivingEntity(this.lastHurtByPlayer);
			if (lastPlayer == null || lastPlayer.isDead() || --this.lastHurtByPlayerMemoryTicks <= 0) {
				this.lastHurtByPlayer = -1;
			}
		}

		if (this.lastHurtByMob != -1) {
			var lastMob = this.getLivingEntity(this.lastHurtByMob);
			if (lastMob == null || lastMob.isDead() || this.player.getAliveTicks() - this.lastHurtByMobTick > HURT_MEMORY_TICKS) {
				this.lastHurtByMob = -1;
			}
		}
	}

	public void recheckStatus() {
		// Check if combat should end
		int idleTicks = this.inCombat ? 300 : 100;
		if (this.takingDamage && (this.player.isDead() || this.player.getAliveTicks() - this.lastDamageTick > idleTicks)) {
            this.reset();
            this.combatEndTick = this.player.getAliveTicks();
		}
	}

	public void reset() {
		boolean wasInCombat = this.inCombat;
        this.takingDamage = false;
        this.inCombat = false;

		if (wasInCombat) {
            this.onLeaveCombat();
		}

        this.entries.clear();
	}

	public Component getEntityName() {
		return EntityUtil.getName(this.player);
	}

	private void onEnterCombat() {
        this.player.getPlayerConnection().sendPacket(new EnterCombatEventPacket());
	}

	private void onLeaveCombat() {
        this.player.getPlayerConnection().sendPacket(new EndCombatEventPacket((int) this.getCombatDuration()));
	}

	public List<CombatEntry> getEntries() {
		return this.entries;
	}

	public Player getPlayer() {
		return this.player;
	}

	public long getLastDamageTick() {
		return this.lastDamageTick;
	}

	public long getCombatStartTick() {
		return this.combatStartTick;
	}

	public long getCombatEndTick() {
		return this.combatEndTick;
	}

	public boolean isInCombat() {
		return this.inCombat;
	}

	public boolean isTakingDamage() {
		return this.takingDamage;
	}
}
