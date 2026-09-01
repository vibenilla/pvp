package io.github.togar2.pvp.feature.cooldown;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

/**
 * Vanilla implementation of {@link AttackCooldownFeature}
 */
public class VanillaAttackCooldownFeature implements AttackCooldownFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaAttackCooldownFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ATTACK_COOLDOWN, VanillaAttackCooldownFeature::new,
			FeatureType.VERSION
	);

	public static final Tag<Long> LAST_ATTACKED_TICKS = Tag.Long("lastAttackedTicks");
	public static final Tag<Material> LAST_MAIN_HAND_MATERIAL = Tag.Transient("lastMainHandMaterial");

	private final FeatureConfiguration configuration;
	private CombatVersion version;

	public VanillaAttackCooldownFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EventListener.builder(PlayerHandAnimationEvent.class).handler(event ->
                this.resetCooldownProgress(event.getPlayer())).build());

		node.addListener(PlayerTickEvent.class, event -> {
			var player = event.getPlayer();
			var material = player.getItemInMainHand().material();
			var lastMaterial = player.getTag(LAST_MAIN_HAND_MATERIAL);

			if (lastMaterial != null && lastMaterial != material) {
				this.resetCooldownProgress(player);
			}

			player.setTag(LAST_MAIN_HAND_MATERIAL, material);
		});
	}

	@Override
	public void resetCooldownProgress(Player player) {
		player.setTag(LAST_ATTACKED_TICKS, player.getAliveTicks());
	}

	@Override
	public double getAttackCooldownProgress(Player player) {
		return this.getAttackCooldownProgress(player, 0.5);
	}

	@Override
	public double getAttackCooldownProgress(Player player, double adjustTicks) {
		if (this.version.legacy()) return 1.0;

		Long lastAttacked = player.getTag(LAST_ATTACKED_TICKS);
		if (lastAttacked == null) return 1.0;

		long timeSinceLastAttacked = player.getAliveTicks() - lastAttacked;
		return Math.clamp(
				(timeSinceLastAttacked + adjustTicks) / this.getAttackCooldownProgressPerTick(player),
				0, 1
		);
	}

	protected double getAttackCooldownProgressPerTick(Player player) {
		return (1 / player.getAttributeValue(Attribute.ATTACK_SPEED)) * 20;
	}
}
