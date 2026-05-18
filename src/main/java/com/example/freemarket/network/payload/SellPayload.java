package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C→S: 出品リクエスト
 * 手に持っているアイテム + 入力した価格を送信
 */
public record SellPayload(long price) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "sell");

    public static final CustomPacketPayload.Type<SellPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, SellPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SellPayload decode(FriendlyByteBuf buf) {
                return new SellPayload(buf.readLong());
            }

            @Override
            public void encode(FriendlyByteBuf buf, SellPayload payload) {
                buf.writeLong(payload.price());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
