package com.example.freemarket.auction;

import com.example.freemarket.data.MarketSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;

public class AuctionTickHandler {

    private static final int CHECK_INTERVAL = 100; // ticks（5秒）
    private int ticker = 0;

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.equals(level.getServer().overworld())) return;

        if (++ticker < CHECK_INTERVAL) return;
        ticker = 0;

        AuctionSavedData auctionData = AuctionSavedData.get(level);
        MarketSavedData  marketData  = MarketSavedData.get(level);

        List<AuctionListing> expired = auctionData.getExpired();
        if (expired.isEmpty()) return;

        for (AuctionListing listing : expired) {
            settle(listing, level, auctionData, marketData);
        }
    }

    private void settle(AuctionListing listing,
                        ServerLevel level,
                        AuctionSavedData auctionData,
                        MarketSavedData marketData) {

        if (listing.hasBid()) {
            ServerPlayer winner = level.getServer()
                    .getPlayerList().getPlayerByName(listing.topBidderName);

            if (winner != null) {
                // オンライン: 即渡し
                if (!winner.getInventory().add(listing.stack.copy())) {
                    winner.drop(listing.stack.copy(), false);
                }
                marketData.addBalance(winner.getUUID(), -listing.currentBid);
            } else {
                // オフライン: ProfileCacheでUUID解決して代金徴収
                level.getServer().getProfileCache()
                        .get(listing.topBidderName)
                        .ifPresent(profile ->
                            marketData.addBalance(profile.getId(), -listing.currentBid));
                // アイテム未渡しログ（Phase4で未渡しキュー実装予定）
                level.getServer().sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "[FreeMarket] 落札アイテム未渡し: " + listing.topBidderName));
            }

            // 出品者に代金付与
            marketData.addBalance(listing.sellerUUID, listing.currentBid);

        } else {
            // 流札: 出品者にアイテム返却
            ServerPlayer seller = level.getServer()
                    .getPlayerList().getPlayerByName(listing.sellerName);

            if (seller != null) {
                if (!seller.getInventory().add(listing.stack.copy())) {
                    seller.drop(listing.stack.copy(), false);
                }
            } else {
                level.getServer().sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                        "[FreeMarket] 流札アイテム未返却: " + listing.sellerName));
            }
        }

        auctionData.removeListing(listing.id);
    }
}