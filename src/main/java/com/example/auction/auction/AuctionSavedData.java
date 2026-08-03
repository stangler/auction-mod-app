package com.example.auction.auction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class AuctionSavedData extends SavedData {

    private static final Codec<AuctionSavedData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        AuctionListing.CODEC.listOf().fieldOf("listings").forGetter(data ->
            new ArrayList<>(data.listings.values()))
    ).apply(inst, AuctionSavedData::fromList));

    public static final SavedDataType<AuctionSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("auctionmod", "auctions"),
        AuctionSavedData::new,
        CODEC,
        null
    );

    /** Codec デシリアライズ用 */
    private static AuctionSavedData fromList(List<AuctionListing> listings) {
        AuctionSavedData data = new AuctionSavedData();
        for (AuctionListing l : listings) {
            data.listings.put(l.id, l);
        }
        return data;
    }

    // id → listing
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();

    // ── 取得 ─────────────────────────────────────────

    public static AuctionSavedData get(ServerLevel level) {
        return level.getServer()
                    .overworld()
                    .getDataStorage()
                    .computeIfAbsent(TYPE);
    }

    // ── CRUD ─────────────────────────────────────────

    public void addListing(AuctionListing listing) {
        listings.put(listing.id, listing);
        setDirty();
    }

    public Optional<AuctionListing> getListing(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    public List<AuctionListing> getAll() {
        return List.copyOf(listings.values());
    }

    public List<AuctionListing> getExpired() {
        return listings.values().stream()
                .filter(AuctionListing::isExpired)
                .toList();
    }

    public void removeListing(UUID id) {
        listings.remove(id);
        setDirty();
    }

    /** 入札。成功時true。dirty自動セット。 */
    public boolean placeBid(UUID listingId, String bidderName, long amount) {
        AuctionListing listing = listings.get(listingId);
        if (listing == null || listing.isExpired()) return false;
        boolean ok = listing.placeBid(bidderName, amount);
        if (ok) setDirty();
        return ok;
    }
}