package com.example.freemarket.network;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.market.FleaMarketMenu;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.market.MarketListing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.registration.IPayloadRegistrar;

import java.util.UUID;

/**
 * ネットワークパケット一覧
 *
 * C→S: ServerboundBuyPacket    (購入リクエスト)
 * C→S: ServerboundSellPacket   (出品リクエスト)
 * S→C: ClientboundListingsPacket (一覧同期)
 * S→C: ClientboundBalancePacket  (残高同期)
 */
public class MarketPackets {

    public static final String CHANNEL = FreeMarketMod.MOD_ID + ":main";

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

        public void handle(PlayPayloadContext ctx) {
            ctx.workHandler().submitAsync(() -> {
                ctx.player().ifPresent(player -> {
                    if (!(player instanceof ServerPlayer sp)) return;
                    MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                    boolean success = data.purchase(sp.getUUID(), sp.getName().getString(), listingId);

                    if (success) {
                        // 購入成功 → アイテム付与
                        data.getListing(listingId).ifPresent(listing ->
                            sp.getInventory().add(listing.getItemStack()));
                        // 残高・一覧を再同期
                        syncToPlayer(sp, data);
                        sp.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "購入完了！ 残高: ¥" + data.getBalance(sp.getUUID())));
                    } else {
                        sp.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("購入失敗（残高不足 or 売切）"));
                    }
                });
            });
        }
    }

    // =====================================================
    // ユーティリティ: プレイヤーに最新データ送信
    // =====================================================
    public static void syncToPlayer(ServerPlayer player, MarketSavedData data) {
        // 実装: ClientboundListingsPacket / ClientboundBalancePacket を送信
        // （パケット実装は次フェーズで追加）
        FreeMarketMod.LOGGER.debug("Syncing market data to {}", player.getName().getString());
    }
}
