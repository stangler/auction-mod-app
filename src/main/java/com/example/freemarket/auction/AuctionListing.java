package com.example.freemarket.auction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuctionListing {

    public final UUID id;
    public final UUID sellerUUID;
    public final String sellerName;
    public final ItemStack stack;
    public final long startPrice;
    public final long durationMs;   // 出品時に指定した期間（ms）
    public final long endTimeMs;

    public long currentBid;
    public String topBidderName;
    public final List<BidEntry> bidHistory;

    public record BidEntry(String bidderName, long amount, long timestampMs) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("bidder", bidderName);
            t.putLong("amount", amount);
            t.putLong("ts", timestampMs);
            return t;
        }
        public static BidEntry load(CompoundTag t) {
            return new BidEntry(t.getString("bidder"), t.getLong("amount"), t.getLong("ts"));
        }
    }

    // 新規作成
    public AuctionListing(UUID sellerUUID, String sellerName, ItemStack stack,
                          long startPrice, long durationMs) {
        this.id            = UUID.randomUUID();
        this.sellerUUID    = sellerUUID;
        this.sellerName    = sellerName;
        this.stack         = stack.copy();
        this.startPrice    = startPrice;
        this.durationMs    = durationMs;
        this.endTimeMs     = System.currentTimeMillis() + durationMs;
        this.currentBid    = 0L;
        this.topBidderName = "";
        this.bidHistory    = new ArrayList<>();
    }

    // NBTロード用
    private AuctionListing(UUID id, UUID sellerUUID, String sellerName, ItemStack stack,
                           long startPrice, long durationMs, long endTimeMs,
                           long currentBid, String topBidderName,
                           List<BidEntry> bidHistory) {
        this.id             = id;
        this.sellerUUID     = sellerUUID;
        this.sellerName     = sellerName;
        this.stack          = stack;
        this.startPrice     = startPrice;
        this.durationMs     = durationMs;
        this.endTimeMs      = endTimeMs;
        this.currentBid     = currentBid;
        this.topBidderName  = topBidderName;
        this.bidHistory     = bidHistory;
    }

    /** 入札。最低額 = currentBid+1（未入札時はstartPrice）。失敗時false。 */
    public boolean placeBid(String bidderName, long amount) {
        long minimum = currentBid > 0 ? currentBid + 1 : startPrice;
        if (amount < minimum) return false;
        currentBid     = amount;
        topBidderName  = bidderName;
        bidHistory.add(new BidEntry(bidderName, amount, System.currentTimeMillis()));
        return true;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= endTimeMs;
    }

    public boolean hasBid() {
        return currentBid > 0;
    }

    // ── NBT ──────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id",          id);
        t.putUUID("sellerUUID",  sellerUUID);
        t.putString("seller",    sellerName);
        t.put("item",            stack.save(registries));
        t.putLong("startPrice",  startPrice);
        t.putLong("durationMs",  durationMs);
        t.putLong("endTimeMs",   endTimeMs);
        t.putLong("currentBid",  currentBid);
        t.putString("topBidder", topBidderName);

        CompoundTag history = new CompoundTag();
        history.putInt("size", bidHistory.size());
        for (int i = 0; i < bidHistory.size(); i++) {
            history.put(String.valueOf(i), bidHistory.get(i).save());
        }
        t.put("bidHistory", history);
        return t;
    }

    public static AuctionListing load(CompoundTag t, HolderLookup.Provider registries) {
        UUID id           = t.getUUID("id");
        UUID sellerUUID   = t.getUUID("sellerUUID");
        String seller     = t.getString("seller");
        ItemStack stack   = ItemStack.parseOptional(registries, t.getCompound("item"));
        long startPrice   = t.getLong("startPrice");
        long durationMs   = t.getLong("durationMs"); // 旧データは0（"―"表示）
        long endTimeMs    = t.getLong("endTimeMs");
        long currentBid   = t.getLong("currentBid");
        String topBidder  = t.getString("topBidder");

        CompoundTag history = t.getCompound("bidHistory");
        int size = history.getInt("size");
        List<BidEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(BidEntry.load(history.getCompound(String.valueOf(i))));
        }

        return new AuctionListing(id, sellerUUID, seller, stack, startPrice, durationMs, endTimeMs,
                                  currentBid, topBidder, entries);
    }
}