package com.example.auction.client;

import com.example.auction.network.payload.ErrorMessagePayload;
import com.example.auction.network.payload.SyncAuctionPayload;
import com.example.auction.network.payload.SyncListingsPayload;
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

    // ── オークション ─────────────────────────────────────

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

    // ── GUI エラーラベル (Phase 10-④ 追加) ─────────────────

    /**
     * S→C エラーメッセージ受信
     * 現在開いている画面に応じて showStatus() を呼ぶ。
     * フリマ・オークション以外の画面では何もしない。
     */
    public static void handleErrorMessage(ErrorMessagePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FleaMarketScreen screen) {
            screen.showStatus(payload.message(), payload.color());
        } else if (mc.screen instanceof AuctionScreen screen) {
            screen.showStatus(payload.message(), payload.color());
        }
    }
}
