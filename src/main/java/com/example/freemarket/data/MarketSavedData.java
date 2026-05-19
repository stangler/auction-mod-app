package com.example.freemarket.data;

import com.example.freemarket.market.MarketListing;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.HashSet;

public class MarketSavedData extends SavedData {

    private static final String DATA_NAME = "freemarket_data";

    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Set<UUID> bonusReceived = new HashSet<>();

    // =========================================================
    // Factory (1.21.1 API)
    // =========================================================

    public static final SavedData.Factory<MarketSavedData> FACTORY =
        new SavedData.Factory<>(
            MarketSavedData::new,
            MarketSavedData::load,
            null
        );

    public static MarketSavedData get(ServerLevel level) {
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(FACTORY, DATA_NAME);
    }

    // =========================================================
    // 出品操作
    // =========================================================

    public UUID addListing(MarketListing listing) {
        listings.put(listing.getListingId(), listing);
        setDirty();
        return listing.getListingId();
    }

    public Optional<MarketListing> getListing(UUID id) {
        return Optional.ofNullable(listings.get(id));
    }

    public List<MarketListing> getActiveListings() {
        return listings.values().stream()
            .filter(l -> !l.isSold())
            .toList();
    }

    public boolean purchase(UUID buyerId, String buyerName, UUID listingId) {
        MarketListing listing = listings.get(listingId);
        if (listing == null || listing.isSold()) return false;

        long price = listing.getPrice();
        long buyerBalance = getBalance(buyerId);
        if (buyerBalance < price) return false;

        setBalance(buyerId, buyerBalance - price);
        addBalance(listing.getSellerId(), price);
        listing.markSold();
        setDirty();
        return true;
    }

    // =========================================================
    // 残高操作
    // =========================================================

    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    public void setBalance(UUID playerId, long amount) {
        balances.put(playerId, Math.max(0, amount));
        setDirty();
    }

    public void addBalance(UUID playerId, long amount) {
        setBalance(playerId, getBalance(playerId) + amount);
    }

    // =========================================================
    // 初回ボーナス管理
    // =========================================================

    public boolean hasReceivedBonus(UUID playerId) {
        return bonusReceived.contains(playerId);
    }

    public void markBonusReceived(UUID playerId) {
        bonusReceived.add(playerId);
        setDirty();
    }

    // =========================================================
    // NBT (1.21.1: save takes HolderLookup.Provider)
    // =========================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag listingsTag = new ListTag();
        listings.values().forEach(l -> listingsTag.add(l.toNbt(registries)));
        tag.put("listings", listingsTag);

        CompoundTag balancesTag = new CompoundTag();
        balances.forEach((uuid, bal) -> balancesTag.putLong(uuid.toString(), bal));
        tag.put("balances", balancesTag);

        // 初回ボーナス受取済みUUID一覧
        ListTag bonusTag = new ListTag();
        for (UUID uuid : bonusReceived) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            bonusTag.add(entry);
        }
        tag.put("bonusReceived", bonusTag);

        return tag;
    }

    public static MarketSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketSavedData data = new MarketSavedData();

        ListTag listingsTag = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingsTag.size(); i++) {
            MarketListing listing = MarketListing.fromNbt(listingsTag.getCompound(i), registries);
            data.listings.put(listing.getListingId(), listing);
        }

        CompoundTag balancesTag = tag.getCompound("balances");
        for (String key : balancesTag.getAllKeys()) {
            data.balances.put(UUID.fromString(key), balancesTag.getLong(key));
        }

        ListTag bonusTag = tag.getList("bonusReceived", Tag.TAG_COMPOUND);
        for (int i = 0; i < bonusTag.size(); i++) {
            data.bonusReceived.add(bonusTag.getCompound(i).getUUID("uuid"));
        }

        return data;
    }
}
