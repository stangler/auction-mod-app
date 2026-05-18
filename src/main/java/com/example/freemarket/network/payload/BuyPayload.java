package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C→S: 購入リクエスト
 */
public record BuyPayload(UUID listingId) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "buy");

    public static final CustomPacketPayload.Type<BuyPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, BuyPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public BuyPayload decode(FriendlyByteBuf buf) {
                return new BuyPayload(buf.readUUID());
            }

            @Override
            public void encode(FriendlyByteBuf buf, BuyPayload payload) {
                buf.writeUUID(payload.listingId());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
