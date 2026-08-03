package com.example.auction.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;

/**
 * アイテムカテゴリ 動的判定。保存データ変更なし。
 */
public class ItemCategory {

    public static final String ALL = "全て";
    public static final String[] VALUES = {"全て", "武器", "防具", "道具", "食料", "ブロック", "その他"};

    public static String get(ItemStack stack) {
        if (stack.isEmpty()) return "その他";
        Item item = stack.getItem();

        // 武器: 弓・クロスボウ・トライデント
        if (item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem) return "武器";

        // 武器: 剣（SWORD タグ or MAX_DAMAGE 付きで攻撃系）
        if (stack.has(DataComponents.DAMAGE) || stack.has(DataComponents.MAX_DAMAGE)) {
            // 剣の判定はアイテムIDで判断
            String id = item.toString();
            if (id.contains("sword")) return "武器";
        }

        // 防具: 盾・装備スロットがあるもの
        if (item instanceof ShieldItem) return "防具";
        if (stack.has(DataComponents.EQUIPPABLE)) {
            var eq = stack.get(DataComponents.EQUIPPABLE);
            if (eq != null && eq.slot() != null) {
                var slot = eq.slot();
                if (slot != null && (slot.name().contains("HEAD") || slot.name().contains("CHEST")
                        || slot.name().contains("LEGS") || slot.name().contains("FEET"))) {
                    return "防具";
                }
            }
        }

        // 道具: ツルハシ・シャベル・斧・クワ（DAMAGE 付きで上記以外）
        if (item instanceof HoeItem) return "道具";
        String itemName = item.toString().toLowerCase();
        if (itemName.contains("pickaxe") || itemName.contains("shovel")
                || itemName.contains("axe") || itemName.contains("hoe")) return "道具";

        // 食料
        if (stack.has(DataComponents.FOOD)) return "食料";

        // ブロック
        if (item instanceof BlockItem) return "ブロック";

        return "その他";
    }
}