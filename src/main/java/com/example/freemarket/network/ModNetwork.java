package com.example.freemarket.network;

import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MarketListing;
import com.example.freemarket.network.payload.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;
import java.util.UUID;

public class ModNetwork {

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        // S→C
        reg.playToClient(
            OpenMarketPayload.TYPE,
            OpenMarketPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                // クライアントハンドラはClientNetworkHandler経由
                com.example.freemarket.client.ClientNetworkHandler.handleOpenMarket();
            })
        );

        reg.playToClient(
            SyncListingsPayload.TYPE,
            SyncListingsPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() ->
                com.example.freemarket.client.ClientNetworkHandler.handleSyncListings(payload)
            )
        );

        // C→S
        reg.playToServer(
            BuyPayload.TYPE,
            BuyPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                boolean ok = data.purchase(sp.getUUID(), sp.getName().getString(), payload.listingId());
                if (ok) {
                    data.getListing(payload.listingId()).ifPresent(l ->
                        sp.getInventory().add(l.getItemStack()));
                    sp.sendSystemMessage(Component.literal(
                        "購入完了！ 残高: ¥" + String.format("%,d", data.getBalance(sp.getUUID()))));
                } else {
                    sp.sendSystemMessage(Component.literal("購入失敗（残高不足 or 売切）"));
                }
                // 一覧を再同期
                syncListingsToPlayer(sp, data);
            })
        );

        reg.playToServer(
            SellPayload.TYPE,
            SellPayload.STREAM_CODEC,
            (payload, ctx) -> ctx.enqueueWork(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)) return;
                if (payload.price() <= 0) return;

                // メインハンドのアイテムを出品
                var held = sp.getMainHandItem();
                if (held.isEmpty()) {
                    sp.sendSystemMessage(Component.literal("手にアイテムを持ってください"));
                    return;
                }

                MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                var listing = new MarketListing(
                    UUID.randomUUID(),
                    sp.getName().getString(),
                    sp.getUUID(),
                    held.copy(),
                    payload.price()
                );
                data.addListing(listing);
                held.shrink(held.getCount()); // インベントリから消費
                sp.sendSystemMessage(Component.literal(
                    held.getHoverName().getString() + " を ¥" +
                    String.format("%,d", payload.price()) + " で出品しました"));
                syncListingsToPlayer(sp, data);
            })
        );
    }

    public static void syncListingsToPlayer(ServerPlayer sp, MarketSavedData data) {
        List<SyncListingsPayload.ListingDto> dtos = data.getActiveListings()
            .stream()
            .map(SyncListingsPayload.ListingDto::from)
            .toList();
        PacketDistributor.sendToPlayer(sp,
            new SyncListingsPayload(dtos, data.getBalance(sp.getUUID())));
    }
}
