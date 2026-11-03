package com.chigusa.partydungeon.menu;

import java.util.ArrayList;
import java.util.List;

import com.chigusa.partydungeon.PDConfig;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.run.RunManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;

/** 「遊ぶ」画面：公開済みのダンジョン一覧。クリックでパーティごと入場。 */
public final class PlayMenu {
    private static final int PER_PAGE = 45;

    public static void open(MinecraftServer server, ServerPlayer player, int page) {
        List<DungeonDef> list = new ArrayList<>();
        for (DungeonDef d : PDData.get(server).dungeons.values()) {
            if (d.published) {
                list.add(d);
            }
        }
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));

        ButtonMenu.Builder b = new ButtonMenu.Builder(
                Component.literal("ダンジョンを選ぶ（空き部屋 " + RunManager.freeRooms() + "/" + PDConfig.ROOM_COUNT.get() + "）"), 6);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            DungeonDef d = list.get(idx);
            String id = d.id;
            b.button(i, icon(d), p -> {
                p.closeContainer();
                DungeonDef now = PDData.get(server).dungeons.get(id);
                if (now != null) {
                    RunManager.start(server, p, now);
                }
            });
        }
        if (list.isEmpty()) {
            b.button(22, named(new ItemStack(Items.BARRIER), "公開されているダンジョンがありません", ChatFormatting.RED), null);
        }
        if (pg > 0) {
            b.button(45, named(new ItemStack(Items.ARROW), "前のページ", ChatFormatting.YELLOW), p -> open(server, p, pg - 1));
        }
        if (pg < pages - 1) {
            b.button(53, named(new ItemStack(Items.ARROW), "次のページ", ChatFormatting.YELLOW), p -> open(server, p, pg + 1));
        }
        b.open(player);
    }

    private static ItemStack icon(DungeonDef d) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
        // ボスがいればそのスポーンエッグを表示
        for (SpawnPoint s : d.spawns) {
            if (s.boss()) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.entity()).orElse(null);
                SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
                if (egg != null) {
                    stack = new ItemStack(egg);
                }
                break;
            }
        }
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(d.name).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        List<Component> lore = new ArrayList<>();
        lore.add(line("クリア条件: " + d.clear.label, ChatFormatting.WHITE));
        lore.add(line("広さ: " + d.size + "×" + d.size + "  高さ: " + d.height, ChatFormatting.GRAY));
        int playing = RunManager.activeCount(d.id);
        if (playing > 0) {
            lore.add(line("挑戦中のパーティ: " + playing, ChatFormatting.AQUA));
        }
        lore.add(line("クリックで入場（パーティ全員）", ChatFormatting.GREEN));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(color));
    }

    private static ItemStack named(ItemStack stack, String name, ChatFormatting color) {
        stack.set(DataComponents.CUSTOM_NAME, line(name, color));
        return stack;
    }

    private PlayMenu() {
    }
}
