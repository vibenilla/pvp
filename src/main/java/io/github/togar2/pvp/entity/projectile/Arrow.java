package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.feature.effect.EffectFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.projectile.ArrowMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.potion.CustomPotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public class Arrow extends AbstractArrow {
	public static final ItemStack DEFAULT_ARROW = ItemStack.of(Material.ARROW);

	private final EffectFeature effectFeature;

	private ItemStack itemStack = DEFAULT_ARROW;

	public Arrow(@Nullable Entity shooter, EffectFeature effectFeature, EnchantmentFeature enchantmentFeature) {
		super(shooter, EntityType.ARROW, enchantmentFeature);
		this.effectFeature = effectFeature;
	}

	@Override
	public void update(long time) {
		super.update(time);
		var potionContents = this.itemStack.get(DataComponents.POTION_CONTENTS);
		if (this.isStuck() && this.stuckTime >= 600 && potionContents != null && !potionContents.equals(PotionContents.EMPTY)) {
            this.triggerStatus((byte) 0);
            this.itemStack = DEFAULT_ARROW;
		}
	}

	@Override
	protected ItemStack getPickupItem() {
		return this.itemStack;
	}

	public void setItemStack(ItemStack itemStack) {
		this.itemStack = itemStack;
        this.updateColor();
	}

	@Override
	protected void onHurt(LivingEntity entity) {
        this.effectFeature.addArrowEffects(entity, this);
	}

	private void updateColor() {
		PotionContents potionContents = this.itemStack.get(DataComponents.POTION_CONTENTS);
		if (potionContents == null || potionContents.equals(PotionContents.EMPTY)) {
            this.setColor(-1);
			return;
		}

        this.setColor(this.effectFeature.getPotionColor(potionContents));
	}

	private void setColor(int color) {
		((ArrowMeta) this.getEntityMeta()).setColor(color);
	}

	public @NotNull PotionContents getPotion() {
		return this.itemStack.get(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
	}

	public float getPotionDurationScale() {
		return this.itemStack.get(DataComponents.POTION_DURATION_SCALE, 1.0F);
	}

	public void setPotion(@NotNull PotionContents potion) {
		if (this.itemStack.material() != Material.TIPPED_ARROW)
            this.itemStack = ItemStack.of(Material.TIPPED_ARROW);
        this.itemStack = this.itemStack.with(DataComponents.POTION_CONTENTS, potion);
        this.updateColor();
	}

	public void addArrowEffect(CustomPotionEffect effect) {
        this.itemStack = this.itemStack.with(DataComponents.POTION_CONTENTS, (UnaryOperator<PotionContents>) potionContents -> {
			List<CustomPotionEffect> list = new ArrayList<>(potionContents.customEffects());
			list.add(effect);
			return new PotionContents(potionContents.potion(), potionContents.customColor(), list);
		});
	}
}
