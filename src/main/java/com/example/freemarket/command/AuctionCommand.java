package com.example.freemarket.command;

import com.example.freemarket.auction.AuctionSavedData;
import com.example.freemarket.data.MarketSavedData;
import com.example.freemarket.network.ModNetwork;
import com.example.freemarket.network.payload.OpenAuctionPayload;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public class AuctionCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("auction")

            // /auction open
            .then(Commands.literal("open")
                .executes(ctx -> {
                    if (!(ctx.getSource().getEntity() instanceof ServerPlayer sp)) return 0;

                    // GUI開放命令 → クライアントでAuctionScreen表示
                    PacketDistributor.sendToPlayer(sp, new OpenAuctionPayload());

                    // 一覧+残高を即sync（画面表示直後にデータが届く）
                    AuctionSavedData auctionData = AuctionSavedData.get(sp.serverLevel());
                    MarketSavedData  marketData  = MarketSavedData.get(sp.serverLevel());
                    ModNetwork.syncAuctionToPlayer(sp, auctionData, marketData);

                    return 1;
                })
            )
        );
    }
}
