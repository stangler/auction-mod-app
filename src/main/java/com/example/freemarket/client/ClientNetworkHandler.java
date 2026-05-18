package com.example.freemarket.client;

import com.example.freemarket.network.payload.SyncListingsPayload;
import net.minecraft.client.Minecraft;

/**
 * クライアント専用パケットハンドラ
 * サーバー側クラスから直接Minecraft.getInstanceを呼ばないための分離
 */
public class ClientNetworkHandler {

    public static void handleOpenMarket() {
        Minecraft mc = Minecraft.getInstance();
        // 一覧データはSyncListingsPayloadで別途届く → 先にGUI開く
        mc.setScreen(new FleaMarketScreen());
    }

    public static void handleSyncListings(SyncListingsPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FleaMarketScreen screen) {
            screen.updateListings(payload.listings(), payload.balance());
        }
    }
}
