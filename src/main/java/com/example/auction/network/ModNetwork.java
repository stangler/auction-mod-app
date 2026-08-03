package com.example.auction.network;

import com.example.auction.auction.AuctionSavedData;
import com.example.auction.data.MarketSavedData;
import com.example.auction.market.MarketListing;
import com.example.auction.market.MobListingGenerator;
import com.example.auction.network.payload.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.UUID;

public class ModNetwork {

    // ── エラー色定数 ─────────────────────────────────────
    public static final int COLOR_ERROR   = 0xFF4444; // 赤: 残高不足・取消不可など
    public static final int COLOR_WARN    = 0xFF8844; // 橙: バリデーション系
    public static final int COLOR_SUCCESS = 0x44FF88; // 緑（必要時）

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        // ── フリマ S→C ──────────────────────────────────
        reg.playToClient(
            OpenMarketPayload.TYPE,
            OpenMarketPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.auction.client.ClientNetworkHandler.handleOpenMarket()
            )
        );

        reg.playToClient(
            SyncListingsPayload.TYPE,
            SyncListingsPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.auction.client.ClientNetworkHandler.handleSyncListings(payload)
            )
        );

        // ── エラーメッセージ S→C (Phase 10-④ 追加) ────────
        reg.playToClient(
            ErrorMessagePayload.TYPE,
            ErrorMessagePayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.auction.client.ClientNetworkHandler.handleErrorMessage(payload)
            )
        );

        // ── フリマ C→S ──────────────────────────────────
        reg.playToServer(
            BuyPayload.TYPE,
            BuyPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                MarketSavedData data = MarketSavedData.get(sp.level());
                boolean ok = data.purchase(sp.getUUID(), sp.getName().getString(), payload.listingId());
                if (ok) {
                    data.getListing(payload.listingId()).ifPresent(l ->
                        sp.getInventory().add(l.getItemStack()));
                    sp.sendSystemMessage(Component.literal(
                        "購入完了！ 残高: ¥" + String.format("%,d", data.getBalance(sp.getUUID()))));
                    MobListingGenerator.replenishMarketIfNeeded(data);
                } else {
                    // GUI エラー表示
                    sendError(sp, "購入失敗（残高不足 or 売切）", COLOR_ERROR);
                }
                syncListingsToPlayer(sp, data);
            })
        );

        reg.playToServer(
            SellPayload.TYPE,
            SellPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                if (payload.price() <= 0) return;

                MarketSavedData data = MarketSavedData.get(sp.level());

                var held = sp.getMainHandItem();
                if (held.isEmpty()) {
                    sendError(sp, "手にアイテムを持ってください", COLOR_WARN);
                    return;
                }

                String itemName = held.getHoverName().getString();

                var listing = new MarketListing(
                    UUID.randomUUID(),
                    sp.getName().getString(),
                    sp.getUUID(),
                    held.copy(),
                    payload.price()
                );
                data.addListing(listing);
                held.shrink(held.getCount());
                sp.sendSystemMessage(Component.literal(
                    itemName + " を ¥" +
                    String.format("%,d", payload.price()) + " で出品しました"));
                syncListingsToPlayer(sp, data);
            })
        );

        // ── フリマ出品取消 C→S (Phase 10-② 追加) ─────────
        reg.playToServer(
            CancelListingPayload.TYPE,
            CancelListingPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                MarketSavedData data = MarketSavedData.get(sp.level());

                var opt = data.getListing(payload.listingId());
                if (opt.isEmpty()) {
                    sendError(sp, "出品が見つかりません", COLOR_WARN);
                    return;
                }
                var listing = opt.get();

                // 本人確認
                if (!listing.getSellerId().equals(sp.getUUID())) {
                    sendError(sp, "自分の出品のみ取消できます", COLOR_WARN);
                    return;
                }

                String itemName = listing.getItemStack().getHoverName().getString();

                data.removeListing(payload.listingId());
                sp.getInventory().add(listing.getItemStack());

                sp.sendSystemMessage(Component.literal(
                    itemName + " の出品を取消しました"));

                syncListingsToPlayer(sp, data);
            })
        );

        // ── オークション S→C ──────────────────────────────
        reg.playToClient(
            OpenAuctionPayload.TYPE,
            OpenAuctionPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.auction.client.ClientNetworkHandler.handleOpenAuction()
            )
        );

        reg.playToClient(
            SyncAuctionPayload.TYPE,
            SyncAuctionPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.auction.client.ClientNetworkHandler.handleSyncAuction(payload)
            )
        );

        // ── オークション C→S ──────────────────────────────
        reg.playToServer(
            BidPayload.TYPE,
            BidPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                if (payload.amount() <= 0) return;

                AuctionSavedData auctionData = AuctionSavedData.get(sp.level());
                MarketSavedData marketData = MarketSavedData.get(sp.level());

                long balance = marketData.getBalance(sp.getUUID());
                if (balance < payload.amount()) {
                    sendError(sp,
                        "残高不足です (残高: ¥" + String.format("%,d", balance) + ")",
                        COLOR_ERROR);
                    syncAuctionToPlayer(sp, auctionData, marketData);
                    return;
                }

                boolean ok = auctionData.placeBid(
                    payload.listingId(),
                    sp.getName().getString(),
                    payload.amount()
                );

                if (ok) {
                    sp.sendSystemMessage(Component.literal(
                        "入札しました: ¥" + String.format("%,d", payload.amount())));
                    String itemName = auctionData.getListing(payload.listingId())
                        .map(l -> l.stack.getHoverName().getString())
                        .orElse("不明なアイテム");
                    Component broadcast = Component.literal(
                        "[オークション] " + sp.getName().getString() +
                        " が " + itemName +
                        " に ¥" + String.format("%,d", payload.amount()) + " で入札しました");
                    sp.level().getServer().getPlayerList().getPlayers()
                        .forEach(p -> p.sendSystemMessage(broadcast));
                } else {
                    sendError(sp, "入札失敗（終了済み or 金額不足）", COLOR_ERROR);
                }
                syncAuctionToPlayer(sp, auctionData, marketData);
            })
        );

        // ── オークション出品 C→S (Phase 8 追加) ─────────────
        reg.playToServer(
            SellAuctionPayload.TYPE,
            SellAuctionPayload.STREAM_CODEC,
            MarketPackets::handleSellAuction
        );

        // ── オークション出品取消 C→S (Phase 10 追加) ─────────
        reg.playToServer(
            CancelAuctionPayload.TYPE,
            CancelAuctionPayload.STREAM_CODEC,
            MarketPackets::handleCancelAuction
        );
    }

    // ── ヘルパー ─────────────────────────────────────────

    /**
     * GUI内エラーラベルをプレイヤーに送信
     * @param color  0xRRGGBB（alpha なし）
     */
    public static void sendError(ServerPlayer sp, String message, int color) {
        PacketDistributor.sendToPlayer(sp, new ErrorMessagePayload(message, color));
    }

    /** デフォルト赤エラー */
    public static void sendError(ServerPlayer sp, String message) {
        sendError(sp, message, COLOR_ERROR);
    }

    public static void syncListingsToPlayer(ServerPlayer sp, MarketSavedData data) {
        List<SyncListingsPayload.ListingDto> dtos = data.getActiveListings()
            .stream()
            .map(SyncListingsPayload.ListingDto::from)
            .toList();
        PacketDistributor.sendToPlayer(sp,
            new SyncListingsPayload(dtos, data.getBalance(sp.getUUID())));
    }

    public static void syncAuctionToPlayer(ServerPlayer sp, AuctionSavedData auctionData,
                                           MarketSavedData marketData) {
        List<SyncAuctionPayload.AuctionDto> dtos = auctionData.getAll()
            .stream()
            .filter(l -> !l.isExpired())
            .map(SyncAuctionPayload.AuctionDto::from)
            .toList();
        PacketDistributor.sendToPlayer(sp,
            new SyncAuctionPayload(dtos, marketData.getBalance(sp.getUUID())));
    }
}
