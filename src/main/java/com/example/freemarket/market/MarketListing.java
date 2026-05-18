package com.example.freemarket.market;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MarketListing {

    private final UUID listingId;
    private final String sellerName;
    private final UUID sellerId;
    private ItemStack itemStack;
    private long price;
    private boolean sold;

    public MarketListing(UUID listingId, String sellerName, UUID sellerId,
                         ItemStack itemStack, long price) {
        this.listingId = listingId;
        this.sellerName = sellerName;
        this.sellerId = sellerId;
        this.itemStack = itemStack.copy();
        this.price = price;
        this.sold = false;
    }

    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("listingId", listingId);
        tag.putString("sellerName", sellerName);
        tag.putUUID("sellerId", sellerId);
        tag.put("item", itemStack.save(registries));
        tag.putLong("price", price);
        tag.putBoolean("sold", sold);
        return tag;
    }

    public static MarketListing fromNbt(CompoundTag tag, HolderLookup.Provider registries) {
        UUID listingId    = tag.getUUID("listingId");
        String sellerName = tag.getString("sellerName");
        UUID sellerId     = tag.getUUID("sellerId");
        ItemStack itemStack = ItemStack.parseOptional(registries, tag.getCompound("item"));
        long price        = tag.getLong("price");
        MarketListing listing = new MarketListing(listingId, sellerName, sellerId, itemStack, price);
        listing.sold = tag.getBoolean("sold");
        return listing;
    }

    public UUID getListingId()     { return listingId; }
    public String getSellerName()  { return sellerName; }
    public UUID getSellerId()      { return sellerId; }
    public ItemStack getItemStack(){ return itemStack.copy(); }
    public long getPrice()         { return price; }
    public boolean isSold()        { return sold; }
    public void markSold()         { this.sold = true; }
}
