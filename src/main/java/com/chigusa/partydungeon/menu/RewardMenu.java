package com.chigusa.partydungeon.menu;

import java.util.ArrayList;
import java.util.List;

import com.chigusa.partydungeon.data.Difficulty;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.Reward;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 報酬を入れる画面。上の4段（36マス）にアイテムを入れるだけ。
 * いちばん下の段で難易度を切り替える。確率は「確率の設定」から。
 */
public final class RewardMenu extends AbstractContainerMenu {
    private static final int ITEM_SLOTS = 36;
    private static final int TAB_FIRST = 45;
    private static final int CHANCE_SLOT = 51;
    private static final int BACK_SLOT = 53;
    private static final int[] CHANCES = {1, 5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    private final MinecraftServer server;
    private final String defId;
    private final SimpleContainer container = new SimpleContainer(54);
    private final int[] chances = new int[ITEM_SLOTS];
    private Difficulty tab;
    private boolean ready;

    private RewardMenu(int id, Inventory inv, MinecraftServer server, String defId, Difficulty tab) {
        super(MenuType.GENERIC_9x6, id);
        this.server = server;
        this.defId = defId;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9;
                if (index < ITEM_SLOTS) {
                    addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18));
                } else {
                    addSlot(new Slot(container, index, 8 + col * 18, 18 + row * 18) {
                        @Override
                        public boolean mayPickup(Player player) {
                            return false;
                        }

                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }
                    });
                }
            }
        }
        int yOff = (6 - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 103 + row * 18 + yOff));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 161 + yOff));
        }
        loadTab(tab);
        ready = true;
    }

    public static void open(MinecraftServer server, ServerPlayer p, String defId, Difficulty tab) {
        if (PDData.get(server).dungeons.get(defId) == null) {
            return;
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new RewardMenu(id, inv, server, defId, tab),
                Component.literal("報酬（上の段にアイテムを入れる）")));
    }

    private DungeonDef def() {
        return PDData.get(server).dungeons.get(defId);
    }

    private void loadTab(Difficulty d) {
        this.tab = d;
        DungeonDef def = def();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            container.setItem(i, ItemStack.EMPTY);
            chances[i] = 100;
        }
        if (def != null) {
            List<Reward> list = def.rewards(d);
            for (int i = 0; i < list.size() && i < ITEM_SLOTS; i++) {
                container.setItem(i, list.get(i).stack(server.registryAccess()));
                chances[i] = list.get(i).chance();
            }
        }
        for (int i = ITEM_SLOTS; i < TAB_FIRST; i++) {
            container.setItem(i, Menus.icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        }
        Difficulty[] all = Difficulty.values();
        for (int i = 0; i < all.length; i++) {
            Difficulty x = all[i];
            int n = def == null ? 0 : def.rewards(x).size();
            ItemStack icon = Menus.icon(x.icon, (x == d ? "▶ " : "") + x.label + " の報酬", x.color,
                    n + " 個", x == d ? "今これを編集中" : "クリックで切り替え");
            if (x == d) {
                icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            }
            container.setItem(TAB_FIRST + i, icon);
        }
        container.setItem(50, Menus.icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        container.setItem(CHANCE_SLOT, Menus.icon(Items.RABBIT_FOOT, "確率の設定", ChatFormatting.AQUA,
                "アイテムごとに、出る確率を決めます", "（最初は 100%）"));
        container.setItem(52, Menus.icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
        container.setItem(BACK_SLOT, Menus.icon(Items.SPRUCE_DOOR, "保存して製作メニューへ", ChatFormatting.GRAY));
        if (ready) {
            broadcastChanges();
        }
    }

    /** 今の中身を、今のタブの報酬として保存 */
    private void save() {
        DungeonDef def = def();
        if (def == null) {
            return;
        }
        List<Reward> list = new ArrayList<>();
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack st = container.getItem(i);
            if (!st.isEmpty()) {
                list.add(Reward.of(st, chances[i], server.registryAccess()));
            }
        }
        def.rewards.put(tab, list);
        PDData.get(server).setDirty();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= ITEM_SLOTS && slotId < 54) {
            if (clickType == ClickType.PICKUP && player instanceof ServerPlayer sp) {
                int tabIndex = slotId - TAB_FIRST;
                if (tabIndex >= 0 && tabIndex < Difficulty.values().length) {
                    save();
                    loadTab(Difficulty.values()[tabIndex]);
                } else if (slotId == CHANCE_SLOT) {
                    save();
                    Difficulty t = tab;
                    chanceMenu(server, sp, defId, t);
                    return;
                } else if (slotId == BACK_SLOT) {
                    save();
                    sp.closeContainer();
                    com.chigusa.partydungeon.net.ScreenActions.openManager(server, sp, defId);
                    return;
                }
            }
            sendAllDataToRemote();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ITEM_SLOTS) {
            if (!moveItemStackTo(stack, 54, 90, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= 54) {
            if (!moveItemStackTo(stack, 0, ITEM_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            save();
        }
    }

    // ---------------------------------------------------------------- 確率

    private static void chanceMenu(MinecraftServer server, ServerPlayer p, String defId, Difficulty tab) {
        DungeonDef def = PDData.get(server).dungeons.get(defId);
        if (def == null) {
            return;
        }
        List<Reward> list = def.rewards(tab);
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("確率の設定（" + tab.label + "）"), 6);
        for (int i = 0; i < list.size() && i < 45; i++) {
            int index = i;
            Reward r = list.get(i);
            ItemStack icon = r.stack(server.registryAccess()).copy();
            icon.set(DataComponents.CUSTOM_NAME, Component.literal(icon.getHoverName().getString() + "  " + r.chance() + "%")
                    .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
            icon.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                    Component.literal("左クリック: 上げる  右クリック: 下げる")
                            .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
            b.button2(i, icon, (pl, button) -> {
                DungeonDef d = PDData.get(server).dungeons.get(defId);
                if (d == null || index >= d.rewards(tab).size()) {
                    return;
                }
                Reward cur = d.rewards(tab).get(index);
                d.rewards(tab).set(index, new Reward(cur.item(), step(cur.chance(), button == 1 ? -1 : 1)));
                PDData.get(server).setDirty();
                chanceMenu(server, pl, defId, tab);
            });
        }
        if (list.isEmpty()) {
            b.button(22, Menus.icon(Items.BARRIER, "報酬がまだありません", ChatFormatting.GRAY), null);
        }
        b.button(49, Menus.icon(Items.SPRUCE_DOOR, "戻る", ChatFormatting.GRAY), pl -> open(server, pl, defId, tab));
        b.open(p);
        Msg.info(p, "確率はクリアした人それぞれに、アイテムごとに抽選されます。");
    }

    private static int step(int current, int dir) {
        int idx = 0;
        for (int i = 0; i < CHANCES.length; i++) {
            if (CHANCES[i] <= current) {
                idx = i;
            }
        }
        idx = Math.max(0, Math.min(CHANCES.length - 1, idx + dir));
        return CHANCES[idx];
    }
}
