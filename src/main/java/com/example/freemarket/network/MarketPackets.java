package com.example.freemarket.network;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MarketListing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * ネットワークパケット
 * C→S: ServerboundBuyPacket  (購入)
 * C→S: ServerboundSellPacket (出品) ← Phase2で追加
 */
public class MarketPackets {

    // =====================================================
    // 購入リクエスト (C→S)
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
    // ユーティリティ
    // =====================================================
    public static void syncToPlayer(ServerPlayer player, MarketSavedData data) {
        // Phase2で実装: 一覧・残高をクライアントへ送信
        FreeMarketMod.LOGGER.debug("Syncing market data to {}", player.getName().getString());
    }
}
