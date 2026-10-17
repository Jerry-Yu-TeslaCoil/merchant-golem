package com.tradinggolem.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractArrow.class)
public interface ArrowDamageAccess {
	@Accessor("baseDamage")
	double tradingGolem$getBaseDamage();
}
