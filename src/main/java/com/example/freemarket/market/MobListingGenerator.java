package com.example.freemarket.market;

import com.example.freemarket.data.MarketSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;
import java.util.UUID;

/**
 * モブの自動出品ロジック（AIなし・ルールベース）
 *
 * ワールドロード時 or 定期イベントでモブが出品する。
 * 村人 → 農産物・ツール
 * スティーブ/アレックス → 初期スターター出品
 */
@EventBusSubscriber(modid = "freemarket")
public class MobListingGenerator {

    // 出品テンプレート（モブ種別 → 商品リスト）
    private static final List<MobListing> VILLAGER_LISTINGS = List.of(
        new MobListing("村人A", new ItemStack(Items.BREAD, 10),      200),
        new MobListing("村人B", new ItemStack(Items.IRON_SWORD),     1500),
        new MobListing("村人C", new ItemStack(Items.GOLDEN_APPLE),   3000),
        new MobListing("村人D", new ItemStack(Items.ENCHANTING_TABLE), 5000)
    );

    private static final List<MobListing> STARTER_LISTINGS = List.of(
        new MobListing("スティーブ", new ItemStack(Items.DIAMOND, 3), 8000),
        new MobListing("アレックス", new ItemStack(Items.BOW),        1200),
        new MobListing("スティーブ", new ItemStack(Items.OAK_LOG, 64), 500),
        new MobListing("アレックス", new ItemStack(Items.COOKED_BEEF, 16), 800)
    );

    /**
     * ワールドロード時にモブ出品データを登録
     * 既に登録済みなら追加しない（重複防止）
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(ServerLevel.OVERWORLD)) return;

        MarketSavedData data = MarketSavedData.get(level);

        // 既存出品が0件のときのみ初期化
        if (data.getActiveListings().isEmpty()) {
            addListings(data, STARTER_LISTINGS);
            addListings(data, VILLAGER_LISTINGS);
            FreeMarketMod.LOGGER.info("FreeMarket: モブ初期出品 {}件登録",
                STARTER_LISTINGS.size() + VILLAGER_LISTINGS.size());
        }
    }

    private static void addListings(MarketSavedData data, List<MobListing> templates) {
        for (MobListing t : templates) {
            // モブは固定UUID（名前ベースで生成）→ 永続
            UUID mobId = UUID.nameUUIDFromBytes(t.sellerName().getBytes());
            MarketListing listing = new MarketListing(
                UUID.randomUUID(), t.sellerName(), mobId, t.item(), t.price()
            );
            data.addListing(listing);
        }
    }

    record MobListing(String sellerName, ItemStack item, long price) {}
}
