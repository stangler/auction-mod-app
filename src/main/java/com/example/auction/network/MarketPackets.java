package com.example.auction.network;

import com.example.auction.AuctionMod;
import com.example.auction.auction.AuctionListing;
import com.example.auction.auction.AuctionSavedData;
import com.example.auction.data.MarketSavedData;
import com.example.auction.market.MarketListing;
import com.example.auction.network.payload.CancelAuctionPayload;
import com.example.auction.network.payload.SellAuctionPayload;
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
                MarketSavedData data = MarketSavedData.get(sp.level());
                boolean success = data.purchase(sp.getUUID(), sp.getName().getString(), listingId);

                if (success) {
                    data.getListing(listingId).ifPresent(listing ->
                        sp.getInventory().add(listing.getItemStack()));
                    sp.sendSystemMessage(Component.literal(
                        "購入完了！ 残高: ¥" + data.getBalance(sp.getUUID())));
                } else {
                    ModNetwork.sendError(sp, "購入失敗（残高不足 or 売切）", ModNetwork.COLOR_ERROR);
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

            MarketSavedData marketData = MarketSavedData.get(sp.level());

            // ── 出品上限チェック（プレイヤーあたり3件まで） ────────
            AuctionSavedData auctionData = AuctionSavedData.get(sp.level());
            long myListings = auctionData.getAll().stream()
                .filter(l -> !l.isExpired() && l.sellerUUID.equals(sp.getUUID()))
                .count();
            if (myListings >= 3) {
                ModNetwork.sendError(sp,
                    "出品上限に達しています (上限: 3件)",
                    ModNetwork.COLOR_WARN);
                return;
            }

            // ── mainhand からアイテム取得（サーバー側で確認） ──
            var held = sp.getMainHandItem();
            if (held.isEmpty()) {
                ModNetwork.sendError(sp, "手にアイテムを持ってください", ModNetwork.COLOR_WARN);
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

            AuctionMod.LOGGER.info("[AuctionMod] auction listed: {} by {} for {}",
                listing.stack.getHoverName().getString(), sp.getName().getString(), label);

            // ── 全プレイヤーへ同期 ─────────────────────────────
            sp.level().getServer().getPlayerList().getPlayers().forEach(p ->
                ModNetwork.syncAuctionToPlayer(p, auctionData, marketData));
        });
    }

    // =====================================================
    // オークション出品取消ハンドラ (C→S) — Phase 10 追加
    // =====================================================
    public static void handleCancelAuction(CancelAuctionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;

            AuctionSavedData auctionData = AuctionSavedData.get(sp.level());
            MarketSavedData marketData   = MarketSavedData.get(sp.level());

            var opt = auctionData.getListing(payload.listingId());
            if (opt.isEmpty()) {
                ModNetwork.sendError(sp, "出品が見つかりません", ModNetwork.COLOR_WARN);
                return;
            }
            var listing = opt.get();

            // 本人確認
            if (!listing.sellerUUID.equals(sp.getUUID())) {
                ModNetwork.sendError(sp, "自分の出品のみ取消できます", ModNetwork.COLOR_WARN);
                return;
            }

            // 入札済みは取消不可
            if (listing.hasBid()) {
                ModNetwork.sendError(sp,
                    "入札済みのオークションは取消できません",
                    ModNetwork.COLOR_ERROR);
                return;
            }

            // アイテム名を先に保存
            String itemName = listing.stack.getHoverName().getString();

            // 出品削除してアイテム返却
            auctionData.removeListing(payload.listingId());
            sp.getInventory().add(listing.stack.copy());

            sp.sendSystemMessage(Component.literal(
                itemName + " のオークション出品を取消しました"));

            AuctionMod.LOGGER.info("[AuctionMod] auction cancelled: {} by {}",
                itemName, sp.getName().getString());

            // 全プレイヤーへ同期
            sp.level().getServer().getPlayerList().getPlayers()
                .forEach(p -> ModNetwork.syncAuctionToPlayer(p, auctionData, marketData));
        });
    }

    // =====================================================
    // ユーティリティ
    // =====================================================
    public static void syncToPlayer(ServerPlayer player, MarketSavedData data) {
        AuctionMod.LOGGER.debug("Syncing market data to {}", player.getName().getString());
    }

    private static String durationLabel(long ms) {
        if (ms == SellAuctionPayload.DURATION_1HOUR)  return "1時間";
        if (ms == SellAuctionPayload.DURATION_30MIN)  return "30分";
        return "3分";
    }
}
