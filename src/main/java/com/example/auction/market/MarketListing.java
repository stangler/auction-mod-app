package com.example.auction.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class MarketListing {

    public static final Codec<MarketListing> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("listingId").forGetter(MarketListing::getListingId),
        Codec.STRING.fieldOf("sellerName").forGetter(MarketListing::getSellerName),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("sellerId").forGetter(MarketListing::getSellerId),
        ItemStack.CODEC.fieldOf("item").forGetter(MarketListing::getItemStack),
        Codec.LONG.fieldOf("price").forGetter(MarketListing::getPrice),
        Codec.BOOL.fieldOf("sold").forGetter(MarketListing::isSold)
    ).apply(inst, MarketListing::new));

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

    // Codec デシリアライズ用
    private MarketListing(UUID listingId, String sellerName, UUID sellerId,
                          ItemStack itemStack, long price, boolean sold) {
        this.listingId = listingId;
        this.sellerName = sellerName;
        this.sellerId = sellerId;
        this.itemStack = itemStack.copy();
        this.price = price;
        this.sold = sold;
    }

    public UUID getListingId()     { return listingId; }
    public String getSellerName()  { return sellerName; }
    public UUID getSellerId()      { return sellerId; }
    public ItemStack getItemStack(){ return itemStack.copy(); }
    public long getPrice()         { return price; }
    public boolean isSold()        { return sold; }
    public void markSold()         { this.sold = true; }
}