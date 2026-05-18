package com.example.freemarket.client;

import com.example.freemarket.network.payload.BuyPayload;
import com.example.freemarket.network.payload.SellPayload;
import com.example.freemarket.network.payload.SyncListingsPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * フリーマーケット GUI
 *
 * ┌─────────────────────────────────┐
 * │  🏪 フリーマーケット  残高: ¥XX,XXX │
 * ├────────────┬────────┬───────────┤
 * │  出品者     │ アイテム │  価格      │ [購入]
 * │  ...       │ ...    │  ...      │
 * ├────────────────────────────────-┤
 * │ [手持ちアイテムを出品]  価格: [___] │
 * └─────────────────────────────────┘
 */
public class FleaMarketScreen extends Screen {

    // 表示中の一覧
    private List<SyncListingsPayload.ListingDto> listings = new ArrayList<>();
    private long balance = 0;

    // スクロール
    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 8;
    private static final int ROW_HEIGHT = 18;

    // 価格入力ボックス
    private EditBox priceBox;

    // ボタン参照
    private Button sellButton;
    private Button scrollUpBtn;
    private Button scrollDownBtn;

    public FleaMarketScreen() {
        super(Component.literal("🏪 フリーマーケット"));
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int panelW = Math.min(500, w - 40);
        int panelX = (w - panelW) / 2;
        int panelY = 30;

        // 価格入力ボックス
        priceBox = new EditBox(this.font,
            panelX + panelW - 160, h - 50, 120, 18,
            Component.literal("価格 (¥)"));
        priceBox.setMaxLength(10);
        priceBox.setHint(Component.literal("価格を入力"));
        priceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(priceBox);

        // 出品ボタン
        sellButton = Button.builder(
            Component.literal("出品する"),
            btn -> doSell()
        ).bounds(panelX, h - 52, 100, 20).build();
        this.addRenderableWidget(sellButton);

        // スクロールボタン
        scrollUpBtn = Button.builder(Component.literal("▲"),
            btn -> { scrollOffset = Math.max(0, scrollOffset - 1); })
            .bounds(panelX + panelW - 20, panelY + 20, 18, 18).build();
        this.addRenderableWidget(scrollUpBtn);

        scrollDownBtn = Button.builder(Component.literal("▼"),
            btn -> { scrollOffset = Math.min(
                Math.max(0, listings.size() - ROWS_VISIBLE), scrollOffset + 1); })
            .bounds(panelX + panelW - 20, panelY + 40, 18, 18).build();
        this.addRenderableWidget(scrollDownBtn);

        // GUIを開いたらサーバーに一覧リクエスト（OpenMarketPayloadを受けた後
        // SyncListingsはサーバーが自動送信するが、念のためここでも要求可能）
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        this.renderBackground(gfx, mouseX, mouseY, delta);

        int w = this.width;
        int h = this.height;
        int panelW = Math.min(500, w - 40);
        int panelX = (w - panelW) / 2;
        int panelY = 30;

        // タイトル + 残高
        gfx.drawCenteredString(this.font,
            "🏪 フリーマーケット",
            w / 2, panelY, 0xFFFFAA);
        gfx.drawString(this.font,
            "残高: ¥" + String.format("%,d", balance),
            panelX, panelY, 0x00FF88);

        // ヘッダー行
        int headerY = panelY + 16;
        gfx.fill(panelX, headerY, panelX + panelW - 22, headerY + 14, 0xFF555555);
        gfx.drawString(this.font, "出品者",           panelX + 4,           headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "アイテム",          panelX + 110,          headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "数量",             panelX + 240,          headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "価格",             panelX + 290,          headerY + 3, 0xFFFFFF);

        // 一覧行
        int listY = headerY + 16;
        int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
        for (int i = scrollOffset; i < end; i++) {
            var dto = listings.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            int rowBg = (i % 2 == 0) ? 0xFF2A2A2A : 0xFF333333;
            gfx.fill(panelX, rowY, panelX + panelW - 22, rowY + ROW_HEIGHT - 2, rowBg);

            gfx.drawString(this.font, truncate(dto.sellerName(), 14), panelX + 4,   rowY + 5, 0xCCCCCC);
            gfx.drawString(this.font, truncate(dto.itemName(), 16),   panelX + 110,  rowY + 5, 0xFFFFFF);
            gfx.drawString(this.font, "x" + dto.itemCount(),          panelX + 240,  rowY + 5, 0xAAAAAA);
            gfx.drawString(this.font,
                "¥" + String.format("%,d", dto.price()),              panelX + 290,  rowY + 5, 0xFFDD44);

            // 購入ボタン（ホバー時ハイライト）
            int btnX = panelX + panelW - 22 - 45;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 42
                           && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                hovered ? 0xFF005500 : 0xFF003300);
            gfx.drawCenteredString(this.font, "購入",
                btnX + 21, rowY + 5, hovered ? 0x88FF88 : 0x44CC44);

            // 購入ボタン座標を記録（クリック判定用）
            // → mouseClicked でi番目のボタン領域チェック
        }

        // 出品エリア背景
        gfx.fill(panelX, h - 58, panelX + panelW, h - 28, 0xFF222244);
        gfx.drawString(this.font, "手に持ったアイテムを出品:", panelX + 4, h - 54, 0xAAAAFF);

        super.render(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 購入ボタン領域クリック判定
        if (button == 0) {
            int w = this.width;
            int panelW = Math.min(500, w - 40);
            int panelX = (w - panelW) / 2;
            int panelY = 30;
            int listY = panelY + 32;
            int btnX = panelX + panelW - 22 - 45;

            int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
                if (mouseX >= btnX && mouseX <= btnX + 42
                 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2) {
                    doBuy(listings.get(i).listingId());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
        else scrollOffset = Math.min(Math.max(0, listings.size() - ROWS_VISIBLE), scrollOffset + 1);
        return true;
    }

    // ===========================================
    // アクション
    // ===========================================

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

    // ===========================================
    // データ更新（サーバーから受信）
    // ===========================================

    public void updateListings(List<SyncListingsPayload.ListingDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance = newBalance;
        this.scrollOffset = Math.min(scrollOffset, Math.max(0, listings.size() - ROWS_VISIBLE));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
