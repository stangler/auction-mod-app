package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** C→S: オークション入札リクエスト */
public record BidPayload(UUID listingId, long amount) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "bid");

    public static final CustomPacketPayload.Type<BidPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, BidPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public BidPayload decode(FriendlyByteBuf buf) {
                return new BidPayload(buf.readUUID(), buf.readLong());
            }

            @Override
            public void encode(FriendlyByteBuf buf, BidPayload payload) {
                buf.writeUUID(payload.listingId());
                buf.writeLong(payload.amount());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
