package com.example.auction.network.payload;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: GUI 内エラーラベル表示用
 * message : 表示文字列
 * color   : ARGB（alpha なし 0xRRGGBB で渡し、画面側でフェード計算）
 */
public record ErrorMessagePayload(String message, int color)
        implements CustomPacketPayload {

    public static final Type<ErrorMessagePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("auctionmod", "error_message"));

    public static final StreamCodec<FriendlyByteBuf, ErrorMessagePayload> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> { buf.writeUtf(p.message()); buf.writeInt(p.color()); },
            buf       -> new ErrorMessagePayload(buf.readUtf(), buf.readInt())
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
