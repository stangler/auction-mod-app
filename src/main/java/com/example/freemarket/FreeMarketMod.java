package com.example.freemarket;

import com.example.freemarket.auction.AuctionTickHandler;
import com.example.freemarket.command.MarketCommand;
import com.example.freemarket.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

@Mod(FreeMarketMod.MOD_ID)
public class FreeMarketMod {

    public static final String MOD_ID = "freemarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FreeMarketMod(IEventBus modEventBus) {
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModNetwork.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.register(new AuctionTickHandler()); // ← 追加
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("FreeMarket: common setup");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MarketCommand.register(event.getDispatcher());
        LOGGER.info("FreeMarket: /market コマンド登録");
    }
}