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

    // =========================================================
    // BidHistoryEntry — 入札履歴の1件分（DTO転送用）
    // =========================================================
    public record BidHistoryEntry(String bidderName, long amount, long timestampMs) {

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(bidderName);
            buf.writeLong(amount);
            buf.writeLong(timestampMs);
        }

        public static BidHistoryEntry decode(FriendlyByteBuf buf) {
            return new BidHistoryEntry(
                buf.readUtf(),
                buf.readLong(),
                buf.readLong()
            );
        }
    }

    // =========================================================
    // AuctionDto — AuctionListing の転送用軽量版
    // =========================================================
    public record AuctionDto(
        UUID listingId,
        String sellerName,
        String itemName,
        int itemCount,
        long startPrice,
        long currentBid,
        String topBidderName,
        long endTimeMs,
        List<BidHistoryEntry> bidHistory   // ★ 追加
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
            // ★ 入札履歴
            buf.writeVarInt(bidHistory.size());
            for (BidHistoryEntry e : bidHistory) {
                e.encode(buf);
            }
        }

        public static AuctionDto decode(FriendlyByteBuf buf) {
            UUID   listingId     = buf.readUUID();
            String sellerName    = buf.readUtf();
            String itemName      = buf.readUtf();
            int    itemCount     = buf.readVarInt();
            long   startPrice    = buf.readLong();
            long   currentBid    = buf.readLong();
            String topBidderName = buf.readUtf();
            long   endTimeMs     = buf.readLong();
            // ★ 入札履歴
            int histSize = buf.readVarInt();
            List<BidHistoryEntry> history = new ArrayList<>(histSize);
            for (int i = 0; i < histSize; i++) {
                history.add(BidHistoryEntry.decode(buf));
            }
            return new AuctionDto(
                listingId, sellerName, itemName, itemCount,
                startPrice, currentBid, topBidderName, endTimeMs,
                history
            );
        }

        public static AuctionDto from(AuctionListing listing) {
            // ★ BidEntry → BidHistoryEntry に変換
            List<BidHistoryEntry> history = new ArrayList<>(listing.bidHistory.size());
            for (AuctionListing.BidEntry e : listing.bidHistory) {
                history.add(new BidHistoryEntry(e.bidderName(), e.amount(), e.timestampMs()));
            }
            return new AuctionDto(
                listing.id,
                listing.sellerName,
                listing.stack.getHoverName().getString(),
                listing.stack.getCount(),
                listing.startPrice,
                listing.currentBid,
                listing.topBidderName,
                listing.endTimeMs,
                history
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