package com.example.freemarket.command;

import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.network.ModNetwork;
import com.example.freemarket.network.payload.OpenMarketPayload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class MarketCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("market")

            // /market open
            .then(Commands.literal("open")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    // GUI表示命令
                    PacketDistributor.sendToPlayer(sp, new OpenMarketPayload());
                    // 出品一覧＋残高を即座に同期（画面表示直後にデータが届く）
                    MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                    ModNetwork.syncListingsToPlayer(sp, data);
                    return 1;
                })
            )

            // /market balance
            .then(Commands.literal("balance")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                    MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                    long bal = data.getBalance(sp.getUUID());
                    sp.sendSystemMessage(Component.literal(
                        "残高: ¥" + String.format("%,d", bal)));
                    return 1;
                })
            )

            // /market give <amount>  ← デバッグ用
            .then(Commands.literal("give")
                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .executes(ctx -> {
                        if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;
                        long amount = LongArgumentType.getLong(ctx, "amount");
                        MarketSavedData data = MarketSavedData.get(sp.serverLevel());
                        data.addBalance(sp.getUUID(), amount);
                        sp.sendSystemMessage(Component.literal(
                            "¥" + String.format("%,d", amount) + " 付与。残高: ¥" +
                            String.format("%,d", data.getBalance(sp.getUUID()))));
                        return 1;
                    })
                )
            )
        );
    }
}
