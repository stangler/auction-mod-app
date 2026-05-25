package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C→S: オークション出品取消リクエスト
 */
public record CancelAuctionPayload(UUID listingId) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "cancel_auction");

    public static final CustomPacketPayload.Type<CancelAuctionPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, CancelAuctionPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public CancelAuctionPayload decode(FriendlyByteBuf buf) {
                return new CancelAuctionPayload(buf.readUUID());
            }

            @Override
            public void encode(FriendlyByteBuf buf, CancelAuctionPayload payload) {
                buf.writeUUID(payload.listingId());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
