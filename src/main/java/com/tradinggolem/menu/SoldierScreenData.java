package com.tradinggolem.menu;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SoldierScreenData(String lordName, int order, int weapon, int ride) {
	public static final StreamCodec<io.netty.buffer.ByteBuf, SoldierScreenData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(64),
			SoldierScreenData::lordName,
			ByteBufCodecs.VAR_INT,
			SoldierScreenData::order,
			ByteBufCodecs.VAR_INT,
			SoldierScreenData::weapon,
			ByteBufCodecs.VAR_INT,
			SoldierScreenData::ride,
			SoldierScreenData::new
	);
}
