package io.github.togar2.pvp.feature.explosion;

import io.github.togar2.pvp.entity.explosion.CrystalEntity;
import io.github.togar2.pvp.events.ExplosionEvent;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.player.CombatPlayer;
import io.github.togar2.pvp.utils.ChunkBlockGetter;
import io.github.togar2.pvp.utils.CollisionUtil;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.instance.Explosion;
import net.minestom.server.instance.ExplosionSupplier;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ExplosionPacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;

import net.minestom.server.utils.WeightedList;
import net.minestom.server.utils.WeightedList.Entry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class VanillaExplosionSupplier implements ExplosionSupplier {
	private static final float LIQUID_EXPLOSION_RESISTANCE = 100.0F;

	private final ExplosionFeature feature;

	private final EnchantmentFeature enchantmentFeature;
	private final FallFeature fallFeature;

    private final WeightedList<ExplosionPacket.BlockParticleInfo> PARTICLES = WeightedList.of(
            new Entry<>(new ExplosionPacket.BlockParticleInfo(Particle.POOF, 0.5f, 1.0f), 1),
            new Entry<>(new ExplosionPacket.BlockParticleInfo(Particle.SMOKE, 1.0f, 1.0f), 1)
    );

	VanillaExplosionSupplier(ExplosionFeature feature, EnchantmentFeature enchantmentFeature, FallFeature fallFeature) {
		this.feature = feature;
		this.enchantmentFeature = enchantmentFeature;
		this.fallFeature = fallFeature;
	}

	@Override
	public Explosion createExplosion(float centerX, float centerY, float centerZ,
	                                 float strength, @Nullable CompoundBinaryTag additionalData) {
		return this.createExplosion(centerX, centerY, centerZ, strength, additionalData, null, null);
	}

	public Explosion createExplosion(float centerX, float centerY, float centerZ,
	                                 float strength, @Nullable CompoundBinaryTag additionalData,
	                                 @Nullable Entity sourceEntityOverride, @Nullable Entity causingEntityOverride) {
		return new Explosion(centerX, centerY, centerZ, strength) {
			private final Map<Player, Vec> playerKnockback = new HashMap<>();

			@Override
			protected List<Point> prepare(Instance instance) {
				List<Point> blocks = new ArrayList<>();
				ThreadLocalRandom random = ThreadLocalRandom.current();

				boolean breakBlocks = true;
				if (additionalData != null && additionalData.keySet().contains("breakBlocks"))
					breakBlocks = additionalData.getBoolean("breakBlocks");

				if (breakBlocks) {
					var blockGetter = new ChunkBlockGetter(instance, null, Block.AIR);

					boolean anchorWater = additionalData != null && additionalData.getBoolean("anchorWater", false);
					Vec originBlock = new Vec(this.getCenterX(), this.getCenterY(), this.getCenterZ()).apply(Vec.Operator.FLOOR);

					for (int x = 0; x < 16; ++x) {
						for (int y = 0; y < 16; ++y) {
							for (int z = 0; z < 16; ++z) {
								if (x == 0 || x == 15 || y == 0 || y == 15 || z == 0 || z == 15) {
									double xLength = (float) x / 15.0F * 2.0F - 1.0F;
									double yLength = (float) y / 15.0F * 2.0F - 1.0F;
									double zLength = (float) z / 15.0F * 2.0F - 1.0F;
									double length = Math.sqrt(xLength * xLength + yLength * yLength + zLength * zLength);
									xLength /= length;
									yLength /= length;
									zLength /= length;
									double centerX = this.getCenterX();
									double centerY = this.getCenterY();
									double centerZ = this.getCenterZ();

									float strengthLeft = this.getStrength() * (0.7F + random.nextFloat() * 0.6F);
									for (; strengthLeft > 0.0F; strengthLeft -= 0.225F) {
										Vec position = new Vec(centerX, centerY, centerZ);
										Block block = blockGetter.getBlock(position);
										boolean anchorBlock = anchorWater && position.sameBlock(originBlock);

										if (anchorBlock || !block.air()) {
											var explosionResistance = anchorBlock ? LIQUID_EXPLOSION_RESISTANCE : this.getExplosionResistance(block);
											strengthLeft -= (explosionResistance + 0.3F) * 0.3F;

											if (strengthLeft > 0.0F && !block.air()) {
												Vec blockPosition = position.apply(Vec.Operator.FLOOR);
												if (!blocks.contains(blockPosition)) {
													blocks.add(blockPosition);
												}
											}
										}

										centerX += xLength * 0.30000001192092896D;
										centerY += yLength * 0.30000001192092896D;
										centerZ += zLength * 0.30000001192092896D;
									}
								}
							}
						}
					}
				}

				double strength = this.getStrength() * 2.0F;
				int minX_ = (int) Math.floor(this.getCenterX() - strength - 1.0D);
				int maxX_ = (int) Math.floor(this.getCenterX() + strength + 1.0D);
				int minY_ = (int) Math.floor(this.getCenterY() - strength - 1.0D);
				int maxY_ = (int) Math.floor(this.getCenterY() + strength + 1.0D);
				int minZ_ = (int) Math.floor(this.getCenterZ() - strength - 1.0D);
				int maxZ_ = (int) Math.floor(this.getCenterZ() + strength + 1.0D);

				int minX = Math.min(minX_, maxX_);
				int maxX = Math.max(minX_, maxX_);
				int minY = Math.min(minY_, maxY_);
				int maxY = Math.max(minY_, maxY_);
				int minZ = Math.min(minZ_, maxZ_);
				int maxZ = Math.max(minZ_, maxZ_);

				Vec centerPoint = new Vec(this.getCenterX(), this.getCenterY(), this.getCenterZ());
				Entity sourceEntity = this.getSourceEntity(instance);

				List<Entity> entities = new ArrayList<>(instance.getEntities().stream()
						.filter(entity -> entity != sourceEntity)
						.filter(entity -> !(entity instanceof Player player) || player.getGameMode() != GameMode.SPECTATOR)
						.filter(entity -> this.intersectsEntity(minX, minY, minZ, maxX, maxY, maxZ, entity))
						.toList());

				boolean anchor = false;
				if (additionalData != null && additionalData.keySet().contains("anchor")) {
					anchor = additionalData.getBoolean("anchor");
				}

				Damage damageObj;
				if (anchor) {
					damageObj = new Damage(DamageType.BAD_RESPAWN_POINT, null, null, centerPoint, 0);
				} else {
					Entity causingEntity = this.getCausingEntity(instance);
					damageObj = new Damage(causingEntity == null || sourceEntity == null ? DamageType.EXPLOSION : DamageType.PLAYER_EXPLOSION,
							sourceEntity, causingEntity, null, 0);
				}

				// Blocks and entities list may be modified during the event call
				ExplosionEvent explosionEvent = new ExplosionEvent(instance, blocks, entities, damageObj);
				EventDispatcher.call(explosionEvent);
				if (explosionEvent.isCancelled()) return null;
				damageObj = explosionEvent.getDamageObject();

				for (Entity entity : entities) {
					var distanceStrength = entity.getPosition().distance(centerPoint) / strength;
					if (distanceStrength <= 1.0D) {
						var originY = entity.getEntityType() == EntityType.TNT
								? entity.getPosition().y()
								: entity.getPosition().y() + entity.getEyeHeight();
						var directionX = entity.getPosition().x() - this.getCenterX();
						var directionY = originY - this.getCenterY();
						var directionZ = entity.getPosition().z() - this.getCenterZ();
						var directionLength = Math.sqrt(directionX * directionX + directionY * directionY + directionZ * directionZ);

						if (directionLength != 0.0D) {
							directionX /= directionLength;
							directionY /= directionLength;
							directionZ /= directionLength;
						} else {
							directionX = 0.0D;
							directionY = 0.0D;
							directionZ = 0.0D;
						}

						var exposure = getExposure(centerPoint, entity);
						var impactStrength = (1.0D - distanceStrength) * exposure;
						var damageAmount = (float) ((impactStrength * impactStrength + impactStrength)
								/ 2.0D * 7.0D * strength + 1.0D);

						var knockback = impactStrength;
						var entityDamage = new Damage(damageObj.getType(), damageObj.getSource(),
								damageObj.getAttacker(), damageObj.getSourcePosition(), damageAmount);

						if (entity instanceof LivingEntity living) {
							knockback = VanillaExplosionSupplier.this.enchantmentFeature.getExplosionKnockback(living, impactStrength);
							knockback *= 1.0 - living.getAttributeValue(Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
							living.damage(entityDamage);
						} else if (entity instanceof CrystalEntity crystal) {
							crystal.damage(entityDamage);
						}

						var knockbackVec = new Vec(
								directionX * knockback,
								directionY * knockback,
								directionZ * knockback
						);

						var tps = ServerFlag.SERVER_TICKS_PER_SECOND;
						if (entity instanceof Player player) {
							VanillaExplosionSupplier.this.fallFeature.resetPostImpulseGraceTime(player);

							if (this.shouldApplyPlayerKnockback(player)) {
								this.playerKnockback.put(player, knockbackVec);
								if (player instanceof CombatPlayer custom) {
									custom.setVelocityNoUpdate(velocity -> velocity.add(knockbackVec.mul(tps)));
								}
							}
						} else {
							entity.setVelocity(entity.getVelocity().add(knockbackVec.mul(tps)));
						}
					}
				}

				return blocks;
			}

			@Override
			public void apply(@NotNull Instance instance) {
				List<Point> blocks = this.prepare(instance);
				if (blocks == null) return; // Event was canceled
				boolean tntExplodes = true;
				if (additionalData != null && additionalData.keySet().contains("tntExplodes"))
					tntExplodes = additionalData.getBoolean("tntExplodes");

				byte[] records = new byte[3 * blocks.size()];
				for (int i = 0; i < blocks.size(); i++) {
					final var pos = blocks.get(i);
					if (tntExplodes && instance.getBlock(pos).compare(Block.TNT)) {
						Entity causingEntity = this.getCausingEntity(instance);
                        VanillaExplosionSupplier.this.feature.primeExplosive(instance, pos, new ExplosionFeature.IgnitionCause.Explosion(causingEntity),
								ThreadLocalRandom.current().nextInt(20) + 10);
					}
					instance.setBlock(pos, Block.AIR);
					final byte x = (byte) (pos.x() - Math.floor(this.getCenterX()));
					final byte y = (byte) (pos.y() - Math.floor(this.getCenterY()));
					final byte z = (byte) (pos.z() - Math.floor(this.getCenterZ()));
					records[i * 3] = x;
					records[i * 3 + 1] = y;
					records[i * 3 + 2] = z;
				}

				var centerPoint = new Vec(centerX, centerY, centerZ);
				var explosionParticle = this.getStrength() < 2.0F ? Particle.EXPLOSION : Particle.EXPLOSION_EMITTER;

				for (Player player : instance.getPlayers()) {
					if (player.getPosition().distanceSquared(centerPoint) >= 4096.0) continue;

					var knockbackVec = this.playerKnockback.get(player);
					player.sendPacket(
							new ExplosionPacket(
									new Vec(centerX, centerY, centerZ),
									this.getStrength(),
									blocks.size(),
									knockbackVec,
									explosionParticle,
									SoundEvent.ENTITY_GENERIC_EXPLODE,
									VanillaExplosionSupplier.this.PARTICLES
							)
					);
				}
				this.playerKnockback.clear();

				if (additionalData != null && additionalData.keySet().contains("fire")) {
					if (additionalData.getBoolean("fire")) {
						ThreadLocalRandom random = ThreadLocalRandom.current();
						for (Point point : blocks) {
							var belowBlock = instance.getBlock(point.sub(0, 1, 0));
							if (random.nextInt(3) != 0
									|| !instance.getBlock(point).air()
									|| !belowBlock.solid())
								continue;

							instance.setBlock(point, this.getFireBlock(belowBlock));
						}
					}
				}

				this.postSend(instance, blocks);
			}

			private @Nullable Entity getCausingEntity(Instance instance) {
				if (causingEntityOverride != null) return causingEntityOverride;

				return this.getEntity(instance, "causingEntity");
			}

			private @Nullable Entity getSourceEntity(Instance instance) {
				if (sourceEntityOverride != null) return sourceEntityOverride;

				return this.getEntity(instance, "sourceEntity");
			}

			private @Nullable Entity getEntity(Instance instance, String key) {
				if (additionalData == null || !additionalData.keySet().contains(key)) return null;

				return instance.getEntityByUuid(UUID.fromString(additionalData.getString(key)));
			}

			private boolean shouldApplyPlayerKnockback(Player player) {
				return player.getGameMode() != GameMode.SPECTATOR
						&& (player.getGameMode() != GameMode.CREATIVE || !player.isFlying());
			}

			private boolean intersectsEntity(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Entity entity) {
				var boundingBox = entity.getBoundingBox();
				var entityPosition = entity.getPosition();

				return minX <= entityPosition.x() + boundingBox.maxX()
						&& maxX >= entityPosition.x() + boundingBox.minX()
						&& minY <= entityPosition.y() + boundingBox.maxY()
						&& maxY >= entityPosition.y() + boundingBox.minY()
						&& minZ <= entityPosition.z() + boundingBox.maxZ()
						&& maxZ >= entityPosition.z() + boundingBox.minZ();
			}

			private float getExplosionResistance(Block block) {
				var explosionResistance = block.explosionResistance();

				if (block.liquid()) {
					explosionResistance = Math.max(explosionResistance, LIQUID_EXPLOSION_RESISTANCE);
				}

				return explosionResistance;
			}

			private Block getFireBlock(Block belowBlock) {
				if (belowBlock.compare(Block.SOUL_SAND) || belowBlock.compare(Block.SOUL_SOIL)) {
					return Block.SOUL_FIRE;
				}

				return Block.FIRE;
			}
		};
	}

	public static double getExposure(Point center, Entity entity) {
		BoundingBox box = entity.getBoundingBox();
		double xStep = 1 / (box.width() * 2 + 1);
		double yStep = 1 / (box.height() * 2 + 1);
		double zStep = 1 / (box.depth() * 2 + 1);
		double g = (1 - Math.floor(1 / xStep) * xStep) / 2;
		double h = (1 - Math.floor(1 / zStep) * zStep) / 2;
		if (xStep < 0 || yStep < 0 || zStep < 0) return 0;

		int exposedCount = 0;
		int rayCount = 0;
		double dx = 0;
		while (dx <= 1) {
			double dy = 0;
			while (dy <= 1) {
				double dz = 0;
				while (dz <= 1) {
					double rayX = box.minX() + dx * box.width();
					double rayY = box.minY() + dy * box.height();
					double rayZ = box.minZ() + dz * box.depth();
					Point point = new Vec(rayX + g, rayY, rayZ + h).add(entity.getPosition());
					if (noBlocking(entity.getInstance(), point, center)) exposedCount++;
					rayCount++;
					dz += zStep;
				}
				dy += yStep;
			}
			dx += xStep;
		}

		return exposedCount / (double) rayCount;
	}

	public static boolean noBlocking(Instance instance, Point start, Point end) {
		return CollisionUtil.hasLineOfSight(instance, start, end);
	}
}
