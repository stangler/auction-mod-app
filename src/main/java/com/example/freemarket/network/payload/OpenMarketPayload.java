package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S→C: フリマGUIを開く命令
 */
public record OpenMarketPayload() implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "open_market");

    public static final CustomPacketPayload.Type<OpenMarketPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, OpenMarketPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenMarketPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
