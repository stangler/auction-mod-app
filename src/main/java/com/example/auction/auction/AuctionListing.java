package com.example.auction.auction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AuctionListing {

    private static final Codec<BidEntry> BID_ENTRY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.fieldOf("bidder").forGetter(BidEntry::bidderName),
        Codec.LONG.fieldOf("amount").forGetter(BidEntry::amount),
        Codec.LONG.fieldOf("ts").forGetter(BidEntry::timestampMs)
    ).apply(inst, BidEntry::new));

    public static final Codec<AuctionListing> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(l -> l.id),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("sellerUUID").forGetter(l -> l.sellerUUID),
        Codec.STRING.fieldOf("sellerName").forGetter(l -> l.sellerName),
        ItemStack.CODEC.fieldOf("item").forGetter(l -> l.stack),
        Codec.LONG.fieldOf("startPrice").forGetter(l -> l.startPrice),
        Codec.LONG.fieldOf("durationMs").forGetter(l -> l.durationMs),
        Codec.LONG.fieldOf("endTimeMs").forGetter(l -> l.endTimeMs),
        Codec.LONG.fieldOf("currentBid").forGetter(l -> l.currentBid),
        Codec.STRING.fieldOf("topBidder").forGetter(l -> l.topBidderName),
        BID_ENTRY_CODEC.listOf().fieldOf("bidHistory").forGetter(l -> l.bidHistory)
    ).apply(inst, AuctionListing::new));

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
            return new BidEntry(
                t.getStringOr("bidder", ""),
                t.getLongOr("amount", 0L),
                t.getLongOr("ts", 0L)
            );
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
    public AuctionListing(UUID id, UUID sellerUUID, String sellerName, ItemStack stack,
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
        t.putString("id",           id.toString());
        t.putString("sellerUUID",   sellerUUID.toString());
        t.putString("seller",       sellerName);
        t.put("item",               saveItemStack(stack, registries));
        t.putLong("startPrice",     startPrice);
        t.putLong("durationMs",     durationMs);
        t.putLong("endTimeMs",      endTimeMs);
        t.putLong("currentBid",     currentBid);
        t.putString("topBidder",    topBidderName);

        CompoundTag history = new CompoundTag();
        history.putInt("size", bidHistory.size());
        for (int i = 0; i < bidHistory.size(); i++) {
            history.put(String.valueOf(i), bidHistory.get(i).save());
        }
        t.put("bidHistory", history);
        return t;
    }

    public static AuctionListing load(CompoundTag t, HolderLookup.Provider registries) {
        UUID id           = UUID.fromString(t.getStringOr("id", UUID.randomUUID().toString()));
        UUID sellerUUID   = UUID.fromString(t.getStringOr("sellerUUID", UUID.randomUUID().toString()));
        String seller     = t.getStringOr("seller", "");
        ItemStack stack   = loadItemStack(t.getCompoundOrEmpty("item"), registries);
        long startPrice   = t.getLongOr("startPrice", 0L);
        long durationMs   = t.getLongOr("durationMs", 0L); // 旧データは0（"―"表示）
        long endTimeMs    = t.getLongOr("endTimeMs", 0L);
        long currentBid   = t.getLongOr("currentBid", 0L);
        String topBidder  = t.getStringOr("topBidder", "");

        CompoundTag history = t.getCompoundOrEmpty("bidHistory");
        int size = history.getIntOr("size", 0);
        List<BidEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(BidEntry.load(history.getCompoundOrEmpty(String.valueOf(i))));
        }

        return new AuctionListing(id, sellerUUID, seller, stack, startPrice, durationMs, endTimeMs,
                                  currentBid, topBidder, entries);
    }

    // ItemStack の Codec ベース保存
    public static CompoundTag saveItemStack(ItemStack stack, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        return ItemStack.CODEC.encodeStart(ops, stack).result()
            .filter(t -> t instanceof CompoundTag)
            .map(t -> (CompoundTag) t)
            .orElseGet(CompoundTag::new);
    }

    public static ItemStack loadItemStack(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.isEmpty()) return ItemStack.EMPTY;
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
    }
}