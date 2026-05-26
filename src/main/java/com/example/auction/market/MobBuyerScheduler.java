package com.example.auction.market;

import com.example.auction.auction.AuctionListing;
import com.example.auction.auction.AuctionSavedData;
import com.example.auction.data.MarketSavedData;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.*;
import java.util.stream.Collectors;

public class MobBuyerScheduler {

    // ── 定数 ────────────────────────────────────────────────

    private static final long   MOB_INITIAL_BALANCE = 10_000L;
    private static final int    INTERVAL_TICKS      = 1200;   // 60秒（20tick/秒）
    private static final double FLEA_BUY_CHANCE     = 0.30;
    private static final double BID_MAX_MULT        = 1.50;   // currentBid の最大150%

    // ── 状態 ────────────────────────────────────────────────

    private int tickCounter = 0;
    private final Random random = new Random();

    // ── Tickイベント ─────────────────────────────────────────

    @SubscribeEvent
    public void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        tickCounter++;
        if (tickCounter < INTERVAL_TICKS) return;
        tickCounter = 0;

        MarketSavedData  market  = MarketSavedData.get(level);
        AuctionSavedData auction = AuctionSavedData.get(level);

        initMobBalances(market);
        doFleaMarketBuy(market);
        doAuctionBid(market, auction);
    }

    // ── 残高初期化 ───────────────────────────────────────────

    /** 未初期化モブに ¥10,000 付与 */
    private void initMobBalances(MarketSavedData market) {
        for (String name : MobConstants.MOB_NAMES) {
            UUID uuid = MobConstants.mobUUID(name);
            if (market.getBalance(uuid) == 0L) {
                market.setBalance(uuid, MOB_INITIAL_BALANCE);
            }
        }
    }

    // ── フリマ購入 ───────────────────────────────────────────

    /**
     * 30%確率でアクティブ出品からランダム1件購入。
     * 出品者本人のモブは購入者候補から除外。
     */
    private void doFleaMarketBuy(MarketSavedData market) {
        if (random.nextDouble() >= FLEA_BUY_CHANCE) return;

        List<MarketListing> active = market.getActiveListings();
        if (active.isEmpty()) return;

        MarketListing target = active.get(random.nextInt(active.size()));

        // 出品者以外のモブを購入者候補とする
        List<String> candidates = Arrays.stream(MobConstants.MOB_NAMES)
            .filter(name -> !MobConstants.mobUUID(name).equals(target.getSellerId()))
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return;

        String mobName = candidates.get(random.nextInt(candidates.size()));
        UUID   mobId   = MobConstants.mobUUID(mobName);

        if (market.getBalance(mobId) >= target.getPrice()) {
            market.purchase(mobId, mobName, target.getListingId());
        }
    }

    // ── オークション入札 ─────────────────────────────────────

    /**
     * アクティブなオークションからランダム1件を選択し入札。
     * - 出品者と同名モブは除外
     * - 既に最高入札者のモブは除外（別モブに再抽選）
     * - currentBid × 150% 以内でランダム入札
     * - 残高不足なら候補から除外して再抽選、それでも不足ならスキップ
     * 再入札：次回スケジューラ起動時に再度抽選されるため自然に実現。
     */
    private void doAuctionBid(MarketSavedData market, AuctionSavedData auction) {
        List<AuctionListing> active = auction.getAll().stream()
            .filter(l -> !l.isExpired())
            .collect(Collectors.toList());
        if (active.isEmpty()) return;

        AuctionListing target = active.get(random.nextInt(active.size()));

        // 入札最低額・上限を計算
        long minimum = target.currentBid > 0 ? target.currentBid + 1 : target.startPrice;
        long maxBid  = (long)((target.currentBid > 0 ? target.currentBid : target.startPrice)
                               * BID_MAX_MULT);
        if (maxBid < minimum) return;

        // 入札額をランダム決定
        long bidAmount = minimum + (maxBid > minimum
            ? (long)(random.nextDouble() * (maxBid - minimum))
            : 0L);

        // 候補モブ: 出品者・現在最高入札者・残高不足を除外
        List<String> candidates = Arrays.stream(MobConstants.MOB_NAMES)
            .filter(name -> !name.equals(target.sellerName))
            .filter(name -> !name.equals(target.topBidderName))
            .filter(name -> market.getBalance(MobConstants.mobUUID(name)) >= bidAmount)
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return;

        String mobName = candidates.get(random.nextInt(candidates.size()));
        auction.placeBid(target.id, mobName, bidAmount);
    }
}
