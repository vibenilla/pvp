package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.events.PlayerExhaustEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.config.PlayerInitReason;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.feature.provider.DifficultyProvider;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.Difficulty;

import java.util.Objects;

/**
 * Vanilla implementation of {@link ExhaustionFeature}
 */
public class VanillaExhaustionFeature implements ExhaustionFeature, RegistrableFeature {
    public static final DefinedFeature<VanillaExhaustionFeature> DEFINED = new DefinedFeature<>(
            FeatureType.EXHAUSTION, VanillaExhaustionFeature::new,
            VanillaExhaustionFeature::initPlayer,
            FeatureType.DIFFICULTY, FeatureType.VERSION
    );

    public static final Tag<Float> EXHAUSTION = Tag.Float("exhaustion");

    private final FeatureConfiguration configuration;

    private DifficultyProvider difficultyFeature;
    private CombatVersion version;

    public VanillaExhaustionFeature(FeatureConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void initDependencies() {
        this.difficultyFeature = this.configuration.get(FeatureType.DIFFICULTY);
        this.version = this.configuration.get(FeatureType.VERSION);
    }

    public static void initPlayer(Player player, PlayerInitReason reason) {
        if (reason == PlayerInitReason.INSTANCE_CHANGE) return;

        player.setTag(EXHAUSTION, 0.0F);
        if (reason == PlayerInitReason.RESPAWN) {
            player.setFood(20);
            player.setFoodSaturation(5.0F);
        }
    }

    @Override
    public void init(EventNode<EntityInstanceEvent> node) {
        node.addListener(PlayerTickEvent.class, event -> this.onTick(event.getPlayer()));

        node.addListener(PlayerBlockBreakEvent.class, event ->
                this.addExhaustion(event.getPlayer(), this.version.legacy() ? 0.025F : 0.005F));

        node.addListener(PlayerMoveEvent.class, this::onMove);
    }

    protected void onTick(Player player) {
        if (player.getGameMode().invulnerable()) return;

        var exhaustion = player.getTag(EXHAUSTION);
        if (exhaustion > 4) {
            player.setTag(EXHAUSTION, exhaustion - 4);
            if (player.getFoodSaturation() > 0) {
                player.setFoodSaturation(Math.max(player.getFoodSaturation() - 1, 0));
            } else if (this.difficultyFeature.getValue(player) != Difficulty.PEACEFUL) {
                player.setFood(Math.max(player.getFood() - 1, 0));
            }
        }
    }

    protected void onMove(PlayerMoveEvent event) {
        var player = event.getPlayer();

        var xDiff = event.getNewPosition().x() - player.getPosition().x();
        var yDiff = event.getNewPosition().y() - player.getPosition().y();
        var zDiff = event.getNewPosition().z() - player.getPosition().z();

        if (yDiff > 0.0 && player.isOnGround() && !event.isOnGround()) {
            if (player.isSprinting()) {
                this.addExhaustion(player, this.version.legacy() ? 0.8F : 0.2F);
            } else {
                this.addExhaustion(player, this.version.legacy() ? 0.2F : 0.05F);
            }
        }

        var instance = Objects.requireNonNull(player.getInstance());
        if (FluidUtil.isTouchingWater(player, event.getNewPosition())) {
            var eyePosition = event.getNewPosition().add(0.0, player.getEyeHeight(), 0.0);
            var submerged = FluidUtil.isWater(instance.getBlock(eyePosition));
            var distance = submerged
                    ? Math.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff)
                    : Math.sqrt(xDiff * xDiff + zDiff * zDiff);
            var distanceUnits = (int) Math.round(distance * 100.0F);
            if (distanceUnits > 0) this.addExhaustion(player, 0.01F * (float) distanceUnits * 0.01F);
        } else if (player.isOnGround()) {
            var distanceUnits = (int) Math.round(Math.sqrt(xDiff * xDiff + zDiff * zDiff) * 100.0F);
            if (distanceUnits > 0) this.addExhaustion(player, (player.isSprinting() ? 0.1F : 0.0F) * (float) distanceUnits * 0.01F);
        }
    }

    @Override
    public void addExhaustion(Player player, float exhaustion) {
        if (player.getGameMode().invulnerable()) return;
        var playerExhaustEvent = new PlayerExhaustEvent(player, exhaustion);
        EventDispatcher.callCancellable(playerExhaustEvent, () -> player.setTag(EXHAUSTION,
                Math.min(player.getTag(EXHAUSTION) + playerExhaustEvent.getAmount(), 40)));
    }

    @Override
    public void addAttackExhaustion(Player player) {
        this.addExhaustion(player, this.version.legacy() ? 0.3F: 0.1F);
    }

    @Override
    public void addDamageExhaustion(Player player, DamageType type) {
        this.addExhaustion(player, type.exhaustion() * (this.version.legacy() ? 3 : 1));
    }

    @Override
    public void applyHungerEffect(Player player, int amplifier) {
        this.addExhaustion(player, (this.version.legacy() ? 0.025F : 0.005F) * (float) (amplifier + 1));
    }
}
