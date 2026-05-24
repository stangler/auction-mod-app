package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.market.MarketListing;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SyncListingsPayload(
    List<ListingDto> listings,
    long balance
) implements CustomPacketPayload {

    public static final ResourceLocation ID_LOC =
        ResourceLocation.fromNamespaceAndPath(FreeMarketMod.MOD_ID, "sync_listings");

    public static final CustomPacketPayload.Type<SyncListingsPayload> TYPE =
        new CustomPacketPayload.Type<>(ID_LOC);

    public static final StreamCodec<FriendlyByteBuf, SyncListingsPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncListingsPayload decode(FriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<ListingDto> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(ListingDto.decode(buf));
                long balance = buf.readLong();
                return new SyncListingsPayload(list, balance);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncListingsPayload payload) {
                buf.writeVarInt(payload.listings().size());
                for (ListingDto dto : payload.listings()) dto.encode(buf);
                buf.writeLong(payload.balance());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record ListingDto(UUID listingId, String sellerName, String itemName,
                             int itemCount, long price, String itemId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(listingId);
            buf.writeUtf(sellerName);
            buf.writeUtf(itemName);
            buf.writeVarInt(itemCount);
            buf.writeLong(price);
            buf.writeUtf(itemId);
        }

        public static ListingDto decode(FriendlyByteBuf buf) {
            return new ListingDto(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readLong(),
                buf.readUtf()
            );
        }

        public static ListingDto from(MarketListing listing) {
            String itemId = BuiltInRegistries.ITEM
                .getKey(listing.getItemStack().getItem()).toString();
            return new ListingDto(
                listing.getListingId(),
                listing.getSellerName(),
                listing.getItemStack().getHoverName().getString(),
                listing.getItemStack().getCount(),
                listing.getPrice(),
                itemId
            );
        }
    }
}