package com.example.auction.client;

import com.example.auction.network.payload.BuyPayload;
import com.example.auction.network.payload.CancelListingPayload;
import com.example.auction.network.payload.SellPayload;
import com.example.auction.network.payload.SyncListingsPayload;
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

    // ── 上位タブ ──────────────────────────────────────────────
    private enum MainTab { BROWSE, SELL }
    private MainTab mainTab = MainTab.BROWSE;

    private List<SyncListingsPayload.ListingDto> listings = new ArrayList<>();
    private long balance = 0;

    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_HEIGHT = 20;

    // ---- レイアウト定数 ----
    private static final int PANEL_Y   = 10;  // タイトル/残高行
    private static final int MAIN_TAB_Y = 22; // 上位タブ（出品一覧/出品する）上端
    private static final int MAIN_TAB_H = 14; // 上位タブ高さ
    private static final int TAB_Y     = 38;  // カテゴリタブ上端（出品一覧タブ内のみ）
    private static final int TAB_H     = 13;  // カテゴリタブ高さ
    private static final int HEADER_Y  = 53;  // ヘッダー行上端
    private static final int LIST_Y    = 69;  // 一覧行上端

    // カテゴリフィルタ状態
    private String selectedCategory = ItemCategory.ALL;

    // ステータスラベル
    private String statusMessage = "";
    private int    statusColor   = 0xFFFFFF;
    private int    statusTimer   = 0;

    // 出品一覧タブ用ウィジェット
    private Button scrollUpBtn;
    private Button scrollDownBtn;

    // 出品するタブ用ウィジェット
    private EditBox priceBox;
    private Button sellButton;

    public FleaMarketScreen() {
        super(Component.literal("フリーマーケット"));
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();

        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;

        if (mainTab == MainTab.BROWSE) {
            // スクロールボタン
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

        } else {
            // 出品するタブ: 価格入力 + 出品ボタン
            int sellPanelY = MAIN_TAB_Y + MAIN_TAB_H + 60;

            priceBox = new EditBox(this.font,
                panelX + 4, sellPanelY + 20, 160, 18,
                Component.literal("価格 (¥)"));
            priceBox.setMaxLength(10);
            priceBox.setHint(Component.literal("価格を入力 (¥)"));
            priceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            this.addRenderableWidget(priceBox);

            sellButton = Button.builder(
                Component.literal("出品する"),
                btn -> doSell()
            ).bounds(panelX + 170, sellPanelY + 19, 80, 20).build();
            this.addRenderableWidget(sellButton);
        }
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

    // ---- ローカルプレイヤー名取得 ----

    private String getLocalPlayerName() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getName().getString();
        }
        return "";
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

        // ── 上位タブ（出品一覧 / 出品する） ──
        renderMainTabs(gfx, panelX, tableW, mouseX, mouseY);

        if (mainTab == MainTab.BROWSE) {
            renderBrowseTab(gfx, panelX, tableW, mouseX, mouseY);
        } else {
            renderSellTab(gfx, panelX, panelW, tableW, mouseX, mouseY, h);
        }

        // ── ステータスラベル ──
        if (statusTimer > 0) {
            statusTimer--;
            int alpha = Math.min(255, statusTimer * 8);
            int col   = (statusColor & 0x00FFFFFF) | (alpha << 24);
            gfx.drawCenteredString(this.font, statusMessage, w / 2, h - 10, col);
        }

        super.render(gfx, mouseX, mouseY, delta);
    }

    /** 上位タブ（出品一覧 / 出品する）描画 */
    private void renderMainTabs(GuiGraphics gfx, int panelX, int tableW, int mouseX, int mouseY) {
        int tabW = tableW / 2;
        String[] labels = { "出品一覧", "出品する" };
        MainTab[] tabs  = { MainTab.BROWSE, MainTab.SELL };

        for (int i = 0; i < 2; i++) {
            int tabX   = panelX + i * tabW;
            int thisW  = (i == 1) ? tableW - tabW : tabW;
            boolean active  = mainTab == tabs[i];
            boolean hovered = mouseX >= tabX && mouseX < tabX + thisW
                           && mouseY >= MAIN_TAB_Y && mouseY < MAIN_TAB_Y + MAIN_TAB_H;

            int bg = active  ? 0xFF3A5A3A
                   : hovered ? 0xFF2A3A2A
                   :           0xFF1A271A;
            int fg = active  ? 0xFF88FF88
                   : hovered ? 0xFFAACC88
                   :           0xFF557755;

            gfx.fill(tabX, MAIN_TAB_Y, tabX + thisW - 1, MAIN_TAB_Y + MAIN_TAB_H, bg);
            if (active) {
                gfx.fill(tabX, MAIN_TAB_Y + MAIN_TAB_H - 2,
                         tabX + thisW - 1, MAIN_TAB_Y + MAIN_TAB_H, 0xFF88FF88);
            }
            gfx.drawCenteredString(this.font, labels[i], tabX + thisW / 2, MAIN_TAB_Y + 3, fg);
        }
    }

    /** 出品一覧タブ描画 */
    private void renderBrowseTab(GuiGraphics gfx, int panelX, int tableW, int mouseX, int mouseY) {
        String localName = getLocalPlayerName();

        // カテゴリタブ
        renderCategoryTabs(gfx, panelX, tableW, mouseX, mouseY);

        // ヘッダー
        gfx.fill(panelX, HEADER_Y, panelX + tableW, HEADER_Y + 14, 0xFF555555);
        gfx.drawString(this.font, "出品者",   panelX + 4,   HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "アイテム", panelX + 130, HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "数量",     panelX + 280, HEADER_Y + 3, 0xFFFFFF);
        gfx.drawString(this.font, "価格",     panelX + 330, HEADER_Y + 3, 0xFFFFFF);

        // 件数
        List<SyncListingsPayload.ListingDto> filtered = getFilteredListings();
        String countText = ItemCategory.ALL.equals(selectedCategory)
            ? filtered.size() + " 件"
            : filtered.size() + " / " + listings.size() + " 件";
        gfx.drawString(this.font, countText, panelX + tableW - 60, HEADER_Y + 3, 0x888888);

        // 一覧
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

            int btnX = panelX + tableW - 45;
            boolean isOwn = dto.sellerName().equals(localName);

            if (isOwn) {
                boolean hovered = mouseX >= btnX && mouseX < btnX + 42
                               && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                    hovered ? 0xFF550000 : 0xFF330000);
                gfx.drawCenteredString(this.font, "取消",
                    btnX + 21, rowY + 6, hovered ? 0xFF8888 : 0xCC4444);
            } else {
                boolean hovered = mouseX >= btnX && mouseX < btnX + 42
                               && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                    hovered ? 0xFF005500 : 0xFF003300);
                gfx.drawCenteredString(this.font, "購入",
                    btnX + 21, rowY + 6, hovered ? 0x88FF88 : 0x44CC44);
            }
        }
    }

    /** 出品するタブ描画 */
    private void renderSellTab(GuiGraphics gfx, int panelX, int panelW, int tableW,
                                int mouseX, int mouseY, int h) {
        String localName = getLocalPlayerName();
        int baseY = MAIN_TAB_Y + MAIN_TAB_H + 8;

        // ── 手持ちアイテム表示エリア ──
        gfx.fill(panelX, baseY, panelX + panelW, baseY + 50, 0xFF1A1A2E);
        gfx.drawString(this.font, "出品アイテム（手に持ったもの）", panelX + 4, baseY + 4, 0xAAAAFF);

        ItemStack held = ItemStack.EMPTY;
        if (this.minecraft != null && this.minecraft.player != null) {
            held = this.minecraft.player.getMainHandItem();
        }

        if (held.isEmpty()) {
            gfx.drawString(this.font, "アイテムを手に持ってください", panelX + 24, baseY + 20, 0xFF6666);
        } else {
            gfx.renderItem(held, panelX + 4, baseY + 20);
            String itemName = held.getHoverName().getString();
            gfx.drawString(this.font, itemName,         panelX + 24, baseY + 22, 0xFFFFFF);
            gfx.drawString(this.font, "x" + held.getCount(), panelX + 24, baseY + 33, 0xAAAAAA);
        }

        // ── 価格入力エリア ──
        int sellPanelY = baseY + 58;
        gfx.fill(panelX, sellPanelY, panelX + panelW, sellPanelY + 50, 0xFF1A1A2E);
        gfx.drawString(this.font, "価格設定", panelX + 4, sellPanelY + 4, 0xAAAAFF);

        // 手数料プレビュー
        if (priceBox != null) {
            String feeStr = feePreviewText(priceBox.getValue());
            if (!feeStr.isEmpty()) {
                gfx.drawString(this.font, feeStr, panelX + 260, sellPanelY + 23, 0xFFAA44);
            }
        }

        // ── 自分の出品中リスト ──
        int myListY = sellPanelY + 58;
        gfx.fill(panelX, myListY, panelX + panelW, myListY + 14, 0xFF444444);
        gfx.drawString(this.font, "出品中のアイテム（最大3件）", panelX + 4, myListY + 3, 0xCCCCCC);

        List<SyncListingsPayload.ListingDto> myListings = new ArrayList<>();
        for (var dto : listings) {
            if (dto.sellerName().equals(localName)) myListings.add(dto);
        }

        if (myListings.isEmpty()) {
            gfx.drawString(this.font, "出品中なし", panelX + 4, myListY + 18, 0x666666);
        } else {
            for (int i = 0; i < myListings.size() && i < 3; i++) {
                var dto = myListings.get(i);
                int rowY = myListY + 14 + i * ROW_HEIGHT;
                int rowBg = (i % 2 == 0) ? 0xFF2A2A2A : 0xFF333333;
                gfx.fill(panelX, rowY, panelX + tableW, rowY + ROW_HEIGHT - 2, rowBg);

                ItemStack icon = makeIconStack(dto.itemId(), dto.itemCount());
                gfx.renderItem(icon, panelX + 4, rowY + 2);
                gfx.drawString(this.font, truncate(dto.itemName(), 15), panelX + 24, rowY + 6, 0xFFFFFF);
                gfx.drawString(this.font, "x" + dto.itemCount(), panelX + 200, rowY + 6, 0xAAAAAA);
                gfx.drawString(this.font,
                    "¥" + String.format("%,d", dto.price()), panelX + 240, rowY + 6, 0xFFDD44);

                // 取消ボタン
                int btnX = panelX + tableW - 45;
                boolean hovered = mouseX >= btnX && mouseX < btnX + 42
                               && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2;
                gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                    hovered ? 0xFF550000 : 0xFF330000);
                gfx.drawCenteredString(this.font, "取消",
                    btnX + 21, rowY + 6, hovered ? 0xFF8888 : 0xCC4444);
            }
        }
    }

    /** カテゴリタブ描画（出品一覧タブ内のみ） */
    private void renderCategoryTabs(GuiGraphics gfx, int panelX, int tableW, int mouseX, int mouseY) {
        String[] cats = ItemCategory.VALUES;
        int totalW = tableW;
        int tabW = totalW / cats.length;

        for (int i = 0; i < cats.length; i++) {
            int tabX = panelX + i * tabW;
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

            gfx.fill(tabX, TAB_Y, tabX + thisW - 1, TAB_Y + TAB_H, bg);
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

            // ── 上位タブクリック ──
            if (mouseY >= MAIN_TAB_Y && mouseY < MAIN_TAB_Y + MAIN_TAB_H) {
                int tabW = tableW / 2;
                if (mouseX >= panelX && mouseX < panelX + tabW) {
                    if (mainTab != MainTab.BROWSE) {
                        mainTab = MainTab.BROWSE;
                        rebuildWidgets();
                    }
                    return true;
                } else if (mouseX >= panelX + tabW && mouseX < panelX + tableW) {
                    if (mainTab != MainTab.SELL) {
                        mainTab = MainTab.SELL;
                        rebuildWidgets();
                    }
                    return true;
                }
            }

            if (mainTab == MainTab.BROWSE) {
                // カテゴリタブクリック
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

                // 購入/取消クリック（出品一覧）
                String localName = getLocalPlayerName();
                int btnX = panelX + tableW - 45;
                List<SyncListingsPayload.ListingDto> filtered = getFilteredListings();
                int end = Math.min(scrollOffset + ROWS_VISIBLE, filtered.size());
                for (int i = scrollOffset; i < end; i++) {
                    int rowY = LIST_Y + (i - scrollOffset) * ROW_HEIGHT;
                    if (mouseX >= btnX && mouseX < btnX + 42
                     && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                        var dto = filtered.get(i);
                        if (dto.sellerName().equals(localName)) {
                            doCancel(dto.listingId());
                        } else {
                            doBuy(dto.listingId());
                        }
                        return true;
                    }
                }

            } else {
                // 出品するタブ: 自分の出品取消クリック
                String localName = getLocalPlayerName();
                int baseY = MAIN_TAB_Y + MAIN_TAB_H + 8;
                int sellPanelY = baseY + 58;
                int myListY = sellPanelY + 58;

                List<SyncListingsPayload.ListingDto> myListings = new ArrayList<>();
                for (var dto : listings) {
                    if (dto.sellerName().equals(localName)) myListings.add(dto);
                }

                int btnX = panelX + tableW - 45;
                for (int i = 0; i < myListings.size() && i < 3; i++) {
                    int rowY = myListY + 14 + i * ROW_HEIGHT;
                    if (mouseX >= btnX && mouseX < btnX + 42
                     && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT - 2) {
                        doCancel(myListings.get(i).listingId());
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mainTab == MainTab.BROWSE) {
            int max = Math.max(0, getFilteredListings().size() - ROWS_VISIBLE);
            if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else             scrollOffset = Math.min(max, scrollOffset + 1);
            return true;
        }
        return false;
    }

    // ---- ネットワーク ----

    private void doBuy(UUID listingId) {
        PacketDistributor.sendToServer(new BuyPayload(listingId));
    }

    private void doCancel(UUID listingId) {
        PacketDistributor.sendToServer(new CancelListingPayload(listingId));
    }

    private void doSell() {
        if (priceBox == null) return;
        String txt = priceBox.getValue().trim();
        if (txt.isEmpty()) return;
        try {
            long price = Long.parseLong(txt);
            if (price <= 0) return;
            PacketDistributor.sendToServer(new SellPayload(price));
            priceBox.setValue("");
        } catch (NumberFormatException ignored) {}
    }

    // ---- データ更新 ----

    public void updateListings(List<SyncListingsPayload.ListingDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance = newBalance;
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

    public void showStatus(String msg, int color) {
        statusMessage = msg;
        statusColor   = color;
        statusTimer   = 80;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String feePreviewText(String input) {
        if (input.isEmpty()) return "";
        try {
            long price = Long.parseLong(input);
            if (price <= 0) return "";
            long fee = Math.max(1L, Math.round(price * 0.05));
            return "手数料: 約¥" + String.format("%,d", fee);
        } catch (NumberFormatException e) {
            return "";
        }
    }
}