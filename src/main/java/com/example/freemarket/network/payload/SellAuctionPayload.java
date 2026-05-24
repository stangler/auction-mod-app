package com.example.freemarket.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SellAuctionPayload(long startPrice, long durationMs)
        implements CustomPacketPayload {

    public static final long DURATION_3MIN  = 3L  * 60 * 1000;
    public static final long DURATION_30MIN = 30L * 60 * 1000;
    public static final long DURATION_1HOUR = 60L * 60 * 1000;

    private static final long[] VALID = { DURATION_3MIN, DURATION_30MIN, DURATION_1HOUR };

    public static final Type<SellAuctionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("freemarket", "sell_auction"));

    public static final StreamCodec<FriendlyByteBuf, SellAuctionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SellAuctionPayload::startPrice,
                    ByteBufCodecs.VAR_LONG, SellAuctionPayload::durationMs,
                    SellAuctionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 不正値は 3分 にフォールバック */
    public long validatedDuration() {
        for (long d : VALID) if (d == durationMs) return d;
        return DURATION_3MIN;
    }
}
