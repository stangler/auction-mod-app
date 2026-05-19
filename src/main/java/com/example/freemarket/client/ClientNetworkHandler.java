package com.example.freemarket.client;

import com.example.freemarket.network.payload.SyncAuctionPayload;
import com.example.freemarket.network.payload.SyncListingsPayload;
import net.minecraft.client.Minecraft;

/**
 * クライアント専用パケットハンドラ
 * サーバー側クラスから直接Minecraft.getInstanceを呼ばないための分離
 */
public class ClientNetworkHandler {

    // ── フリマ ───────────────────────────────────────────

    public static void handleOpenMarket() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new FleaMarketScreen());
    }

    public static void handleSyncListings(SyncListingsPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FleaMarketScreen screen) {
            screen.updateListings(payload.listings(), payload.balance());
        }
    }

    // ── オークション ★追加 ───────────────────────────────

    public static void handleOpenAuction() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AuctionScreen());
    }

    public static void handleSyncAuction(SyncAuctionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AuctionScreen screen) {
            screen.updateListings(payload.listings(), payload.balance());
        }
    }
}
