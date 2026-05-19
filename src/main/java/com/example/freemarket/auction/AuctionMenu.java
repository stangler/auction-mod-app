package com.example.freemarket.auction;

import com.example.freemarket.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * オークション メニュー（サーバー/クライアント共通）
 * スロットなし。データはネットワークパケット経由。
 */
public class AuctionMenu extends AbstractContainerMenu {

    // サーバー側コンストラクタ
    public AuctionMenu(int containerId, Inventory playerInventory) {
        super(ModMenuTypes.AUCTION.get(), containerId);
    }

    // クライアント側コンストラクタ（パケット経由）
    public AuctionMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
