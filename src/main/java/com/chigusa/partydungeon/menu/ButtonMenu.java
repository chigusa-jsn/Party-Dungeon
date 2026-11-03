package com.chigusa.partydungeon.menu;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/**
 * チェストの見た目を使った「ボタンの並んだ画面」。
 * アイテムはボタンとして表示されるだけで、取り出せない。
 */
public final class ButtonMenu extends ChestMenu {
    private final Map<Integer, Consumer<ServerPlayer>> actions;
    private final int buttonSlots;

    private ButtonMenu(int id, Inventory inv, SimpleContainer container, int rows,
            Map<Integer, Consumer<ServerPlayer>> actions) {
        super(typeFor(rows), id, inv, container, rows);
        this.actions = actions;
        this.buttonSlots = rows * 9;
    }

    private static MenuType<?> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // アイテムは一切動かさない
        if (player instanceof ServerPlayer sp) {
            if (slotId >= 0 && slotId < buttonSlots && clickType == ClickType.PICKUP) {
                Consumer<ServerPlayer> action = actions.get(slotId);
                if (action != null) {
                    action.accept(sp);
                    return;
                }
            }
            this.setCarried(ItemStack.EMPTY);
            this.sendAllDataToRemote();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 画面を組み立てるための部品 */
    public static final class Builder {
        private final Component title;
        private final int rows;
        private final SimpleContainer container;
        private final Map<Integer, Consumer<ServerPlayer>> actions = new HashMap<>();

        public Builder(Component title, int rows) {
            this.title = title;
            this.rows = Math.max(1, Math.min(6, rows));
            this.container = new SimpleContainer(this.rows * 9);
        }

        public Builder button(int slot, ItemStack icon, Consumer<ServerPlayer> action) {
            if (slot >= 0 && slot < rows * 9) {
                container.setItem(slot, icon);
                if (action != null) {
                    actions.put(slot, action);
                }
            }
            return this;
        }

        public void open(ServerPlayer player) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inv, p) -> new ButtonMenu(id, inv, container, rows, actions), title));
        }
    }
}
