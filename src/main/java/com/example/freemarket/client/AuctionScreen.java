package com.example.freemarket.client;

import com.example.freemarket.network.payload.BidPayload;
import com.example.freemarket.network.payload.CancelAuctionPayload;
import com.example.freemarket.network.payload.SellAuctionPayload;
import com.example.freemarket.network.payload.SyncAuctionPayload;
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

public class AuctionScreen extends Screen {

    private List<SyncAuctionPayload.AuctionDto> listings = new ArrayList<>();
    private long balance = 0;

    // ── タブ ────────────────────────────────────────────
    private static final int TAB_LIST = 0;
    private static final int TAB_SELL = 1;
    private int currentTab = TAB_LIST;

    // ── 一覧タブ ─────────────────────────────────────────
    private int selectedRow = -1;
    private int scrollOffset = 0;
    private static final int ROWS_VISIBLE = 7;
    private static final int ROW_HEIGHT = 20;

    private EditBox bidBox;
    private Button bidButton;
    private Button scrollUpBtn;
    private Button scrollDownBtn;

    // ── 出品タブ ─────────────────────────────────────────
    private EditBox sellPriceBox;
    private Button sellButton;
    private Button btnDur3min;
    private Button btnDur30min;
    private Button btnDur1hour;
    private long selectedDuration = SellAuctionPayload.DURATION_3MIN;

    // ── 共通 ─────────────────────────────────────────────
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;
    private int statusTimer = 0;

    public AuctionScreen() {
        super(Component.literal("🔨 オークション"));
    }

    // =====================================================
    // init
    // =====================================================
    @Override
    protected void init() {
        int w = this.width;
        int h = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;

        initListTab(panelX, panelW, h);
        initSellTab(panelX, panelW, h);
        applyTabVisibility();
    }

    private void initListTab(int panelX, int panelW, int h) {
        bidBox = new EditBox(this.font,
            panelX + 90, h - 52, 130, 18,
            Component.literal("入札額 (¥)"));
        bidBox.setMaxLength(12);
        bidBox.setHint(Component.literal("金額を入力"));
        bidBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(bidBox);

        bidButton = Button.builder(
            Component.literal("入札する"),
            btn -> doPlaceBid()
        ).bounds(panelX + 228, h - 54, 80, 20).build();
        this.addRenderableWidget(bidButton);

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

    private void initSellTab(int panelX, int panelW, int h) {
        int centerX = panelX + panelW / 2;
        int baseY   = 80;

        // 開始価格入力
        sellPriceBox = new EditBox(this.font,
            centerX - 80, baseY, 160, 18,
            Component.literal("開始価格 (¥)"));
        sellPriceBox.setMaxLength(12);
        sellPriceBox.setHint(Component.literal("開始価格を入力 (¥)"));
        sellPriceBox.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        this.addRenderableWidget(sellPriceBox);

        // 期間選択ボタン（3個横並び、幅52px）
        int bw = 52;
        int bh = 18;
        int bGap = 6;
        int totalBw = bw * 3 + bGap * 2;
        int bStartX = centerX - totalBw / 2;
        int bY = baseY + 32;

        btnDur3min = Button.builder(
            Component.literal("3分"),
            btn -> selectDuration(SellAuctionPayload.DURATION_3MIN))
            .bounds(bStartX, bY, bw, bh).build();
        this.addRenderableWidget(btnDur3min);

        btnDur30min = Button.builder(
            Component.literal("30分"),
            btn -> selectDuration(SellAuctionPayload.DURATION_30MIN))
            .bounds(bStartX + bw + bGap, bY, bw, bh).build();
        this.addRenderableWidget(btnDur30min);

        btnDur1hour = Button.builder(
            Component.literal("1時間"),
            btn -> selectDuration(SellAuctionPayload.DURATION_1HOUR))
            .bounds(bStartX + (bw + bGap) * 2, bY, bw, bh).build();
        this.addRenderableWidget(btnDur1hour);

        // 出品ボタン
        sellButton = Button.builder(
            Component.literal("出品する"),
            btn -> doSellAuction())
            .bounds(centerX - 50, bY + 28, 100, 20).build();
        this.addRenderableWidget(sellButton);

        updateDurationButtons();
    }

    // ── タブ切り替えヘルパー ───────────────────────────────
    private void selectTab(int tab) {
        currentTab = tab;
        applyTabVisibility();
    }

    private void applyTabVisibility() {
        boolean list = (currentTab == TAB_LIST);
        boolean sell = (currentTab == TAB_SELL);

        bidBox.visible       = list;
        bidButton.visible    = list;
        scrollUpBtn.visible  = list;
        scrollDownBtn.visible = list;

        sellPriceBox.visible  = sell;
        sellButton.visible    = sell;
        btnDur3min.visible    = sell;
        btnDur30min.visible   = sell;
        btnDur1hour.visible   = sell;
    }

    // ── 期間選択 ─────────────────────────────────────────
    private void selectDuration(long ms) {
        selectedDuration = ms;
        updateDurationButtons();
    }

    private void updateDurationButtons() {
        btnDur3min.setMessage(Component.literal(
            selectedDuration == SellAuctionPayload.DURATION_3MIN  ? "§a§l▶3分"  : "3分"));
        btnDur30min.setMessage(Component.literal(
            selectedDuration == SellAuctionPayload.DURATION_30MIN ? "§a§l▶30分" : "30分"));
        btnDur1hour.setMessage(Component.literal(
            selectedDuration == SellAuctionPayload.DURATION_1HOUR ? "§a§l▶1時間" : "1時間"));
    }

    // ── ローカルプレイヤー名取得 ─────────────────────────────
    private String getLocalPlayerName() {
        if (this.minecraft != null && this.minecraft.player != null) {
            return this.minecraft.player.getName().getString();
        }
        return "";
    }

    // =====================================================
    // render
    // =====================================================
    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(0, 0, this.width, this.height, 0xC0101018);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        this.renderBackground(gfx, mouseX, mouseY, delta);

        int w      = this.width;
        int h      = this.height;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;
        int panelY = 20;

        // タイトル・残高
        gfx.drawCenteredString(this.font, "🔨 オークション", w / 2, panelY, 0xFFDD44);
        gfx.drawString(this.font,
            "残高: ¥" + String.format("%,d", balance), panelX, panelY, 0x00FF88);

        // ── タブ描画 ──────────────────────────────────────
        renderTabs(gfx, panelX, panelW, panelY + 12);

        if (currentTab == TAB_LIST) {
            renderListTab(gfx, mouseX, mouseY, panelX, panelW, panelY, h);
        } else {
            renderSellTab(gfx, panelX, panelW);
        }

        // ステータスメッセージ
        if (statusTimer > 0) {
            statusTimer--;
            int alpha = Math.min(255, statusTimer * 8);
            int col   = (statusColor & 0x00FFFFFF) | (alpha << 24);
            gfx.drawCenteredString(this.font, statusMessage, w / 2, h - 18, col);
        }

        super.render(gfx, mouseX, mouseY, delta);

        if (currentTab == TAB_LIST) {
            renderBidHistoryTooltip(gfx, mouseX, mouseY);
        }
    }

    private void renderTabs(GuiGraphics gfx, int panelX, int panelW, int y) {
        int tabW = 80;
        int tabH = 14;

        // 一覧タブ
        boolean listSel = (currentTab == TAB_LIST);
        gfx.fill(panelX, y, panelX + tabW, y + tabH,
            listSel ? 0xFF334466 : 0xFF222233);
        gfx.drawCenteredString(this.font, "一覧/入札",
            panelX + tabW / 2, y + 3, listSel ? 0xFFFFFF : 0x888888);

        // 出品タブ
        boolean sellSel = (currentTab == TAB_SELL);
        gfx.fill(panelX + tabW + 2, y, panelX + tabW * 2 + 2, y + tabH,
            sellSel ? 0xFF334466 : 0xFF222233);
        gfx.drawCenteredString(this.font, "出品",
            panelX + tabW + 2 + tabW / 2, y + 3, sellSel ? 0xFFFFFF : 0x888888);
    }

    private void renderListTab(GuiGraphics gfx, int mouseX, int mouseY,
                                int panelX, int panelW, int panelY, int h) {
        int listY  = getListY();
        int tableW = panelW - 22;
        String localName = getLocalPlayerName();

        // ヘッダー行
        int headerY = panelY + 28;
        gfx.fill(panelX, headerY, panelX + tableW, headerY + 15, 0xFF444466);
        gfx.drawString(this.font, "出品者",     col(panelX, 0), headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "アイテム",   col(panelX, 1), headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "現在入札額", col(panelX, 2), headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "残り時間",   col(panelX, 3), headerY + 4, 0xCCCCFF);
        gfx.drawString(this.font, "期間",       col(panelX, 4), headerY + 4, 0xCCCCFF);

        // 出品一覧
        int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
        for (int i = scrollOffset; i < end; i++) {
            var dto  = listings.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            boolean isSelected = (i == selectedRow);
            boolean isOwn      = dto.sellerName().equals(localName);

            int rowBg = isSelected ? 0xFF223355
                : (i % 2 == 0 ? 0xFF1E1E2E : 0xFF252535);
            gfx.fill(panelX, rowY, panelX + tableW, rowY + ROW_HEIGHT - 1, rowBg);
            if (isSelected) {
                gfx.fill(panelX, rowY, panelX + tableW, rowY + 1, 0xFF5577AA);
                gfx.fill(panelX, rowY + ROW_HEIGHT - 2, panelX + tableW, rowY + ROW_HEIGHT - 1, 0xFF5577AA);
            }

            gfx.drawString(this.font, truncate(dto.sellerName(), 10), col(panelX, 0), rowY + 6, 0xCCCCCC);

            ItemStack icon = makeIconStack(dto.itemId(), dto.itemCount());
            gfx.renderItem(icon, col(panelX, 1), rowY + 2);
            gfx.drawString(this.font, truncate(dto.itemName(), 11), col(panelX, 1) + 20, rowY + 6, 0xFFFFFF);

            if (dto.currentBid() > 0) {
                gfx.drawString(this.font,
                    "¥" + String.format("%,d", dto.currentBid()),
                    col(panelX, 2), rowY + 6, 0xFFDD44);
            } else {
                gfx.drawString(this.font,
                    "¥" + String.format("%,d", dto.startPrice()) + " ~",
                    col(panelX, 2), rowY + 6, 0x888855);
            }

            long secs     = dto.remainingSecs();
            String timeStr = formatTime(secs);
            int timeColor  = secs <= 60 ? 0xFF4444 : secs <= 300 ? 0xFF8833 : 0x88CCFF;
            gfx.drawString(this.font, timeStr, col(panelX, 3), rowY + 6, timeColor);

            // 出品期間
            gfx.drawString(this.font,
                formatDuration(dto.durationMs()), col(panelX, 4), rowY + 6, 0xAA88CC);

            // 自分の出品 → 取消ボタン（入札ありの場合はグレーアウト）
            // 他人の出品 → 入札ボタン
            int btnX = panelX + tableW - 44;
            boolean hov = mouseX >= btnX && mouseX <= btnX + 42
                       && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1;

            if (isOwn) {
                boolean hasBid = dto.currentBid() > 0;
                if (hasBid) {
                    // 入札済み → グレー（取消不可を視覚的に表現）
                    gfx.fill(btnX, rowY + 2, btnX + 42, rowY + ROW_HEIGHT - 3, 0xFF333333);
                    gfx.drawCenteredString(this.font, "取消不可", btnX + 21, rowY + 6, 0x666666);
                } else {
                    // 取消可能 → 赤系
                    gfx.fill(btnX, rowY + 2, btnX + 42, rowY + ROW_HEIGHT - 3,
                        hov ? 0xFF550000 : 0xFF330000);
                    gfx.drawCenteredString(this.font, "取消",
                        btnX + 21, rowY + 6, hov ? 0xFF8888 : 0xCC4444);
                }
            } else {
                // 入札ボタン（青系）
                gfx.fill(btnX, rowY + 2, btnX + 42, rowY + ROW_HEIGHT - 3,
                    hov ? 0xFF004488 : 0xFF002244);
                gfx.drawCenteredString(this.font, "入札",
                    btnX + 21, rowY + 6, hov ? 0x88DDFF : 0x4499CC);
            }
        }

        if (listings.isEmpty()) {
            gfx.drawCenteredString(this.font,
                "出品中のアイテムはありません", this.width / 2, listY + 30, 0x888888);
        }

        // 入札エリア
        int areaY = h - 62;
        gfx.fill(panelX, areaY, panelX + panelW, h - 26, 0xFF1A1A33);
        gfx.fill(panelX, areaY, panelX + panelW, areaY + 1, 0xFF445588);

        if (selectedRow >= 0 && selectedRow < listings.size()) {
            var sel = listings.get(selectedRow);
            gfx.drawString(this.font,
                "選択: " + truncate(sel.itemName(), 20)
                + "  最低入札額: ¥" + String.format("%,d", sel.minimumBid()),
                panelX + 4, areaY + 5, 0xAAAAFF);
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

        gfx.drawString(this.font, "入札額 ¥:", panelX + 4, h - 46, 0xCCCCCC);
    }

    private void renderSellTab(GuiGraphics gfx, int panelX, int panelW) {
        int centerX = panelX + panelW / 2;
        int baseY   = 56;

        // パネル背景
        gfx.fill(panelX, baseY, panelX + panelW, baseY + 140, 0xFF1A1A33);
        gfx.fill(panelX, baseY, panelX + panelW, baseY + 1, 0xFF445588);

        // 手持ちアイテムプレビュー
        var held = this.minecraft.player != null ? this.minecraft.player.getMainHandItem() : ItemStack.EMPTY;
        int previewX = centerX - 8;
        int previewY = baseY + 10;
        if (!held.isEmpty()) {
            gfx.renderItem(held, previewX, previewY);
            gfx.drawCenteredString(this.font,
                held.getHoverName().getString(),
                centerX, previewY + 18, 0xFFFFFF);
        } else {
            gfx.drawCenteredString(this.font,
                "手にアイテムを持ってください",
                centerX, previewY + 4, 0xFF6666);
        }

        // ラベル類
        gfx.drawCenteredString(this.font, "開始価格 :", centerX, baseY + 46, 0xCCCCCC);
        gfx.drawCenteredString(this.font, "出品期間 :", centerX, baseY + 74, 0xCCCCCC);
    }

    // =====================================================
    // 入札履歴ツールチップ
    // =====================================================
    private void renderBidHistoryTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
        int w      = this.width;
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;
        int tableW = panelW - 22;
        int listY  = getListY();

        int hoveredRow = -1;
        int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
        for (int i = scrollOffset; i < end; i++) {
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
             && mouseX >= panelX && mouseX < panelX + tableW - 44) {
                hoveredRow = i;
                break;
            }
        }
        if (hoveredRow < 0) return;

        var dto     = listings.get(hoveredRow);
        var history = dto.bidHistory();

        int lineH      = 11;
        int padding    = 5;
        int tipW       = 200;
        int entryCount = history.isEmpty() ? 1 : Math.min(5, history.size());
        int tipH       = padding * 2 + lineH + entryCount * lineH + 2;

        int tipX = mouseX + 10;
        if (tipX + tipW > w - 4) tipX = mouseX - tipW - 10;
        int tipY = mouseY - tipH / 2;
        tipY = Math.max(4, Math.min(tipY, this.height - tipH - 4));

        gfx.fill(tipX - 1, tipY - 1, tipX + tipW + 1, tipY + tipH + 1, 0xFF445566);
        gfx.fill(tipX,     tipY,     tipX + tipW,     tipY + tipH,     0xF0090E18);

        gfx.drawString(this.font, "入札履歴", tipX + padding, tipY + padding, 0xAAAAFF);

        int yBase = tipY + padding + lineH + 2;
        if (history.isEmpty()) {
            gfx.drawString(this.font, "入札なし", tipX + padding, yBase, 0x555577);
        } else {
            int startIdx = Math.max(0, history.size() - 5);
            int dispIdx  = 0;
            for (int j = history.size() - 1; j >= startIdx; j--, dispIdx++) {
                var entry   = history.get(j);
                long agoSecs = (System.currentTimeMillis() - entry.timestampMs()) / 1000;
                String line = entry.bidderName()
                    + "  ¥" + String.format("%,d", entry.amount())
                    + "  " + formatAgo(agoSecs);
                int color = (dispIdx == 0) ? 0xFFDD44 : 0x999999;
                gfx.drawString(this.font, truncate(line, 22),
                    tipX + padding, yBase + dispIdx * lineH, color);
            }
        }
    }

    // =====================================================
    // アクション
    // =====================================================
    private void doPlaceBid() {
        if (selectedRow < 0 || selectedRow >= listings.size()) {
            showStatus("入札する出品を選択してください", 0xFF8888);
            return;
        }
        String txt = bidBox.getValue().trim();
        if (txt.isEmpty()) { showStatus("入札額を入力してください", 0xFF8888); return; }
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

    private void doSellAuction() {
        if (this.minecraft == null || this.minecraft.player == null) return;

        var held = this.minecraft.player.getMainHandItem();
        if (held.isEmpty()) {
            showStatus("手にアイテムを持ってください", 0xFF8888);
            return;
        }

        String txt = sellPriceBox.getValue().trim();
        if (txt.isEmpty()) { showStatus("開始価格を入力してください", 0xFF8888); return; }

        long price;
        try {
            price = Long.parseLong(txt);
        } catch (NumberFormatException e) {
            showStatus("正しい金額を入力してください", 0xFF8888);
            return;
        }
        if (price <= 0) { showStatus("1円以上を入力してください", 0xFF8888); return; }

        PacketDistributor.sendToServer(new SellAuctionPayload(price, selectedDuration));
        showStatus("出品リクエストを送信しました", 0x44FF88);
        sellPriceBox.setValue("");
    }

    private void doCancelAuction(UUID listingId) {
        PacketDistributor.sendToServer(new CancelAuctionPayload(listingId));
        showStatus("取消リクエストを送信しました", 0xFFAA44);
    }

    // =====================================================
    // 公開 API（ClientNetworkHandler から呼ばれる）
    // =====================================================
    public void updateListings(List<SyncAuctionPayload.AuctionDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance  = newBalance;
        this.scrollOffset = Math.min(scrollOffset, Math.max(0, listings.size() - ROWS_VISIBLE));
        if (selectedRow >= listings.size()) selectedRow = -1;
    }

    // =====================================================
    // 入力イベント
    // =====================================================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int w      = this.width;
            int panelW = Math.min(520, w - 40);
            int panelX = (w - panelW) / 2;
            int panelY = 20;
            String localName = getLocalPlayerName();

            // ── タブクリック判定 ──────────────────────────
            int tabY = panelY + 12;
            int tabW = 80;
            int tabH = 14;
            if (mouseY >= tabY && mouseY <= tabY + tabH) {
                if (mouseX >= panelX && mouseX <= panelX + tabW) {
                    selectTab(TAB_LIST);
                    return true;
                }
                if (mouseX >= panelX + tabW + 2 && mouseX <= panelX + tabW * 2 + 2) {
                    selectTab(TAB_SELL);
                    return true;
                }
            }

            // ── 一覧タブの行クリック判定 ──────────────────
            if (currentTab == TAB_LIST) {
                int tableW = panelW - 22;
                int listY  = getListY();
                int btnX   = panelX + tableW - 44;

                int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
                for (int i = scrollOffset; i < end; i++) {
                    int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;

                    // ボタン領域クリック判定
                    if (mouseX >= btnX && mouseX <= btnX + 42
                     && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1) {
                        var dto   = listings.get(i);
                        boolean isOwn   = dto.sellerName().equals(localName);
                        boolean hasBid  = dto.currentBid() > 0;

                        if (isOwn && !hasBid) {
                            // 取消
                            doCancelAuction(dto.listingId());
                        } else if (!isOwn) {
                            // 行選択 + 最低入札額をセット
                            selectedRow = i;
                            bidBox.setValue(String.valueOf(dto.minimumBid()));
                        }
                        // isOwn && hasBid → 何もしない（グレーアウト）
                        return true;
                    }

                    // 行全体クリック → 選択のみ（自分の出品でも選択は許可）
                    if (mouseX >= panelX && mouseX < panelX + tableW
                     && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1) {
                        selectedRow = i;
                        bidBox.setValue(String.valueOf(listings.get(i).minimumBid()));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == TAB_LIST) {
            if (scrollY > 0) scrollOffset = Math.max(0, scrollOffset - 1);
            else scrollOffset = Math.min(Math.max(0, listings.size() - ROWS_VISIBLE), scrollOffset + 1);
        }
        return true;
    }

    // =====================================================
    // ユーティリティ
    // =====================================================
    private ItemStack makeIconStack(String itemId, int count) {
        try {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            return new ItemStack(item, count);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private int col(int panelX, int colIndex) {
        // 0:出品者 1:アイテム 2:現在入札額 3:残り時間 4:出品期間
        int[] offsets = {4, 96, 228, 332, 396};
        return panelX + offsets[colIndex];
    }

    /** 一覧タブのリスト開始Y座標（タブ+ヘッダー分を加算）*/
    private int getListY() { return 20 + 14 + 16 + 14; }

    private String formatTime(long secs) {
        if (secs <= 0) return "終了";
        long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%d:%02d", m, s);
    }

    /** 出品期間(ms)を "3分" / "30分" / "1時間" に変換。0は "―" */
    private String formatDuration(long ms) {
        if (ms <= 0)            return "―";
        if (ms < 600_000)       return "3分";    // ~10分未満 → 3分枠
        if (ms < 3_000_000)     return "30分";   // ~50分未満 → 30分枠
        return "1時間";
    }

    private String formatAgo(long secs) {
        if (secs < 60) return secs + "秒前";
        if (secs < 3600) return (secs / 60) + "分前";
        return (secs / 3600) + "時間前";
    }

    private String truncate(String str, int max) {
        return str.length() <= max ? str : str.substring(0, max - 1) + "…";
    }

    private void showStatus(String msg, int color) {
        statusMessage = msg; statusColor = color; statusTimer = 80;
    }

    @Override public boolean isPauseScreen() { return false; }
}
