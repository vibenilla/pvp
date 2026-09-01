package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class EntityCollisionParityTest {
	@Test
	public void matchesMinestomAcrossRandomizedEntities(Env env) {
		var instance = env.createFlatInstance();
		var random = new Random(31415L);

		var spawned = this.spawnEntities(instance, random);
		env.tick();

		assertTrue(spawned > 0);

		var mismatches = 0;
		var nonEmptyResults = 0;

		for (var iteration = 0; iteration < 4000; iteration++) {
			var position = new Pos(
					random.nextDouble() * 24.0 - 12.0,
					41.0 + random.nextDouble() * 3.0,
					random.nextDouble() * 24.0 - 12.0
			);
			var velocity = new Vec(
					(random.nextDouble() - 0.5) * 6.0,
					(random.nextDouble() - 0.5) * 2.0,
					(random.nextDouble() - 0.5) * 6.0
			);
			var boundingBox = new BoundingBox(0.25, 0.25, 0.25).expand(0.1, 0.3, 0.1);
			var extendRadius = 1.0 + random.nextDouble() * 3.0;
			var sweepLimit = random.nextBoolean() ? 1.0 : random.nextDouble();

			var expected = CollisionUtils.checkEntityCollisions(instance, boundingBox, position, velocity,
					extendRadius, entity -> true, this.minestomResult(position, velocity, sweepLimit));
			var actual = EntityCollision.checkCollision(instance.getEntityTracker(), boundingBox, position, velocity,
					extendRadius, entity -> true, this.pvpResult(position, velocity, sweepLimit));

			if (!expected.isEmpty()) nonEmptyResults++;
			if (!this.matches(expected, actual)) mismatches++;
		}

		assertEquals(0, mismatches);
		assertTrue(nonEmptyResults > 0);
	}

	private int spawnEntities(Instance instance, Random random) {
		var spawned = 0;

		for (var index = 0; index < 60; index++) {
			var entity = new LivingEntity(EntityType.ZOMBIE);
			entity.setInstance(instance, new Pos(
					random.nextDouble() * 24.0 - 12.0,
					41.0,
					random.nextDouble() * 24.0 - 12.0
			)).join();
			spawned++;
		}

		return spawned;
	}

	private net.minestom.server.collision.PhysicsResult minestomResult(Pos position, Vec velocity, double sweepLimit) {
		return new net.minestom.server.collision.PhysicsResult(position, velocity, false, false, false, false,
				velocity, new Vec[3], new net.minestom.server.collision.Shape[3], new BlockVec[3], false, sweepLimit);
	}

	private PhysicsResult pvpResult(Pos position, Vec velocity, double sweepLimit) {
		return new PhysicsResult(position, velocity, false, false, false, false,
				velocity, new Point[3], new net.minestom.server.collision.Shape[3], new Point[3], false, sweepLimit);
	}

	private boolean matches(List<EntityCollisionResult> expected, List<EntityCollisionResult> actual) {
		if (expected.size() != actual.size()) return false;

		var comparator = Comparator.comparingInt((EntityCollisionResult result) -> result.entity().getEntityId());
		var sortedExpected = expected.stream().sorted(comparator).toList();
		var sortedActual = actual.stream().sorted(comparator).toList();

		for (var index = 0; index < sortedExpected.size(); index++) {
			var first = sortedExpected.get(index);
			var second = sortedActual.get(index);

			if (first.entity() != second.entity()) return false;
			if (!first.collisionPoint().samePoint(second.collisionPoint())) return false;
			if (!first.direction().samePoint(second.direction())) return false;
			if (first.percentage() != second.percentage()) return false;
		}

		return true;
	}
}
