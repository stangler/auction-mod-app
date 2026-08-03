package com.example.auction.data;

import com.example.auction.market.MarketListing;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public class MarketSavedData extends SavedData {

    private static final Codec<Map<UUID, Long>> BALANCES_CODEC =
        Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), Codec.LONG);

    private static final Codec<List<ItemStack>> ITEM_LIST_CODEC =
        ItemStack.CODEC.listOf();

    private static final Codec<Map<UUID, List<ItemStack>>> PENDING_CODEC =
        Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), ITEM_LIST_CODEC);

    private static final Codec<MarketSavedData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        MarketListing.CODEC.listOf().fieldOf("listings").forGetter(data ->
            new ArrayList<>(data.listings.values())),
        BALANCES_CODEC.fieldOf("balances").forGetter(data -> data.balances),
        Codec.STRING.xmap(UUID::fromString, UUID::toString)
            .listOf().fieldOf("bonusReceived").forGetter(data ->
                new ArrayList<>(data.bonusReceived)),
        Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), ITEM_LIST_CODEC)
            .fieldOf("pendingItems").forGetter(data -> data.pendingItems)
    ).apply(inst, MarketSavedData::fromParts));

    public static final SavedDataType<MarketSavedData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("auctionmod", "data"),
        MarketSavedData::new,
        CODEC,
        null
    );

    /** Codec デシリアライズ用 */
    private static MarketSavedData fromParts(List<MarketListing> listings,
                                             Map<UUID, Long> balances,
                                             List<UUID> bonusReceived,
                                             Map<UUID, List<ItemStack>> pendingItems) {
        MarketSavedData data = new MarketSavedData();
        for (MarketListing l : listings) {
            data.listings.put(l.getListingId(), l);
        }
        data.balances.putAll(balances);
        data.bonusReceived.addAll(bonusReceived);
        data.pendingItems.putAll(pendingItems);
        return data;
    }

    private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();
    private final Map<UUID, Long> balances = new HashMap<>();
    private final Set<UUID> bonusReceived = new HashSet<>();

    /** オフライン落札者へのアイテム未渡しキュー */
    private final Map<UUID, List<ItemStack>> pendingItems = new HashMap<>();

    public static MarketSavedData get(ServerLevel level) {
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(TYPE);
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

    /** 出品取消（プレイヤー自身が取り消す場合に使用） */
    public void removeListing(UUID id) {
        listings.remove(id);
        setDirty();
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
        long fee = isMobSeller(listing) ? 0L : calcFee(price);
        addBalance(listing.getSellerId(), price - fee);
        listing.markSold();
        setDirty();
        return true;
    }

    // =========================================================
    // 手数料
    // =========================================================

    /** 出品手数料：5%・最低¥1（モブ出品者には適用しない） */
    public static long calcFee(long price) {
        return Math.max(1L, Math.round(price * 0.05));
    }

    private static boolean isMobSeller(MarketListing listing) {
        UUID mobUUID = UUID.nameUUIDFromBytes(listing.getSellerName().getBytes());
        return listing.getSellerId().equals(mobUUID);
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
}