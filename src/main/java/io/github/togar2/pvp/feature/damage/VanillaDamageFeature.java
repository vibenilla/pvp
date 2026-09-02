package io.github.togar2.pvp.feature.damage;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.events.EntityPreDeathEvent;
import io.github.togar2.pvp.events.FinalDamageEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.armor.ArmorFeature;
import io.github.togar2.pvp.feature.block.BlockFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.knockback.KnockbackFeature;
import io.github.togar2.pvp.feature.provider.DifficultyProvider;
import io.github.togar2.pvp.feature.totem.TotemFeature;
import io.github.togar2.pvp.feature.tracking.TrackingFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.EntityUtil;
import io.github.togar2.pvp.utils.RegistryTags;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDamageEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link DamageFeature}.
 * Supports blocking, knockback, totems, armor, etc.
 */
public class VanillaDamageFeature implements DamageFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaDamageFeature> DEFINED = new DefinedFeature<>(
			FeatureType.DAMAGE, VanillaDamageFeature::new,
			FeatureType.DIFFICULTY, FeatureType.BLOCK, FeatureType.ARMOR, FeatureType.TOTEM,
			FeatureType.EXHAUSTION, FeatureType.KNOCKBACK, FeatureType.TRACKING,
			FeatureType.ITEM_DAMAGE, FeatureType.ENCHANTMENT, FeatureType.VERSION
	);

	public static final Tag<Long> NEW_DAMAGE_TIME = Tag.Long("newDamageTime");
	public static final Tag<Float> LAST_DAMAGE_AMOUNT = Tag.Float("lastDamageAmount");

	private final FeatureConfiguration configuration;

	private DifficultyProvider difficultyProvider;

	private BlockFeature blockFeature;
	private ArmorFeature armorFeature;
	private TotemFeature totemFeature;
	private ExhaustionFeature exhaustionFeature;
	private KnockbackFeature knockbackFeature;
	private TrackingFeature trackingFeature;
	private ItemDamageFeature itemDamageFeature;
	private EnchantmentFeature enchantmentFeature;

	private CombatVersion version;

	public VanillaDamageFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.difficultyProvider = this.configuration.get(FeatureType.DIFFICULTY);
		this.blockFeature = this.configuration.get(FeatureType.BLOCK);
		this.armorFeature = this.configuration.get(FeatureType.ARMOR);
		this.totemFeature = this.configuration.get(FeatureType.TOTEM);
		this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
		this.knockbackFeature = this.configuration.get(FeatureType.KNOCKBACK);
		this.trackingFeature = this.configuration.get(FeatureType.TRACKING);
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityDamageEvent.class, this::handleDamage);
	}

	protected void handleDamage(EntityDamageEvent event) {
		boolean shouldAnimate = event.shouldAnimate();

		// We will handle sound and animation ourselves
		event.setAnimation(false);
		SoundEvent sound = event.getSound();
		event.setSound(null);

		LivingEntity entity = event.getEntity();
		Damage damage = event.getDamage();
		Entity attacker = damage.getAttacker();

		DamageType damageType = MinecraftServer.getDamageTypeRegistry().get(damage.getType());
		assert damageType != null;

		DamageTypeInfo typeInfo = DamageTypeInfo.of(damage.getType());
		if (entity instanceof Player player
				&& player.isInvulnerable()
				&& !this.bypassesInvulnerability(damageType)) {
			event.setCancelled(true);
			return;
		}

		if (this.isImmuneToDamageFromEnchantments(entity, damage, damageType)) {
			event.setCancelled(true);
			return;
		}

		if (event.getEntity() instanceof Player player && typeInfo.shouldScaleWithDifficulty(damage)) {
			damage.setAmount(this.scaleWithDifficulty(player, damage.getAmount()));

            if (damage.getAmount() == 0.0F) {
                event.setCancelled(true);
                return;
            }
        }

		if (typeInfo.fire() && entity.hasEffect(PotionEffect.FIRE_RESISTANCE)) {
			event.setCancelled(true);
			return;
		}
		if (typeInfo.fire() && entity.getEntityType().fireImmune()) {
			event.setCancelled(true);
			return;
		}

		// This will be used to determine whether knockback should be applied
		// We can't just check if the remaining damage is 0 because this would apply no knockback for snowballs & eggs
		boolean fullyBlocked = false;
		if (this.blockFeature.isDamageBlocked(entity, damage)) {
			fullyBlocked = this.blockFeature.applyBlock(entity, damage);
		}

		float amount = damage.getAmount();
		if (typeInfo.freeze() && RegistryTags.contains(RegistryTags.FREEZE_HURTS_EXTRA_TYPES, entity.getEntityType())) {
			amount *= 5.0F;
		}

		if (entity instanceof Player && typeInfo.damagesHelmet() && !entity.getEquipment(EquipmentSlot.HELMET).isAir()) {
            this.itemDamageFeature.damageArmor(entity, damageType, amount, EquipmentSlot.HELMET);
			amount *= 0.75F;
		}

		float amountBeforeProcessing = amount;

		// Invulnerability ticks
		boolean hurtSoundAndAnimation = true;
		long newDamageTime = entity.hasTag(NEW_DAMAGE_TIME) ? entity.getTag(NEW_DAMAGE_TIME) : -10000;
		if (entity.getAliveTicks() - newDamageTime < 0) {
			float lastDamage = entity.hasTag(LAST_DAMAGE_AMOUNT) ? entity.getTag(LAST_DAMAGE_AMOUNT) : 0;

			if (amount <= lastDamage) {
				event.setCancelled(true);
				return;
			}

			hurtSoundAndAnimation = false;
			amount = amount - lastDamage;
		}

		// Process armor and effects
		if (entity instanceof Player && !typeInfo.bypassesArmor()) {
			this.itemDamageFeature.damageArmor(
					entity, damageType, amount,
					EquipmentSlot.BOOTS, EquipmentSlot.LEGGINGS, EquipmentSlot.CHESTPLATE, EquipmentSlot.HELMET
			);
		}

		amount = this.armorFeature.getDamageWithProtection(entity, damageType, amount,
				attacker instanceof LivingEntity livingAttacker ? livingAttacker : null);

		damage.setAmount(amount);
		FinalDamageEvent finalDamageEvent = new FinalDamageEvent(entity, damage, 10, shouldAnimate);
		EventDispatcher.call(finalDamageEvent);
		// New amount has been set in the Damage class
		amount = damage.getAmount();

		if (finalDamageEvent.isCancelled()) {
			event.setCancelled(true);
			return;
		}

		// Register damage to tracking feature
		boolean register = this.version.legacy() || amount > 0;
		if (register && entity instanceof Player player)
            this.trackingFeature.recordDamage(player, attacker, damage);

		// Exhaustion from damage
		if (amountBeforeProcessing != 0 && entity instanceof Player player)
            this.exhaustionFeature.addDamageExhaustion(player, damageType);

		entity.setTag(LAST_DAMAGE_AMOUNT, amountBeforeProcessing);

		var velocityBeforeKnockback = entity.getVelocity();
		var appliedKnockback = false;

		if (hurtSoundAndAnimation) {
			entity.setTag(NEW_DAMAGE_TIME, entity.getAliveTicks() + finalDamageEvent.getInvulnerabilityTicks());

			if (!fullyBlocked && finalDamageEvent.shouldAnimate()) {
				// Send damage animation
				entity.sendPacketToViewersAndSelf(new DamageEventPacket(
						entity.getEntityId(),
						MinecraftServer.getDamageTypeRegistry().getId(damage.getType()),
						damage.getAttacker() == null ? 0 : damage.getAttacker().getEntityId() + 1,
						damage.getSource() == null ? 0 : damage.getSource().getEntityId() + 1,
						null
				));
			}

			if (!this.isNoKnockbackDamage(damage)) {
				appliedKnockback = true;

				if (attacker != null && !typeInfo.explosive()) {
                    this.knockbackFeature.applyDamageKnockback(damage, entity, fullyBlocked);
				} else {
					// Update velocity
					entity.setVelocity(entity.getVelocity());
				}
			}
		}

		if (fullyBlocked) {
			if (hurtSoundAndAnimation) this.playHurtSound(entity, sound);
			event.setCancelled(true);
			return;
		}

		boolean death = false;
		float totalHealth = entity.getHealth() +
				(entity instanceof Player player ? player.getAdditionalHearts() : 0);
		if (totalHealth - amount <= 0) {
			boolean totem = this.totemFeature.tryProtect(entity, damageType);

			if (totem) {
				event.setCancelled(true);
			} else {
				death = true;
				if (hurtSoundAndAnimation) {
					// Death sound
					sound = entity instanceof Player ? SoundEvent.ENTITY_PLAYER_DEATH : SoundEvent.ENTITY_GENERIC_DEATH;
				}
			}
		} else if (hurtSoundAndAnimation) {
			// Workaround to have different types make a different sound,
			// but only if the sound has not been changed by damage#getSound
			if (entity instanceof Player && sound == SoundEvent.ENTITY_PLAYER_HURT) {
				String effects = damageType.effects();
				if (effects != null) sound = switch (effects) {
					case "drowning" -> SoundEvent.ENTITY_PLAYER_HURT_DROWN;
					case "burning" -> SoundEvent.ENTITY_PLAYER_HURT_ON_FIRE;
					case "poking" -> SoundEvent.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH;
					case "freezing" -> SoundEvent.ENTITY_PLAYER_HURT_FREEZE;
					default -> sound;
				};
			}
		}

		if (hurtSoundAndAnimation) {
			this.playHurtSound(entity, sound);

			if (damage.getType().equals(DamageType.THORNS)) {
				entity.sendPacketToViewersAndSelf(new SoundEffectPacket(
						SoundEvent.ENCHANT_THORNS_HIT,
						entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
						entity.getPosition(),
						1.0F, 1.0F, ThreadLocalRandom.current().nextLong()
				));
			}
		}

		damage.setAmount(amount);

		if (death && !event.isCancelled()) {
			EntityPreDeathEvent entityPreDeathEvent = new EntityPreDeathEvent(entity, damage);
			EventDispatcher.call(entityPreDeathEvent);
			if ((entityPreDeathEvent.isCancelled() || entityPreDeathEvent.isCancelDeath()) && appliedKnockback) {
				entity.setVelocity(velocityBeforeKnockback);
			}

			if (entityPreDeathEvent.isCancelled()) event.setCancelled(true);
			if (entityPreDeathEvent.isCancelDeath()) {
				amount = 0;
				event.setCancelled(true);
			}
		}

		damage.setAmount(amount);

		// lastDamage field is set when event is not canceled but should also when canceled
		if (register) EntityUtil.setLastDamage(entity, damage);

	}

	private void playHurtSound(LivingEntity entity, @Nullable SoundEvent sound) {
		if (sound == null) return;

		var random = ThreadLocalRandom.current();
		var pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
		entity.sendPacketToViewersAndSelf(new SoundEffectPacket(
				sound, entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				entity.getPosition(),
				1.0F, pitch, random.nextLong()
		));
	}

	private boolean isNoKnockbackDamage(Damage damage) {
		var noKnockback = MinecraftServer.process().damageType().getTag(Key.key("minecraft:no_knockback"));

		return noKnockback != null && noKnockback.contains(damage.getType());
	}

	private boolean isImmuneToDamageFromEnchantments(LivingEntity entity, Damage damage, DamageType damageType) {
		var burnFromStepping = MinecraftServer.process().damageType().getTag(Key.key("minecraft:burn_from_stepping"));

		return burnFromStepping != null
				&& burnFromStepping.contains(damage.getType())
				&& !this.bypassesInvulnerability(damageType)
				&& this.enchantmentFeature.getEquipmentLevel(entity, Enchantment.FROST_WALKER) > 0;
	}

	private boolean bypassesInvulnerability(DamageType damageType) {
		var bypassesInvulnerability = MinecraftServer.process().damageType().getTag(Key.key("minecraft:bypasses_invulnerability"));

		return bypassesInvulnerability != null
				&& bypassesInvulnerability.contains(MinecraftServer.getDamageTypeRegistry().getKey(damageType));
	}

	protected float scaleWithDifficulty(Player player, float amount) {
		return switch (this.difficultyProvider.getValue(player)) {
			case PEACEFUL -> 0.0F;
			case EASY -> Math.min(amount / 2.0f + 1.0f, amount);
			case HARD -> amount * 3.0f / 2.0f;
			default -> amount;
		};
	}

}
