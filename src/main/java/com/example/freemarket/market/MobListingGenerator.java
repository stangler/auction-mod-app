package com.example.freemarket.market;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.auction.AuctionListing;
import com.example.freemarket.auction.AuctionSavedData;
import com.example.freemarket.data.MarketSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FreeMarketMod.MOD_ID)
public class MobListingGenerator {

    private static final List<MobListing> STARTER_LISTINGS = List.of(
        new MobListing("スティーブ", new ItemStack(Items.DIAMOND, 3),    8000),
        new MobListing("アレックス", new ItemStack(Items.BOW),           1200),
        new MobListing("スティーブ", new ItemStack(Items.OAK_LOG, 64),    500),
        new MobListing("アレックス", new ItemStack(Items.COOKED_BEEF, 16), 800)
    );

    private static final List<MobListing> VILLAGER_LISTINGS = List.of(
        new MobListing("村人A", new ItemStack(Items.BREAD, 10),        200),
        new MobListing("村人B", new ItemStack(Items.IRON_SWORD),      1500),
        new MobListing("村人C", new ItemStack(Items.GOLDEN_APPLE),    3000),
        new MobListing("村人D", new ItemStack(Items.ENCHANTING_TABLE), 5000)
    );

    // オークション初期出品（終了まで1時間）
    // 30秒 → 3分に変更
    private static final long AUCTION_DURATION_MS = 3 * 60 * 1000L; // デバッグ用（3分）／本番: 60 * 60 * 1000L（1時間）
    private static final List<MobListing> AUCTION_LISTINGS = List.of(
        new MobListing("スティーブ", new ItemStack(Items.NETHERITE_INGOT),      5000),
        new MobListing("アレックス", new ItemStack(Items.ELYTRA),              20000),
        new MobListing("村人A",     new ItemStack(Items.DIAMOND_SWORD),        3000),
        new MobListing("村人B",     new ItemStack(Items.SHULKER_BOX),          2000)
    );

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        MarketSavedData data = MarketSavedData.get(level);

        if (data.getActiveListings().isEmpty()) {
            addListings(data, STARTER_LISTINGS);
            addListings(data, VILLAGER_LISTINGS);
            FreeMarketMod.LOGGER.info("FreeMarket: モブ初期出品 {}件登録",
                STARTER_LISTINGS.size() + VILLAGER_LISTINGS.size());
        }

        // オークション初期出品（アクティブ出品ゼロの時のみ）
        AuctionSavedData auctionData = AuctionSavedData.get(level);
        boolean hasActiveAuction = auctionData.getAll().stream().anyMatch(l -> !l.isExpired());
        if (!hasActiveAuction) {
            addAuctionListings(auctionData, AUCTION_LISTINGS);
            FreeMarketMod.LOGGER.info("FreeMarket: オークション初期出品 {}件登録",
                AUCTION_LISTINGS.size());
        }
    }

    private static void addListings(MarketSavedData data, List<MobListing> templates) {
        for (MobListing t : templates) {
            UUID mobId = UUID.nameUUIDFromBytes(t.sellerName().getBytes());
            data.addListing(new MarketListing(
                UUID.randomUUID(), t.sellerName(), mobId, t.item(), t.price()
            ));
        }
    }

    private static void addAuctionListings(AuctionSavedData data, List<MobListing> templates) {
        for (MobListing t : templates) {
            UUID mobId = UUID.nameUUIDFromBytes(t.sellerName().getBytes());
            data.addListing(new AuctionListing(
                mobId, t.sellerName(), t.item(), t.price(), AUCTION_DURATION_MS
            ));
        }
    }

    record MobListing(String sellerName, ItemStack item, long price) {}
}