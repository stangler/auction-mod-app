package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * C→S: フリマ出品取消リクエスト
 */
public record CancelListingPayload(UUID listingId) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "cancel_listing");

    public static final CustomPacketPayload.Type<CancelListingPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, CancelListingPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public CancelListingPayload decode(FriendlyByteBuf buf) {
                return new CancelListingPayload(buf.readUUID());
            }

            @Override
            public void encode(FriendlyByteBuf buf, CancelListingPayload payload) {
                buf.writeUUID(payload.listingId());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
