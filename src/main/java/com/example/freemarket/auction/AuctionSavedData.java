// src/main/java/com/example/freemarket/auction/AuctionSavedData.java
package com.example.freemarket.auction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class AuctionSavedData extends SavedData {

    private static final String NAME = "freemarket_auctions";
    private static final SavedData.Factory<AuctionSavedData> FACTORY =
            new SavedData.Factory<>(
                    AuctionSavedData::new,
                    AuctionSavedData::load
            );

    // id → listing
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();

    // ── 取得 ─────────────────────────────────────────

    public static AuctionSavedData get(ServerLevel level) {
        return level.getServer()
                    .overworld()
                    .getDataStorage()
                    .computeIfAbsent(FACTORY, NAME);
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

    // ── NBT ──────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (AuctionListing l : listings.values()) {
            list.add(l.save(registries));
        }
        tag.put("auctions", list);
        return tag;
    }

    private static AuctionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        AuctionSavedData data = new AuctionSavedData();
        ListTag list = tag.getList("auctions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            AuctionListing l = AuctionListing.load(list.getCompound(i), registries);
            data.listings.put(l.id, l);
        }
        return data;
    }
}