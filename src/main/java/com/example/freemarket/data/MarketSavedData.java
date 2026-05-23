package com.example.freemarket.data;

import com.example.freemarket.market.MarketListing;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class MarketSavedData extends SavedData {

    private static final String DATA_NAME = "freemarket_data";

    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Set<UUID> bonusReceived = new HashSet<>();

    /** オフライン落札者へのアイテム未渡しキュー */
    private final Map<UUID, List<ItemStack>> pendingItems = new HashMap<>();

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
    // 未渡しアイテムキュー
    // =========================================================

    /** オフライン落札者のアイテムをキューに追加 */
    public void addPendingItem(UUID playerId, ItemStack stack) {
        pendingItems.computeIfAbsent(playerId, k -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    /** キューのアイテム一覧を取得（空ならemptyList） */
    public List<ItemStack> getPendingItems(UUID playerId) {
        return List.copyOf(pendingItems.getOrDefault(playerId, List.of()));
    }

    /** 配送完了後にキューをクリア */
    public void clearPendingItems(UUID playerId) {
        pendingItems.remove(playerId);
        setDirty();
    }

    // =========================================================
    // NBT (1.21.1: save takes HolderLookup.Provider)
    // =========================================================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // 出品一覧
        ListTag listingsTag = new ListTag();
        listings.values().forEach(l -> listingsTag.add(l.toNbt(registries)));
        tag.put("listings", listingsTag);

        // 残高
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

        // 未渡しアイテムキュー
        CompoundTag pendingTag = new CompoundTag();
        pendingItems.forEach((uuid, items) -> {
            ListTag itemList = new ListTag();
            for (ItemStack stack : items) {
                itemList.add(stack.save(registries));
            }
            pendingTag.put(uuid.toString(), itemList);
        });
        tag.put("pendingItems", pendingTag);

        return tag;
    }

    public static MarketSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MarketSavedData data = new MarketSavedData();

        // 出品一覧
        ListTag listingsTag = tag.getList("listings", Tag.TAG_COMPOUND);
        for (int i = 0; i < listingsTag.size(); i++) {
            MarketListing listing = MarketListing.fromNbt(listingsTag.getCompound(i), registries);
            data.listings.put(listing.getListingId(), listing);
        }

        // 残高
        CompoundTag balancesTag = tag.getCompound("balances");
        for (String key : balancesTag.getAllKeys()) {
            data.balances.put(UUID.fromString(key), balancesTag.getLong(key));
        }

        // 初回ボーナス受取済み
        ListTag bonusTag = tag.getList("bonusReceived", Tag.TAG_COMPOUND);
        for (int i = 0; i < bonusTag.size(); i++) {
            data.bonusReceived.add(bonusTag.getCompound(i).getUUID("uuid"));
        }

        // 未渡しアイテムキュー
        CompoundTag pendingTag = tag.getCompound("pendingItems");
        for (String key : pendingTag.getAllKeys()) {
            UUID uuid = UUID.fromString(key);
            ListTag itemList = pendingTag.getList(key, Tag.TAG_COMPOUND);
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < itemList.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, itemList.getCompound(i));
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
            if (!items.isEmpty()) {
                data.pendingItems.put(uuid, items);
            }
        }

        return data;
    }
}
