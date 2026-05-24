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

    private EditBox priceBox;
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
        int panelW = Math.min(520, w - 40);
        int panelX = (w - panelW) / 2;

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

        int panelY = 30;
        scrollUpBtn = Button.builder(Component.literal("▲"),
            btn -> scrollOffset = Math.max(0, scrollOffset - 1))
            .bounds(panelX + panelW - 20, panelY + 20, 18, 18).build();
        this.addRenderableWidget(scrollUpBtn);

        scrollDownBtn = Button.builder(Component.literal("▼"),
            btn -> scrollOffset = Math.min(
                Math.max(0, listings.size() - ROWS_VISIBLE), scrollOffset + 1))
            .bounds(panelX + panelW - 20, panelY + 40, 18, 18).build();
        this.addRenderableWidget(scrollDownBtn);
    }

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
        int panelY = 30;

        // タイトル・残高
        gfx.drawCenteredString(this.font, "🏪 フリーマーケット", w / 2, panelY, 0xFFFFAA);
        gfx.drawString(this.font,
            "残高: ¥" + String.format("%,d", balance), panelX, panelY, 0x00FF88);

        // ヘッダー行
        int headerY = panelY + 16;
        int tableW = panelW - 22;
        gfx.fill(panelX, headerY, panelX + tableW, headerY + 14, 0xFF555555);
        gfx.drawString(this.font, "出品者",   panelX + 4,   headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "アイテム", panelX + 130,  headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "数量",     panelX + 280,  headerY + 3, 0xFFFFFF);
        gfx.drawString(this.font, "価格",     panelX + 330,  headerY + 3, 0xFFFFFF);

        // 一覧行
        int listY = headerY + 16;
        int end = Math.min(scrollOffset + ROWS_VISIBLE, listings.size());
        for (int i = scrollOffset; i < end; i++) {
            var dto = listings.get(i);
            int rowY = listY + (i - scrollOffset) * ROW_HEIGHT;
            int rowBg = (i % 2 == 0) ? 0xFF2A2A2A : 0xFF333333;
            gfx.fill(panelX, rowY, panelX + tableW, rowY + ROW_HEIGHT - 2, rowBg);

            // 出品者
            gfx.drawString(this.font, truncate(dto.sellerName(), 13), panelX + 4, rowY + 6, 0xCCCCCC);

            // アイテムアイコン
            ItemStack icon = makeIconStack(dto.itemId(), dto.itemCount());
            gfx.renderItem(icon, panelX + 110, rowY + 2);

            // アイテム名
            gfx.drawString(this.font, truncate(dto.itemName(), 13), panelX + 130, rowY + 6, 0xFFFFFF);

            // 数量・価格
            gfx.drawString(this.font, "x" + dto.itemCount(), panelX + 280, rowY + 6, 0xAAAAAA);
            gfx.drawString(this.font,
                "¥" + String.format("%,d", dto.price()), panelX + 330, rowY + 6, 0xFFDD44);

            // 購入ボタン
            int btnX = panelX + tableW - 45;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 42
                           && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            gfx.fill(btnX, rowY + 1, btnX + 42, rowY + ROW_HEIGHT - 3,
                hovered ? 0xFF005500 : 0xFF003300);
            gfx.drawCenteredString(this.font, "購入",
                btnX + 21, rowY + 6, hovered ? 0x88FF88 : 0x44CC44);
        }

        // 出品エリア背景
        gfx.fill(panelX, h - 58, panelX + panelW, h - 28, 0xFF222244);
        gfx.drawString(this.font, "手に持ったアイテムを出品:", panelX + 4, h - 54, 0xAAAAFF);

        super.render(gfx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int w = this.width;
            int panelW = Math.min(520, w - 40);
            int panelX = (w - panelW) / 2;
            int panelY = 30;
            int listY = panelY + 32;
            int tableW = panelW - 22;
            int btnX = panelX + tableW - 45;

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

    public void updateListings(List<SyncListingsPayload.ListingDto> newListings, long newBalance) {
        this.listings = new ArrayList<>(newListings);
        this.balance = newBalance;
        this.scrollOffset = Math.min(scrollOffset, Math.max(0, listings.size() - ROWS_VISIBLE));
    }

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