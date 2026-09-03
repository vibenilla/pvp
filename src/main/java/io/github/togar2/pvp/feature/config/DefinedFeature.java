package io.github.togar2.pvp.feature.config;

import io.github.togar2.pvp.feature.CombatFeature;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class DefinedFeature<F extends CombatFeature> {
    private final FeatureType<?> featureType;
    private final Set<FeatureType<?>> dependencies;
    private final Constructor<F> constructor;
    private final PlayerInit playerInit;

    public DefinedFeature(FeatureType<? super F> featureType, Constructor<F> constructor,
                          FeatureType<?>... dependencies) {
        this(featureType, constructor, null, dependencies);
    }

    public DefinedFeature(FeatureType<? super F> featureType, Constructor<F> constructor,
                          @Nullable PlayerInit playerInit, FeatureType<?>... dependencies) {
        this.featureType = featureType;
        this.dependencies = Set.of(dependencies);
        this.constructor = constructor;
        this.playerInit = playerInit;
    }

    public F construct(FeatureConfiguration configuration) {
        CombatFeatureRegistry.init(this);
        return this.constructor.construct(configuration);
    }

    public FeatureType<?> featureType() {
        return this.featureType;
    }

    public Set<FeatureType<?>> dependencies() {
        return this.dependencies;
    }

    @Nullable PlayerInit playerInit() {
        return this.playerInit;
    }

    @FunctionalInterface
    public interface Constructor<F> {
        F construct(FeatureConfiguration configuration);
    }

    @FunctionalInterface
    public interface PlayerInit {
        void init(Player player, PlayerInitReason reason);
    }
}
