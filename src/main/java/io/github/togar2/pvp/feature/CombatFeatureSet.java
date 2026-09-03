package io.github.togar2.pvp.feature;

import io.github.togar2.pvp.feature.config.CombatFeatureRegistry;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.trait.EntityInstanceEvent;

/**
 * A container for multiple {@link CombatFeature}s. Use {@link CombatFeatureSet#createNode()} to get an event node.
 */
public class CombatFeatureSet extends FeatureConfiguration implements RegistrableFeature {
    private boolean initialized = false;

    @Override
    public EventNode<EntityInstanceEvent> createNode() {
        CombatFeatureRegistry.attach();
        return RegistrableFeature.super.createNode();
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        for (var feature : this.listFeatures()) {
            if (!(feature instanceof RegistrableFeature registrable)) continue;
            node.addChild(registrable.createNode());
        }
    }

    @Override
    public void initDependencies() {
        for (var feature : this.listFeatures()) {
            feature.initDependencies();
        }
        this.initialized = true;
    }

    @Override
    public FeatureConfiguration add(FeatureType<?> type, CombatFeature feature) {
        if (this.initialized) throw new UnsupportedOperationException("Cannot add features after initialization");
        return super.add(type, feature);
    }
}
