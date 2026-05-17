package com.example.freemarket.data;

import com.example.freemarket.market.MarketListing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * ワールド保存データ
 * - 出品一覧
 * - プレイヤー残高（UUID → 円）
 */
public class MarketSavedData extends SavedData {

    private static final String DATA_NAME = "freemarket_data";

    // 出品一覧
    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();

    // プレイヤー残高 (UUID → 円)
    private final Map<UUID, Long> balances = new HashMap<>();

    // =========================================================
    // ファクトリ
    // =========================================================

    public static MarketSavedData get(ServerLevel level) {
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(
                MarketSavedData::load,
                MarketSavedData::new,
                DATA_NAME
            );
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

    /** 売却済み除く一覧 */
    public List<MarketListing> getActiveListings() {
        return listings.values().stream()
            .filter(l -> !l.isSold())
            .toList();
    }

    /**
     * 購入処理
     * @return true=成功, false=残高不足 or 既売却
     */
    public boolean purchase(UUID buyerId, String buyerName, UUID listingId) {
        MarketListing listing = listings.get(listingId);
        if (listing == null || listing.isSold()) return false;

        long price = listing.getPrice();
        long buyerBalance = getBalance(buyerId);
        if (buyerBalance < price) return false;

        // 残高移動
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
    // NBT シリアライズ
    // =========================================================

    @Override
    public CompoundTag save(CompoundTag tag) {
        // 出品一覧
        ListTag listingsTag = new ListTag();
        listings.values().forEach(l -> listingsTag.add(l.toNbt()));
        tag.put("listings", listingsTag);

        // 残高
        CompoundTag balancesTag = new CompoundTag();
        balances.forEach((uuid, bal) -> balancesTag.putLong(uuid.toString(), bal));
        tag.put("balances", balancesTag);

        return tag;
    }

    public static MarketSavedData load(CompoundTag tag) {
        MarketSavedData data = new MarketSavedData();

        // 出品一覧復元
        ListTag listingsTag = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingsTag.size(); i++) {
            MarketListing listing = MarketListing.fromNbt(listingsTag.getCompound(i));
            data.listings.put(listing.getListingId(), listing);
        }

        // 残高復元
        CompoundTag balancesTag = tag.getCompound("balances");
        for (String key : balancesTag.getAllKeys()) {
            data.balances.put(UUID.fromString(key), balancesTag.getLong(key));
        }

        return data;
    }
}
