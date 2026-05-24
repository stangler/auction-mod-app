package com.example.freemarket.network;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.auction.AuctionListing;
import com.example.freemarket.auction.AuctionSavedData;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MarketListing;
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
    // ModNetwork から MarketPackets::handleSellAuction で参照される
    // =====================================================

    public static void handleSellAuction(SellAuctionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            // ── mainhand からアイテム取得（サーバー側で確認） ──
            var held = sp.getMainHandItem();
            if (held.isEmpty()) {
                sp.sendSystemMessage(Component.literal("手にアイテムを持ってください"));
                return;
            }

            // ── バリデーション ──────────────────────────────
            long startPrice = Math.max(1L, payload.startPrice());
            long durationMs = payload.validatedDuration(); // 不正値は 3分 にフォールバック

            // ── AuctionListing 生成 ─────────────────────────
            // コンストラクタ: (sellerUUID, sellerName, stack, startPrice, durationMs)
            // endTimeMs への変換はコンストラクタ内で行われる
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
            AuctionSavedData auctionData = AuctionSavedData.get(sp.serverLevel());
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
            MarketSavedData marketData = MarketSavedData.get(sp.serverLevel());
            sp.getServer().getPlayerList().getPlayers().forEach(p ->
                ModNetwork.syncAuctionToPlayer(p, auctionData, marketData));
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
