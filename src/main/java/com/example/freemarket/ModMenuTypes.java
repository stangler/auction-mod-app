package com.example.freemarket;

import com.example.freemarket.market.FleaMarketMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, FreeMarketMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FleaMarketMenu>> FLEA_MARKET =
        MENUS.register("flea_market",
            () -> IMenuTypeExtension.create(FleaMarketMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
