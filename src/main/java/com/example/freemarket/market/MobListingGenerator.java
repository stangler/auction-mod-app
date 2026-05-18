package com.example.freemarket.market;

import com.example.freemarket.FreeMarketMod;
import com.example.freemarket.data.MarketSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FreeMarketMod.MOD_ID)
public class MobListingGenerator {

    private static final List<MobListing> STARTER_LISTINGS = List.of(
        new MobListing("スティーブ", new ItemStack(Items.DIAMOND, 3),    8000),
        new MobListing("アレックス", new ItemStack(Items.BOW),           1200),
        new MobListing("スティーブ", new ItemStack(Items.OAK_LOG, 64),    500),
        new MobListing("アレックス", new ItemStack(Items.COOKED_BEEF, 16), 800)
    );

    private static final List<MobListing> VILLAGER_LISTINGS = List.of(
        new MobListing("村人A", new ItemStack(Items.BREAD, 10),        200),
        new MobListing("村人B", new ItemStack(Items.IRON_SWORD),      1500),
        new MobListing("村人C", new ItemStack(Items.GOLDEN_APPLE),    3000),
        new MobListing("村人D", new ItemStack(Items.ENCHANTING_TABLE), 5000)
    );

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        MarketSavedData data = MarketSavedData.get(level);

        if (data.getActiveListings().isEmpty()) {
            addListings(data, STARTER_LISTINGS);
            addListings(data, VILLAGER_LISTINGS);
            FreeMarketMod.LOGGER.info("FreeMarket: モブ初期出品 {}件登録",
                STARTER_LISTINGS.size() + VILLAGER_LISTINGS.size());
        }
    }

    private static void addListings(MarketSavedData data, List<MobListing> templates) {
        for (MobListing t : templates) {
            UUID mobId = UUID.nameUUIDFromBytes(t.sellerName().getBytes());
            data.addListing(new MarketListing(
                UUID.randomUUID(), t.sellerName(), mobId, t.item(), t.price()
            ));
        }
    }

    record MobListing(String sellerName, ItemStack item, long price) {}
}
