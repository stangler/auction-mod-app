package com.example.freemarket.event;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.data.MarketSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public class PlayerLoginHandler {

    private static final long INITIAL_BONUS = 10_000L;

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        MarketSavedData data = MarketSavedData.get(sp.serverLevel());

        // 初回のみ付与（残高0 かつ 未付与記録なし）
        if (!data.hasReceivedBonus(sp.getUUID())) {
            data.markBonusReceived(sp.getUUID());
            data.addBalance(sp.getUUID(), INITIAL_BONUS);
            sp.sendSystemMessage(Component.literal(
                "[FreeMarket] ようこそ！初回ボーナス ¥" +
                String.format("%,d", INITIAL_BONUS) + " を付与しました。"));
            FreeMarketMod.LOGGER.info("[FreeMarket] 初回ボーナス付与: {}", sp.getName().getString());
        }
    }
}
