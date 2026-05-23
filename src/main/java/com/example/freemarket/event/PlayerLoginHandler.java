package com.example.freemarket.event;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.data.MarketSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

public class PlayerLoginHandler {

    private static final long INITIAL_BONUS = 10_000L;

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MarketSavedData data = MarketSavedData.get(sp.serverLevel());

        // 初回のみボーナス付与
        if (!data.hasReceivedBonus(sp.getUUID())) {
            data.markBonusReceived(sp.getUUID());
            data.addBalance(sp.getUUID(), INITIAL_BONUS);
            sp.sendSystemMessage(Component.literal(
                "[FreeMarket] ようこそ！初回ボーナス ¥" +
                String.format("%,d", INITIAL_BONUS) + " を付与しました。"));
            FreeMarketMod.LOGGER.info("[FreeMarket] 初回ボーナス付与: {}", sp.getName().getString());
        }

        // 未渡しアイテムの配送
        List<ItemStack> pending = data.getPendingItems(sp.getUUID());
        if (!pending.isEmpty()) {
            int delivered = 0;
            for (ItemStack stack : pending) {
                if (!sp.getInventory().add(stack.copy())) {
                    // インベントリ満杯はドロップで対応
                    sp.drop(stack.copy(), false);
                }
                delivered++;
            }
            data.clearPendingItems(sp.getUUID());
            sp.sendSystemMessage(Component.literal(
                "[FreeMarket] オフライン中に落札されたアイテム " +
                delivered + " 件を受け取りました。"));
            FreeMarketMod.LOGGER.info(
                "[FreeMarket] 未渡しアイテム配送: {} → {}件",
                sp.getName().getString(), delivered);
        }
    }
}
