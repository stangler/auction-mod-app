package com.example.freemarket.market;

import com.example.freemarket.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * フリーマーケット メニュー（サーバー/クライアント共通）
 * スロットなし。データはネットワークパケット or SavedData経由。
 */
public class FleaMarketMenu extends AbstractContainerMenu {

    // GUI操作モード
    public enum Mode { BROWSE, SELL }

    private Mode mode;

    // サーバー側コンストラクタ
    public FleaMarketMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.FLEA_MARKET.get(), containerId);
        this.mode = Mode.BROWSE;
    }

    // クライアント側コンストラクタ（パケット経由）
    public FleaMarketMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // GUIを閉じる条件なし（距離制限不要）
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }
}
