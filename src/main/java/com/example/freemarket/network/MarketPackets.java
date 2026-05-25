package com.example.freemarket.network;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.auction.AuctionListing;
import com.example.freemarket.auction.AuctionSavedData;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MarketListing;
import com.example.freemarket.network.payload.CancelAuctionPayload;
import com.example.freemarket.network.payload.SellAuctionPayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class MarketPackets {

    // =====================================================
    // 購入リクエスト (C→S)  ※ 実処理は ModNetwork に移行済み
    // =====================================================
    public record ServerboundBuyPacket(UUID listingId) {

        public static ServerboundBuyPacket decode(FriendlyByteBuf buf) {
            return new ServerboundBuyPacket(buf.readUUID());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(listingId);
        }

        public void handle(IPayloadContext ctx) {
            ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                boolean success = data.purchase(sp.getUUID(), sp.getName().getString(), listingId);

                if (success) {
                    data.getListing(listingId).ifPresent(listing ->
                        sp.getInventory().add(listing.getItemStack()));
                    sp.sendSystemMessage(Component.literal(
                        "購入完了！ 残高: ¥" + data.getBalance(sp.getUUID())));
                } else {
                    sp.sendSystemMessage(Component.literal(
                        "購入失敗（残高不足 or 売切）"));
                }
            });
        }
    }

    // =====================================================
    // オークション出品ハンドラ (C→S) — Phase 8 追加
    // =====================================================
    public static void handleSellAuction(SellAuctionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // ── 残高チェック（¥1以上必要） ──────────────────────
            MarketSavedData marketData = MarketSavedData.get(sp.serverLevel());
            long balance = marketData.getBalance(sp.getUUID());
            if (balance < 1) {
                sp.sendSystemMessage(Component.literal(
                    "残高不足で出品できません (残高: ¥" + String.format("%,d", balance) + ")"));
                return;
            }

            // ── 出品上限チェック（プレイヤーあたり3件まで） ────────
            AuctionSavedData auctionData = AuctionSavedData.get(sp.serverLevel());
            long myListings = auctionData.getAll().stream()
                .filter(l -> !l.isExpired() && l.sellerUUID.equals(sp.getUUID()))
                .count();
            if (myListings >= 3) {
                sp.sendSystemMessage(Component.literal(
                    "出品上限に達しています (上限: 3件)"));
                return;
            }

            // ── mainhand からアイテム取得（サーバー側で確認） ──
            var held = sp.getMainHandItem();
            if (held.isEmpty()) {
                sp.sendSystemMessage(Component.literal("手にアイテムを持ってください"));
                return;
            }

            // ── バリデーション ──────────────────────────────
            long startPrice = Math.max(1L, payload.startPrice());
            long durationMs = payload.validatedDuration();

            // ── AuctionListing 生成 ─────────────────────────
            var listing = new AuctionListing(
                sp.getUUID(),
                sp.getName().getString(),
                held.copyWithCount(1),
                startPrice,
                durationMs
            );

            // ── インベントリから 1個消費 ───────────────────────
            held.shrink(1);

            // ── 保存 ────────────────────────────────────────
            auctionData.addListing(listing);

            // ── 出品者に確認メッセージ ─────────────────────────
            String label = durationLabel(durationMs);
            sp.sendSystemMessage(Component.literal(
                "[オークション] " + listing.stack.getHoverName().getString() +
                " を ¥" + String.format("%,d", startPrice) +
                " スタートで " + label + " 出品しました"));

            FreeMarketMod.LOGGER.info("[FreeMarket] auction listed: {} by {} for {}",
                listing.stack.getHoverName().getString(), sp.getName().getString(), label);

            // ── 全プレイヤーへ同期 ─────────────────────────────
            sp.getServer().getPlayerList().getPlayers().forEach(p ->
                ModNetwork.syncAuctionToPlayer(p, auctionData, marketData));
        });
    }

    // =====================================================
    // オークション出品取消ハンドラ (C→S) — Phase 10 追加
    // =====================================================
    public static void handleCancelAuction(CancelAuctionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            AuctionSavedData auctionData = AuctionSavedData.get(sp.serverLevel());
            MarketSavedData marketData   = MarketSavedData.get(sp.serverLevel());

            var opt = auctionData.getListing(payload.listingId());
            if (opt.isEmpty()) {
                sp.sendSystemMessage(Component.literal("出品が見つかりません"));
                return;
            }
            var listing = opt.get();

            // 本人確認
            if (!listing.sellerUUID.equals(sp.getUUID())) {
                sp.sendSystemMessage(Component.literal("自分の出品のみ取消できます"));
                return;
            }

            // 入札済みは取消不可
            // ※ AuctionListing の入札件数チェック
            //   currentBid がフィールドの場合: listing.currentBid > 0
            //   bids リストがフィールドの場合: !listing.bids.isEmpty()
            //   → 実際の AuctionListing フィールド名に合わせて修正すること
            if (listing.hasBid()) {
                sp.sendSystemMessage(Component.literal(
                    "入札済みのオークションは取消できません"));
                return;
            }

            // アイテム名を先に保存
            String itemName = listing.stack.getHoverName().getString();

            // 出品削除してアイテム返却
            auctionData.removeListing(payload.listingId());
            sp.getInventory().add(listing.stack.copy());

            sp.sendSystemMessage(Component.literal(
                itemName + " のオークション出品を取消しました"));

            FreeMarketMod.LOGGER.info("[FreeMarket] auction cancelled: {} by {}",
                itemName, sp.getName().getString());

            // 全プレイヤーへ同期
            sp.getServer().getPlayerList().getPlayers()
                .forEach(p -> ModNetwork.syncAuctionToPlayer(p, auctionData, marketData));
        });
    }

    // =====================================================
    // ユーティリティ
    // =====================================================
    public static void syncToPlayer(ServerPlayer player, MarketSavedData data) {
        FreeMarketMod.LOGGER.debug("Syncing market data to {}", player.getName().getString());
    }

    private static String durationLabel(long ms) {
        if (ms == SellAuctionPayload.DURATION_1HOUR)  return "1時間";
        if (ms == SellAuctionPayload.DURATION_30MIN)  return "30分";
        return "3分";
    }
}