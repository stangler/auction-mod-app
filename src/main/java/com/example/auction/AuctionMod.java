package com.example.auction;

import com.example.auction.auction.AuctionTickHandler;
import com.example.auction.command.AuctionCommand;
import com.example.auction.command.MarketCommand;
import com.example.auction.event.PlayerLoginHandler;
import com.example.auction.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(AuctionMod.MOD_ID)
public class AuctionMod {

    public static final String MOD_ID = "auctionmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AuctionMod(IEventBus modEventBus) {
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModNetwork.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.register(new AuctionTickHandler());
        NeoForge.EVENT_BUS.register(new PlayerLoginHandler());
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("AuctionMod: common setup");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MarketCommand.register(event.getDispatcher());
        AuctionCommand.register(event.getDispatcher());
        LOGGER.info("AuctionMod: /market /auction コマンド登録");
    }
}