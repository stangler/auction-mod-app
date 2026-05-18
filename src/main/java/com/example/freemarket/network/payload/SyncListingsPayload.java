package com.example.freemarket.network.payload;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.market.MarketListing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * S→C: 出品一覧 + 残高を同期
 */
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
                for (int i = 0; i < size; i++) {
                    list.add(ListingDto.decode(buf));
                }
                long balance = buf.readLong();
                return new SyncListingsPayload(list, balance);
            }

            @Override
            public void encode(FriendlyByteBuf buf, SyncListingsPayload payload) {
                buf.writeVarInt(payload.listings().size());
                for (ListingDto dto : payload.listings()) {
                    dto.encode(buf);
                }
                buf.writeLong(payload.balance());
            }
        };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // DTO: MarketListingの転送用軽量版
    public record ListingDto(UUID listingId, String sellerName, String itemName,
                             int itemCount, long price) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(listingId);
            buf.writeUtf(sellerName);
            buf.writeUtf(itemName);
            buf.writeVarInt(itemCount);
            buf.writeLong(price);
        }

        public static ListingDto decode(FriendlyByteBuf buf) {
            return new ListingDto(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readLong()
            );
        }

        public static ListingDto from(MarketListing listing) {
            return new ListingDto(
                listing.getListingId(),
                listing.getSellerName(),
                listing.getItemStack().getHoverName().getString(),
                listing.getItemStack().getCount(),
                listing.getPrice()
            );
        }
    }
}
