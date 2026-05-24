package com.example.freemarket.auction;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MobListingGenerator;
import com.example.freemarket.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.UUID;

public class AuctionTickHandler {

    private static final int CHECK_INTERVAL = 100;
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

        // ★ モブ出品の自動補充（落札/流札で減った分を補う）
        MobListingGenerator.replenishAuctionIfNeeded(auctionData);

        // 全員にオークション一覧を再sync
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ModNetwork.syncAuctionToPlayer(player, auctionData, marketData);
        }
    }

    private static boolean isMobSeller(AuctionListing listing) {
        UUID mobUUID = UUID.nameUUIDFromBytes(listing.sellerName.getBytes());
        return listing.sellerUUID.equals(mobUUID);
    }

    private void settle(AuctionListing listing,
                        ServerLevel level,
                        AuctionSavedData auctionData,
                        MarketSavedData marketData) {

        if (listing.hasBid()) {
            // ── 落札処理 ────────────────────────────────────
            ServerPlayer winner = level.getServer()
                    .getPlayerList().getPlayerByName(listing.topBidderName);

            if (winner != null) {
                if (!winner.getInventory().add(listing.stack.copy())) {
                    winner.drop(listing.stack.copy(), false);
                }
                marketData.addBalance(winner.getUUID(), -listing.currentBid);
                winner.sendSystemMessage(Component.literal(
                    "[FreeMarket] 落札アイテムを受け取りました: " +
                    listing.stack.getHoverName().getString()));
            } else {
                level.getServer().getProfileCache()
                        .get(listing.topBidderName)
                        .ifPresentOrElse(
                            profile -> {
                                marketData.addBalance(profile.getId(), -listing.currentBid);
                                marketData.addPendingItem(profile.getId(), listing.stack);
                                FreeMarketMod.LOGGER.info(
                                    "[FreeMarket] 落札アイテムをキューに追加: {} → {}",
                                    listing.stack.getHoverName().getString(),
                                    listing.topBidderName);
                            },
                            () -> FreeMarketMod.LOGGER.warn(
                                "[FreeMarket] ProfileCache未解決のため未渡しキュー登録不可: {}",
                                listing.topBidderName)
                        );
            }

            marketData.addBalance(listing.sellerUUID, listing.currentBid);

        } else {
            // ── 流札処理 ────────────────────────────────────
            if (isMobSeller(listing)) {
                // モブ出品: 破棄
                FreeMarketMod.LOGGER.info(
                    "[FreeMarket] 流札（モブ出品）破棄: {}",
                    listing.stack.getHoverName().getString());
            } else {
                // プレイヤー出品: 出品者に返却
                ServerPlayer seller = level.getServer()
                        .getPlayerList().getPlayerByName(listing.sellerName);

                if (seller != null) {
                    // オンライン → 直接返却
                    if (!seller.getInventory().add(listing.stack.copy())) {
                        seller.drop(listing.stack.copy(), false);
                    }
                    seller.sendSystemMessage(Component.literal(
                        "[FreeMarket] 流札のため返却: " +
                        listing.stack.getHoverName().getString()));
                } else {
                    // オフライン → pendingItems キュー（次回ログイン時に配送）
                    level.getServer().getProfileCache()
                            .get(listing.sellerName)
                            .ifPresentOrElse(
                                profile -> {
                                    marketData.addPendingItem(profile.getId(), listing.stack);
                                    FreeMarketMod.LOGGER.info(
                                        "[FreeMarket] 流札アイテムをキューに追加（返却）: {} → {}",
                                        listing.stack.getHoverName().getString(),
                                        listing.sellerName);
                                },
                                () -> FreeMarketMod.LOGGER.warn(
                                    "[FreeMarket] ProfileCache未解決のため流札返却不可: {}",
                                    listing.sellerName)
                            );
                }
            }
        }

        auctionData.removeListing(listing.id);
    }
}
