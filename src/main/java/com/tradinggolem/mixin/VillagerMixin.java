package com.tradinggolem.mixin;

import com.tradinggolem.villager.SoldierRecruitment;
import com.tradinggolem.villager.VillagerFoodAccess;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerMixin implements VillagerFoodAccess {
	/** Same field a villager uses to decide it is hungry (below 12). */
	@Accessor("foodLevel")
	@Override
	public abstract int tradingGolem$getFoodLevel();

	@Accessor("foodLevel")
	@Override
	public abstract void tradingGolem$setFoodLevel(int foodLevel);

	@Inject(method = "setVillagerData", at = @At("TAIL"))
	private void tradingGolem$onProfession(VillagerData data, CallbackInfo ci) {
		SoldierRecruitment.onProfessionSet((Villager) (Object) this, data);
	}
}
