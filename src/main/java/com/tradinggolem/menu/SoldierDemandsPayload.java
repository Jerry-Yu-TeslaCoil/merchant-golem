package com.tradinggolem.menu;

import com.tradinggolem.TradingGolemMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SoldierDemandsPayload(String text) implements CustomPacketPayload {
	public static final Type<SoldierDemandsPayload> TYPE = new Type<>(TradingGolemMod.id("soldier_demands"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SoldierDemandsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(512),
			SoldierDemandsPayload::text,
			SoldierDemandsPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
