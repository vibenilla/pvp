package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.entity.explosion.TntEntity;
import io.github.togar2.pvp.events.ExplosivePrimeEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntitySpawnEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Vanilla implementation of {@link ExplosionFeature}
 * <p>
 * Provides an explosion supplier which can be registered to an instance,
 * see {@link VanillaExplosionFeature#getExplosionSupplier()}.
 */
public class VanillaExplosionFeature implements ExplosionFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaExplosionFeature> DEFINED = new DefinedFeature<>(
			FeatureType.EXPLOSION, VanillaExplosionFeature::new,
			FeatureType.ENCHANTMENT, FeatureType.FALL
	);

	private final FeatureConfiguration configuration;

	private VanillaExplosionSupplier explosionSupplier;

	public VanillaExplosionFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.explosionSupplier = new VanillaExplosionSupplier(
				this, this.configuration.get(FeatureType.ENCHANTMENT), this.configuration.get(FeatureType.FALL)
		);
	}

	@Override
	public VanillaExplosionSupplier getExplosionSupplier() {
		return this.explosionSupplier;
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntitySpawnEvent.class, event -> this.installExplosionSupplier(event.getSpawnInstance()));
	}

	public void installExplosionSupplier(Instance instance) {
		if (instance.getExplosionSupplier() != null) return;

		instance.setExplosionSupplier(this.explosionSupplier);
	}

	@Override
	public void primeExplosive(Instance instance, Point blockPosition, @NotNull IgnitionCause cause, int fuse) {
		this.installExplosionSupplier(instance);

		ExplosivePrimeEvent event = new ExplosivePrimeEvent(instance, blockPosition, cause, fuse);
		EventDispatcher.callCancellable(event, () -> {
			TntEntity entity = new TntEntity(cause.causingEntity());
			entity.setFuse(event.getFuse());
			entity.setInstance(instance, blockPosition.add(0.5, 0, 0.5));
			entity.getViewersAsAudience().playSound(Sound.sound(
					SoundEvent.ENTITY_TNT_PRIMED, Sound.Source.BLOCK,
					1.0f, 1.0f
			), entity);
		});
	}
}
