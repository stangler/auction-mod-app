package com.example.freemarket.client;

import com.example.freemarket.network.payload.BidPayload;
import com.example.freemarket.network.payload.SyncAuctionPayload;
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
 * オークション GUI（Yahoo!オークション風）
 *
 * ┌──────────────────────────────────────────────┐
 * │  🔨 オークション            残高: ¥XX,XXX      │
 * ├────────┬────────┬──────────┬────────┬────────┤
 * │ 出品者  │ アイテム │ 現在入札額 │ 残り時間 │        │
 * │ ...    │ ...    │ ¥X,XXX   │ 2:30   │ [入札] │
 * ├──────────────────────────────────────────────┤
 * │ 選択中: Diamond Sword  最低入札額: ¥1,001      │
 * │ 入札額: [__________]  [入札する]               │
 * └──────────────────────────────────────────────┘
 */
public class AuctionScreen extends Screen {

    // 表示中の一覧
    private List<SyncAuctionPayload.AuctionDto> listings = new ArrayList<>();
    private long balance = 0;

    // 選択中の出品（入札対象）
    private int selectedRow = -1;

    // スクロール
    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 7;
    private static final int ROW_HEIGHT = 20;

    // 入札額入力ボックス
    private EditBox bidBox;

    // ボタン参照
    private Button bidButton;
    private Button scrollUpBtn;
    private Button scrollDownBtn;

    // メッセージ（フィードバック表示）
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;
    private int statusTimer = 0;

    public AuctionScreen() {
        super(Component.literal("🔨 オークション"));
    }

    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;

        // 入札額入力ボックス
        bidBox = new EditBox(this.font,
            panelX + 90, h - 52, 130, 18,
            Component.literal("入札額 (¥)"));
        bidBox.setMaxLength(12);
        bidBox.setHint(Component.literal("金額を入力"));
        bidBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(bidBox);

        // 入札ボタン
        bidButton = Button.builder(
            Component.literal("入札する"),
            btn -> doPlaceBid()
        ).bounds(panelX + 228, h - 54, 80, 20).build();
        this.addRenderableWidget(bidButton);

        // スクロールボタン
        scrollUpBtn = Button.builder(Component.literal("▲"),
            btn -> scrollOffset = Math.max(0, scrollOffset - 1))
            .bounds(panelX + panelW - 20, getListY() - 2, 18, 18).build();
        this.addRenderableWidget(scrollUpBtn);

        scrollDownBtn = Button.builder(Component.literal("▼"),
            btn -> scrollOffset = Math.min(
                Math.max(0, listings.size() - ROWS_VISIBLE), scrollOffset + 1))
            .bounds(panelX + panelW - 20, getListY() + 18, 18, 18).build();
        this.addRenderableWidget(scrollDownBtn);
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        // ぼかし（被写界深度）を無効化し、半透明黒背景に置き換え
        gfx.fill(0, 0, this.width, this.height, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        this.renderBackground(gfx, mouseX, mouseY, delta);

        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;
        int panelY = 20;
        int listY = getListY();

        // ── タイトル・残高 ──────────────────────────────
        gfx.drawCenteredString(this.font,
            "🔨 オークション",
            w / 2, panelY, 0xFFDD44);
        gfx.drawString(this.font,
            "残高: ¥" + String.format("%,d", balance),
            panelX, panelY, 0x00FF88);

        // ── ヘッダー行 ──────────────────────────────────
        int headerY = panelY + 14;
        int tableW = panelW - 22;
        gfx.fill(panelX, headerY, panelX + tableW, headerY + 15, 0xFF444466);
        gfx.drawString(this.font, "出品者",    col(panelX, 0),  headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "アイテム",   col(panelX, 1),  headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "現在入札額", col(panelX, 2),  headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "残り時間",   col(panelX, 3),  headerY + 4, 0xCCCCFF);

        // ── 出品一覧 ────────────────────────────────────
        int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
        for (int i = scrollOffset; i < end; i++) {
            var dto = listings.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            boolean isSelected = (i == selectedRow);

            // 行背景
            int rowBg = isSelected ? 0xFF223355
                : (i % 2 == 0 ? 0xFF1E1E2E : 0xFF252535);
            gfx.fill(panelX, rowY, panelX + tableW, rowY + ROW_HEIGHT - 1, rowBg);
            if (isSelected) {
                // 選択枠
                gfx.fill(panelX, rowY, panelX + tableW, rowY + 1, 0xFF5577AA);
                gfx.fill(panelX, rowY + ROW_HEIGHT - 2, panelX + tableW, rowY + ROW_HEIGHT - 1, 0xFF5577AA);
            }

            // テキスト
            gfx.drawString(this.font, truncate(dto.sellerName(), 10), col(panelX, 0), rowY + 6, 0xCCCCCC);
            gfx.drawString(this.font, truncate(dto.itemName(), 14),   col(panelX, 1), rowY + 6, 0xFFFFFF);

            // 入札額（入札なし時はスタート価格を薄く表示）
            if (dto.currentBid() > 0) {
                gfx.drawString(this.font,
                    "¥" + String.format("%,d", dto.currentBid()),
                    col(panelX, 2), rowY + 6, 0xFFDD44);
            } else {
                gfx.drawString(this.font,
                    "¥" + String.format("%,d", dto.startPrice()) + " ~",
                    col(panelX, 2), rowY + 6, 0x888855);
            }

            // 残り時間（色分け）
            long secs = dto.remainingSecs();
            String timeStr = formatTime(secs);
            int timeColor = secs <= 60 ? 0xFF4444       // 1分以内 → 赤
                : secs <= 300 ? 0xFF8833               // 5分以内 → オレンジ
                : 0x88CCFF;                             // 通常 → 水色
            gfx.drawString(this.font, timeStr, col(panelX, 3), rowY + 6, timeColor);

            // 入札ボタン（ホバー or 選択でハイライト）
            int btnX = panelX + tableW - 44;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 42
                           && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1;
            gfx.fill(btnX, rowY + 2, btnX + 42, rowY + ROW_HEIGHT - 3,
                hovered ? 0xFF004488 : 0xFF002244);
            gfx.drawCenteredString(this.font, "入札",
                btnX + 21, rowY + 6,
                hovered ? 0x88DDFF : 0x4499CC);
        }

        // 一覧なし表示
        if (listings.isEmpty()) {
            gfx.drawCenteredString(this.font,
                "出品中のアイテムはありません",
                w / 2, listY + 30, 0x888888);
        }

        // ── 入札エリア ──────────────────────────────────
        int areaY = h - 62;
        gfx.fill(panelX, areaY, panelX + panelW, h - 26, 0xFF1A1A33);
        gfx.fill(panelX, areaY, panelX + panelW, areaY + 1, 0xFF445588);

        if (selectedRow >= 0 && selectedRow < listings.size()) {
            var sel = listings.get(selectedRow);
            // 選択中アイテム情報
            gfx.drawString(this.font,
                "選択: " + truncate(sel.itemName(), 20)
                + "  最低入札額: ¥" + String.format("%,d", sel.minimumBid()),
                panelX + 4, areaY + 5, 0xAAAAFF);
            // 最高入札者
            if (!sel.topBidderName().isEmpty()) {
                gfx.drawString(this.font,
                    "現在の最高額入札者: " + sel.topBidderName(),
                    panelX + 4, areaY + 16, 0xFF8844);
            }
        } else {
            gfx.drawString(this.font,
                "← 入札する出品を選択してください",
                panelX + 4, areaY + 10, 0x666688);
        }

        // 入札額ラベル
        gfx.drawString(this.font, "入札額 ¥:", panelX + 4, h - 46, 0xCCCCCC);

        // ── ステータスメッセージ ─────────────────────────
        if (statusTimer > 0) {
            statusTimer--;
            int alpha = Math.min(255, statusTimer * 8);
            int col = (statusColor & 0x00FFFFFF) | (alpha << 24);
            gfx.drawCenteredString(this.font, statusMessage, w / 2, h - 18, col);
        }

        super.render(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int w = this.width;
            int panelW = Math.min(520, w - 40);
            int panelX = (w - panelW) / 2;
            int tableW = panelW - 22;
            int listY = getListY();
            int btnX = panelX + tableW - 44;

            int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
            for (int i = scrollOffset; i < end; i++) {
                int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;

                // 入札ボタンクリック
                if (mouseX >= btnX && mouseX <= btnX + 42
                 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1) {
                    selectedRow = i;
                    // 最低入札額を自動セット
                    bidBox.setValue(String.valueOf(listings.get(i).minimumBid()));
                    return true;
                }

                // 行クリック → 選択
                if (mouseX >= panelX && mouseX <= panelX + tableW
                 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1) {
                    selectedRow = i;
                    bidBox.setValue(String.valueOf(listings.get(i).minimumBid()));
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

    // ===================================================
    // アクション
    // ===================================================

    private void doPlaceBid() {
        if (selectedRow < 0 || selectedRow >= listings.size()) {
            showStatus("入札する出品を選択してください", 0xFF8888);
            return;
        }
        String txt = bidBox.getValue().trim();
        if (txt.isEmpty()) {
            showStatus("入札額を入力してください", 0xFF8888);
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(txt);
        } catch (NumberFormatException e) {
            showStatus("正しい金額を入力してください", 0xFF8888);
            return;
        }

        var dto = listings.get(selectedRow);
        if (amount < dto.minimumBid()) {
            showStatus("最低入札額は ¥" + String.format("%,d", dto.minimumBid()) + " です", 0xFF8844);
            return;
        }
        if (amount > balance) {
            showStatus("残高不足です (残高: ¥" + String.format("%,d", balance) + ")", 0xFF4444);
            return;
        }

        PacketDistributor.sendToServer(new BidPayload(dto.listingId(), amount));
        showStatus("入札しました: ¥" + String.format("%,d", amount), 0x44FF88);
        bidBox.setValue("");
    }

    // ===================================================
    // データ更新（サーバーから受信）
    // ===================================================

    public void updateListings(List<SyncAuctionPayload.AuctionDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance = newBalance;
        this.scrollOffset = Math.min(scrollOffset, Math.max(0, listings.size() - ROWS_VISIBLE));
        // 選択行の再チェック（削除された場合はリセット）
        if (selectedRow >= listings.size()) selectedRow = -1;
    }

    // ===================================================
    // ユーティリティ
    // ===================================================

    /** カラム X 座標 */
    private int col(int panelX, int colIndex) {
        int[] offsets = {4, 100, 250, 370};
        return panelX + offsets[colIndex];
    }

    /** 一覧開始Y座標 */
    private int getListY() {
        return 20 + 14 + 16; // panelY + headerY + headerH
    }

    /** 残り時間フォーマット: H:MM:SS or MM:SS */
    private String formatTime(long secs) {
        if (secs <= 0) return "終了";
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        long s = secs % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    private String truncate(String str, int max) {
        return str.length() <= max ? str : str.substring(0, max - 1) + "…";
    }

    private void showStatus(String msg, int color) {
        statusMessage = msg;
        statusColor = color;
        statusTimer = 80; // 約4秒
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
