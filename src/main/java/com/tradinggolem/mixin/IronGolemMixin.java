package com.tradinggolem.mixin;

import com.tradinggolem.entity.Soldier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Soldiers and iron golems never fight. A stray arrow does not crack the golem or make it angry. */
@Mixin(IronGolem.class)
public class IronGolemMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void tradingGolem$spareSoldiers(LivingEntity target, CallbackInfoReturnable<Boolean> ci) {
		if (target instanceof Soldier) {
			ci.setReturnValue(false);
		}
	}

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void tradingGolem$ignoreSoldierDamage(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> ci) {
		if (source.getEntity() instanceof Soldier) {
			ci.setReturnValue(false);
		}
	}
}
