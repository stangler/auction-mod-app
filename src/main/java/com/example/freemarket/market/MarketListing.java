package com.example.freemarket.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * フリーマーケット 1件の出品データ
 */
public class MarketListing {

    private final UUID listingId;   // 出品ID
    private final String sellerName; // 出品者名（プレイヤー or モブ名）
    private final UUID sellerId;    // 出品者UUID（モブはランダム固定UUID）
    private ItemStack itemStack;    // 出品アイテム
    private long price;             // 価格（日本円）
    private boolean sold;           // 売却済みフラグ

    public MarketListing(UUID listingId, String sellerName, UUID sellerId,
                         ItemStack itemStack, long price) {
        this.listingId = listingId;
        this.sellerName = sellerName;
        this.sellerId = sellerId;
        this.itemStack = itemStack.copy();
        this.price = price;
        this.sold = false;
    }

    // --- NBTシリアライズ（SavedData永続化用）---

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("listingId", listingId);
        tag.putString("sellerName", sellerName);
        tag.putUUID("sellerId", sellerId);
        tag.put("item", itemStack.save(new CompoundTag()));
        tag.putLong("price", price);
        tag.putBoolean("sold", sold);
        return tag;
    }

    public static MarketListing fromNbt(CompoundTag tag) {
        UUID listingId = tag.getUUID("listingId");
        String sellerName = tag.getString("sellerName");
        UUID sellerId = tag.getUUID("sellerId");
        ItemStack itemStack = ItemStack.of(tag.getCompound("item"));
        long price = tag.getLong("price");
        MarketListing listing = new MarketListing(listingId, sellerName, sellerId, itemStack, price);
        listing.sold = tag.getBoolean("sold");
        return listing;
    }

    // --- Getters ---

    public UUID getListingId()   { return listingId; }
    public String getSellerName(){ return sellerName; }
    public UUID getSellerId()    { return sellerId; }
    public ItemStack getItemStack(){ return itemStack.copy(); }
    public long getPrice()       { return price; }
    public boolean isSold()      { return sold; }

    public void markSold() { this.sold = true; }
}
