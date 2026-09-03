package io.github.togar2.pvp.entity.explosion;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.other.PrimedTntMeta;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

public class TntEntity extends Entity {
    private final Entity causingEntity;
    private boolean exploded;

    public TntEntity(@Nullable Entity causingEntity) {
        super(EntityType.TNT);
        this.causingEntity = causingEntity;

        var angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        this.setVelocity(new Vec(-Math.sin(angle) * 0.02, 0.2F, -Math.cos(angle) * 0.02)
                .mul(ServerFlag.SERVER_TICKS_PER_SECOND));
    }

    public int getFuse() {
        return ((PrimedTntMeta) this.getEntityMeta()).getFuseTime();
    }

    public void setFuse(int fuse) {
        ((PrimedTntMeta) this.getEntityMeta()).setFuseTime(fuse);
    }

    @Override
    public void update(long time) {
        if (this.onGround) this.velocity = this.velocity.mul(0.7, -0.5, 0.7);
        var newFuse = this.getFuse() - 1;
        this.setFuse(newFuse);
        if (newFuse <= 0 && !this.exploded) {
            this.exploded = true;
            var instance = this.instance;
            var position = this.position;
            var boundingBox = this.boundingBox;

            if (instance.getExplosionSupplier() != null) {
                var additionalData = CompoundBinaryTag.builder()
                        .putString("sourceEntity", this.getUuid().toString());

                if (this.causingEntity != null) {
                    additionalData.putString("causingEntity", this.causingEntity.getUuid().toString());
                }

                instance.explode(
                        (float) position.x(),
                        (float) (position.y() + boundingBox.height() * 0.0625),
                        (float) position.z(),
                        4.0F,
                        additionalData.build()
                );
            }

            this.remove();
        }
    }

    @Override
    public double getEyeHeight() {
        return 0.15;
    }
}
