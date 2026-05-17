package com.example.freemarket;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(FreeMarketMod.MOD_ID);

    /**
     * 日本円コイン: ゲーム内通貨
     * スタック64枚。実際の残高はSavedDataで管理し、
     * このアイテムは「両替窓口」や「可視化」用途。
     * 基本的に残高はプレイヤーデータに整数で保持。
     */
    public static final DeferredItem<Item> YEN_COIN =
        ITEMS.register("yen_coin", () -> new Item(
            new Item.Properties().stacksTo(64)
        ));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
