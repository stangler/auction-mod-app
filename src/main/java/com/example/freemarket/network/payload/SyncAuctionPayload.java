package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.auction.AuctionListing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** S→C: オークション出品一覧 + 残高を同期 */
public record SyncAuctionPayload(
    List<AuctionDto> listings,
    long balance
) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "sync_auction");

    public static final CustomPacketPayload.Type<SyncAuctionPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, SyncAuctionPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncAuctionPayload decode(FriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<AuctionDto> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(AuctionDto.decode(buf));
                }
                long balance = buf.readLong();
                return new SyncAuctionPayload(list, balance);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncAuctionPayload payload) {
                buf.writeVarInt(payload.listings().size());
                for (AuctionDto dto : payload.listings()) {
                    dto.encode(buf);
                }
                buf.writeLong(payload.balance());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** AuctionListing の転送用軽量版 */
    public record AuctionDto(
        UUID listingId,
        String sellerName,
        String itemName,
        int itemCount,
        long startPrice,
        long currentBid,
        String topBidderName,
        long endTimeMs
    ) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(listingId);
            buf.writeUtf(sellerName);
            buf.writeUtf(itemName);
            buf.writeVarInt(itemCount);
            buf.writeLong(startPrice);
            buf.writeLong(currentBid);
            buf.writeUtf(topBidderName);
            buf.writeLong(endTimeMs);
        }

        public static AuctionDto decode(FriendlyByteBuf buf) {
            return new AuctionDto(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readLong(),
                buf.readUtf(),
                buf.readLong()
            );
        }

        public static AuctionDto from(AuctionListing listing) {
            return new AuctionDto(
                listing.id,
                listing.sellerName,
                listing.stack.getHoverName().getString(),
                listing.stack.getCount(),
                listing.startPrice,
                listing.currentBid,
                listing.topBidderName,
                listing.endTimeMs
            );
        }

        /** 残り秒数（0以下 = 終了） */
        public long remainingSecs() {
            return Math.max(0, (endTimeMs - System.currentTimeMillis()) / 1000);
        }

        /** 現在の最低入札額 */
        public long minimumBid() {
            return currentBid > 0 ? currentBid + 1 : startPrice;
        }
    }
}
