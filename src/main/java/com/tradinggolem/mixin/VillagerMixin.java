package com.tradinggolem.mixin;

import com.tradinggolem.villager.SoldierRecruitment;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public class VillagerMixin {
	@Inject(method = "setVillagerData", at = @At("TAIL"))
	private void tradingGolem$onProfession(VillagerData data, CallbackInfo ci) {
		SoldierRecruitment.onProfessionSet((Villager) (Object) this, data);
	}
}
