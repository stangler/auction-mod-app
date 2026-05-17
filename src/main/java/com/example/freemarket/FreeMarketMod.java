package com.example.freemarket;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(FreeMarketMod.MOD_ID)
public class FreeMarketMod {

    public static final String MOD_ID = "freemarket";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FreeMarketMod(IEventBus modEventBus) {
        // 各レジストリ登録
        ModItems.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("FreeMarket: common setup");
    }

    private void clientSetup(FMLClientSetupEvent event) {
        LOGGER.info("FreeMarket: client setup");
        // スクリーン登録はModMenuTypes側で実施
    }
}
