package com.example.freemarket.client;

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

        // 武器: 剣・弓・クロスボウ・トライデント
        if (item instanceof SwordItem
                || item instanceof BowItem
                || item instanceof CrossbowItem
                || item instanceof TridentItem) return "武器";

        // 防具: 鎧・盾
        if (item instanceof ArmorItem || item instanceof ShieldItem) return "防具";

        // 道具: ツルハシ・シャベル・斧・クワ（DiggerItem 全般）
        if (item instanceof DiggerItem) return "道具";

        // 食料
        if (stack.has(DataComponents.FOOD)) return "食料";

        // ブロック
        if (item instanceof BlockItem) return "ブロック";

        return "その他";
    }
}