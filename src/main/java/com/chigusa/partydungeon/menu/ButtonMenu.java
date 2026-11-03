package com.chigusa.partydungeon.menu;

import java.util.HashMap;
import java.util.Map;

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
    /** ボタンを押したときの処理。button は 0=左クリック、1=右クリック */
    @FunctionalInterface
    public interface Action {
        void run(ServerPlayer player, int button);
    }

    private final Map<Integer, Action> actions;
    private final int buttonSlots;
    /** 画面の種類（自動で開き直すときの目印） */
    public final String kind;

    private ButtonMenu(int id, Inventory inv, SimpleContainer container, int rows,
            Map<Integer, Action> actions, String kind) {
        super(typeFor(rows), id, inv, container, rows);
        this.actions = actions;
        this.kind = kind;
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
                Action action = actions.get(slotId);
                if (action != null) {
                    this.setCarried(ItemStack.EMPTY);
                    action.run(sp, button);
                    if (sp.containerMenu == this) {
                        this.sendAllDataToRemote();
                    }
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
        private final Map<Integer, Action> actions = new HashMap<>();
        private String kind = "";

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder(Component title, int rows) {
            this.title = title;
            this.rows = Math.max(1, Math.min(6, rows));
            this.container = new SimpleContainer(this.rows * 9);
        }

        public Builder button(int slot, ItemStack icon, java.util.function.Consumer<ServerPlayer> action) {
            return button2(slot, icon, action == null ? null : (p, b) -> action.accept(p));
        }

        /** 左右クリックを区別するボタン */
        public Builder button2(int slot, ItemStack icon, Action action) {
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
                    (id, inv, p) -> new ButtonMenu(id, inv, container, rows, actions, kind), title));
        }
    }
}
