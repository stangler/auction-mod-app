package com.example.freemarket.client;

import com.example.freemarket.network.payload.BuyPayload;
import com.example.freemarket.network.payload.SellPayload;
import com.example.freemarket.network.payload.SyncListingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FleaMarketScreen extends Screen {

    private List<SyncListingsPayload.ListingDto> listings = new ArrayList<>();
    private long balance = 0;

    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_HEIGHT = 20;

    // ---- レイアウト定数（変更時はここだけ直す） ----
    private static final int PANEL_Y   = 30;  // タイトル/残高行
    private static final int TAB_Y     = 43;  // カテゴリタブ上端
    private static final int TAB_H     = 13;  // タブ高さ
    private static final int HEADER_Y  = 58;  // ヘッダー行上端
    private static final int LIST_Y    = 74;  // 一覧行上端 (HEADER_Y + 16)

    // カテゴリフィルタ状態
    private String selectedCategory = ItemCategory.ALL;

    private EditBox priceBox;
    private Button sellButton;
    private Button scrollUpBtn;
    private Button scrollDownBtn;

    public FleaMarketScreen() {
        super(Component.literal("フリーマーケット"));
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;

        // 出品エリア
        priceBox = new EditBox(this.font,
            panelX + panelW - 160, h - 50, 120, 18,
            Component.literal("価格 (¥)"));
        priceBox.setMaxLength(10);
        priceBox.setHint(Component.literal("価格を入力"));
        priceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(priceBox);

        sellButton = Button.builder(
            Component.literal("出品する"),
            btn -> doSell()
        ).bounds(panelX, h - 52, 100, 20).build();
        this.addRenderableWidget(sellButton);

        // スクロールボタン（一覧の右端・LIST_Y 基準）
        int scrollBtnX = panelX + panelW - 20;
        scrollUpBtn = Button.builder(Component.literal("▲"),
            btn -> scrollOffset = Math.max(0, scrollOffset - 1))
            .bounds(scrollBtnX, LIST_Y, 18, 18).build();
        this.addRenderableWidget(scrollUpBtn);

        scrollDownBtn = Button.builder(Component.literal("▼"),
            btn -> {
                int max = Math.max(0, getFilteredListings().size() - ROWS_VISIBLE);
                scrollOffset = Math.min(max, scrollOffset + 1);
            })
            .bounds(scrollBtnX, LIST_Y + 20, 18, 18).build();
        this.addRenderableWidget(scrollDownBtn);
    }

    // ---- フィルタリング ----

    private List<SyncListingsPayload.ListingDto> getFilteredListings() {
        if (ItemCategory.ALL.equals(selectedCategory)) {
            return listings;
        }
        List<SyncListingsPayload.ListingDto> result = new ArrayList<>();
        for (var dto : listings) {
            ItemStack stack = makeIconStack(dto.itemId(), dto.itemCount());
            if (selectedCategory.equals(ItemCategory.get(stack))) {
                result.add(dto);
            }
        }
        return result;
    }

    // ---- 描画 ----

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, this.width, this.height, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        this.renderBackground(gfx, mouseX, mouseY, delta);

        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;
        int tableW = panelW - 22;

        // ── タイトル・残高 ──
        gfx.drawCenteredString(this.font, "フリーマーケット", w / 2, PANEL_Y, 0xFFFFAA);
        gfx.drawString(this.font,
            "残高: ¥" + String.format("%,d", balance), panelX, PANEL_Y, 0x00FF88);

        // ── カテゴリタブ ──
        renderTabs(gfx, panelX, tableW, mouseX, mouseY);

        // ── ヘッダー ──
        gfx.fill(panelX, HEADER_Y, panelX + tableW, HEADER_Y + 14, 0xFF555555);
        gfx.drawString(this.font, "出品者",   panelX + 4,   HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "アイテム", panelX + 130, HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "数量",     panelX + 280, HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "価格",     panelX + 330, HEADER_Y + 3, 0xFFFFFF);

        // ── 一覧 ──
        List<SyncListingsPayload.ListingDto> filtered = getFilteredListings();
        int end = Math.min(scrollOffset + ROWS_VISIBLE, filtered.size());
        for (int i = scrollOffset; i < end; i++) {
            var dto = filtered.get(i);
            int rowY = LIST_Y + (i - scrollOffset) * ROW_HEIGHT;
            int rowBg = (i % 2 == 0) ? 0xFF2A2A2A : 0xFF333333;
            gfx.fill(panelX, rowY, panelX + tableW, rowY + ROW_HEIGHT - 2, rowBg);

            gfx.drawString(this.font, truncate(dto.sellerName(), 13), panelX + 4,   rowY + 6, 0xCCCCCC);

            ItemStack icon = makeIconStack(dto.itemId(), dto.itemCount());
            gfx.renderItem(icon, panelX + 110, rowY + 2);

            gfx.drawString(this.font, truncate(dto.itemName(), 13), panelX + 130, rowY + 6, 0xFFFFFF);
            gfx.drawString(this.font, "x" + dto.itemCount(),        panelX + 280, rowY + 6, 0xAAAAAA);
            gfx.drawString(this.font,
                "¥" + String.format("%,d", dto.price()),            panelX + 330, rowY + 6, 0xFFDD44);

            // 購入ボタン
            int btnX = panelX + tableW - 45;
            boolean hovered = mouseX >= btnX && mouseX < btnX + 42
                           && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
            gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                hovered ? 0xFF005500 : 0xFF003300);
            gfx.drawCenteredString(this.font, "購入",
                btnX + 21, rowY + 6, hovered ? 0x88FF88 : 0x44CC44);
        }

        // ── 件数表示（ヘッダー右端） ──
        String countText = ItemCategory.ALL.equals(selectedCategory)
            ? filtered.size() + " 件"
            : filtered.size() + " / " + listings.size() + " 件";
        gfx.drawString(this.font, countText, panelX + tableW - 60, HEADER_Y + 3, 0x888888);

        // ── 出品エリア ──
        gfx.fill(panelX, h - 58, panelX + panelW, h - 28, 0xFF222244);
        gfx.drawString(this.font, "手に持ったアイテムを出品:", panelX + 4, h - 54, 0xAAAAFF);

        super.render(gfx, mouseX, mouseY, delta);
    }

    /** カテゴリタブを手動描画（rebuildWidgets なしで選択状態をハイライト） */
    private void renderTabs(GuiGraphics gfx, int panelX, int tableW, int mouseX, int mouseY) {
        String[] cats = ItemCategory.VALUES;
        int totalW = tableW;
        int tabW = totalW / cats.length;

        for (int i = 0; i < cats.length; i++) {
            int tabX = panelX + i * tabW;
            // 最後のタブは余白を吸収
            int thisW = (i == cats.length - 1) ? totalW - tabW * (cats.length - 1) : tabW;

            boolean active  = cats[i].equals(selectedCategory);
            boolean hovered = mouseX >= tabX && mouseX < tabX + thisW
                           && mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H;

            int bg = active  ? 0xFF446688
                   : hovered ? 0xFF334455
                   :           0xFF222233;
            int fg = active  ? 0xFFFFDD44
                   : hovered ? 0xFFCCCCCC
                   :           0xFF888888;

            gfx.fill(tabX,     TAB_Y,          tabX + thisW - 1, TAB_Y + TAB_H, bg);
            // アクティブタブは下端に黄色ライン
            if (active) {
                gfx.fill(tabX, TAB_Y + TAB_H - 2, tabX + thisW - 1, TAB_Y + TAB_H, 0xFFFFDD44);
            }
            gfx.drawCenteredString(this.font, cats[i], tabX + thisW / 2, TAB_Y + 2, fg);
        }
    }

    // ---- 入力処理 ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int w = this.width;
            int panelW = Math.min(520, w - 40);
            int panelX = (w - panelW) / 2;
            int tableW = panelW - 22;

            // タブクリック判定
            String[] cats = ItemCategory.VALUES;
            int tabW = tableW / cats.length;
            if (mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H) {
                for (int i = 0; i < cats.length; i++) {
                    int tabX = panelX + i * tabW;
                    int thisW = (i == cats.length - 1) ? tableW - tabW * (cats.length - 1) : tabW;
                    if (mouseX >= tabX && mouseX < tabX + thisW) {
                        selectedCategory = cats[i];
                        scrollOffset = 0;
                        return true;
                    }
                }
            }

            // 購入ボタンクリック判定
            int btnX = panelX + tableW - 45;
            List<SyncListingsPayload.ListingDto> filtered = getFilteredListings();
            int end = Math.min(scrollOffset + ROWS_VISIBLE, filtered.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowY = LIST_Y + (i - scrollOffset) * ROW_HEIGHT;
                if (mouseX >= btnX && mouseX < btnX + 42
                 && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                    doBuy(filtered.get(i).listingId());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = Math.max(0, getFilteredListings().size() - ROWS_VISIBLE);
        if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else             scrollOffset = Math.min(max, scrollOffset + 1);
        return true;
    }

    // ---- ネットワーク ----

    private void doBuy(UUID listingId) {
        PacketDistributor.sendToServer(new BuyPayload(listingId));
    }

    private void doSell() {
        String txt = priceBox.getValue().trim();
        if (txt.isEmpty()) return;
        try {
            long price = Long.parseLong(txt);
            if (price <= 0) return;
            PacketDistributor.sendToServer(new SellPayload(price));
            priceBox.setValue("");
        } catch (NumberFormatException ignored) {}
    }

    // ---- データ更新（サーバーからの同期時に呼ばれる） ----

    public void updateListings(List<SyncListingsPayload.ListingDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance = newBalance;
        // フィルタ後のサイズでスクロール上限を再計算
        int max = Math.max(0, getFilteredListings().size() - ROWS_VISIBLE);
        this.scrollOffset = Math.min(scrollOffset, max);
    }

    // ---- ユーティリティ ----

    private ItemStack makeIconStack(String itemId, int count) {
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return new ItemStack(item, count);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
