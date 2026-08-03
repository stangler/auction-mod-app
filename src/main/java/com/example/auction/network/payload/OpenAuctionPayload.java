package com.example.auction.network.payload;

import com.example.auction.AuctionMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** S→C: オークションGUIを開く命令 */
public record OpenAuctionPayload() implements CustomPacketPayload {

    public static final Identifier ID_LOC =
        Identifier.fromNamespaceAndPath(AuctionMod.MOD_ID, "open_auction");

    public static final CustomPacketPayload.Type<OpenAuctionPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, OpenAuctionPayload> STREAM_CODEC =
        StreamCodec.unit(new OpenAuctionPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
