package io.github.togar2.pvp.potion.effect;

import io.github.togar2.pvp.feature.effect.VanillaEffectFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.minestom.server.potion.PotionEffect;

public class AbsorptionPotionEffect extends CombatPotionEffect {
	private static final float ABSORPTION_PER_LEVEL = 4.0F;

	public AbsorptionPotionEffect() {
		super(PotionEffect.ABSORPTION);
		this.addAttributeModifier(Attribute.MAX_ABSORPTION, Key.key("minecraft:effect.absorption"),
				ABSORPTION_PER_LEVEL, AttributeOperation.ADD_VALUE);
	}

	@Override
	public void onStarted(LivingEntity entity, int amplifier) {
		if (entity instanceof Player player) {
			player.setAdditionalHearts(Math.max(player.getAdditionalHearts(), getAbsorption(amplifier)));
		}
	}

	@Override
	public void onRemoved(LivingEntity entity, int amplifier, CombatVersion version) {
		super.onRemoved(entity, amplifier, version);

		if (entity instanceof Player player) {
			int hiddenAmplifier = VanillaEffectFeature.getHiddenAmplifier(entity, PotionEffect.ABSORPTION);
			float cap = hiddenAmplifier < 0 ? 0.0F : getAbsorption(hiddenAmplifier);
			player.setAdditionalHearts(Math.min(player.getAdditionalHearts(), cap));
		}
	}

	private static float getAbsorption(int amplifier) {
		return ABSORPTION_PER_LEVEL * (amplifier + 1);
	}
}
