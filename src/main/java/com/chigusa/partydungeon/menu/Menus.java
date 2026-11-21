package com.chigusa.partydungeon.menu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDConfig;
import com.chigusa.partydungeon.data.ClearCondition;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.party.PartyManager;
import com.chigusa.partydungeon.party.PartyManager.Party;
import com.chigusa.partydungeon.run.RunManager;
import com.chigusa.partydungeon.util.Msg;
import com.chigusa.partydungeon.workshop.WorkshopManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;

/**
 * すべての画面。チェストの見た目で、アイテムをボタンとして並べている。
 */
public final class Menus {
    private static final int[] DISTANCES = {0, 4, 8, 12, 16, 24, 32, 48};
    private static final int[] SIZES = {16, 32, 48, 64, 96, 128};
    private static final int[] HEIGHTS = {8, 12, 16, 24, 32, 48, 64, 96};

    // ================================================================ メイン

    public static void main(MinecraftServer server, ServerPlayer p) {
        boolean op = p.hasPermissions(2);
        boolean inWorkshop = WorkshopManager.isIn(p);
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("パーティダンジョン"), 3).kind("main");

        if (RunManager.isInRun(p.getUUID())) {
            b.button(13, icon(Items.BARRIER, "ダンジョンから抜ける", ChatFormatting.RED, "クリックで元の場所に戻ります"),
                    pl -> {
                        pl.closeContainer();
                        RunManager.leave(server, pl);
                    });
            b.open(p);
            return;
        }

        b.button(11, icon(Items.FILLED_MAP, "ダンジョンに挑む", ChatFormatting.GOLD, "ダンジョンを選んでロビーへ"),
                pl -> dungeonList(server, pl, 0));
        int recruiting = PartyManager.recruiting().size();
        b.button(13, icon(Items.BELL, "募集中のパーティ", ChatFormatting.AQUA,
                recruiting == 0 ? "今は募集がありません" : recruiting + " パーティが募集中"),
                pl -> recruitBoard(server, pl));
        b.button(15, head(p, "パーティ（ロビー）", "仲間の招待・募集・スタート"), pl -> lobby(server, pl));

        if (op) {
            if (inWorkshop) {
                b.button(20, icon(Items.CRAFTING_TABLE, "製作メニュー", ChatFormatting.YELLOW,
                        "区画の中なら編集画面、外なら一覧"), pl -> workshop(server, pl));
                b.button(24, icon(Items.GRASS_BLOCK, "遊ぶモードに戻る", ChatFormatting.GREEN,
                        "作業場を出て、元の場所・ゲームモードへ"), pl -> {
                            pl.closeContainer();
                            WorkshopManager.exit(server, pl);
                        });
            } else {
                b.button(22, icon(Items.IRON_PICKAXE, "製作モードへ", ChatFormatting.YELLOW,
                        "作業場（クリエイティブ）に移動します", "OP だけに見えています"), pl -> {
                            pl.closeContainer();
                            WorkshopManager.enter(server, pl);
                        });
            }
        }
        b.open(p);
    }

    // ================================================================ 遊ぶ

    public static void dungeonList(MinecraftServer server, ServerPlayer p, int page) {
        List<DungeonDef> list = new ArrayList<>();
        for (DungeonDef d : PDData.get(server).dungeons.values()) {
            if (d.published) {
                list.add(d);
            }
        }
        int perPage = 45;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("ダンジョンを選ぶ（空き部屋 "
                + RunManager.freeRooms() + "/" + PDConfig.ROOM_COUNT.get() + "）"), 6);
        PDData pdata = PDData.get(server);
        for (int i = 0; i < perPage && pg * perPage + i < list.size(); i++) {
            DungeonDef d = list.get(pg * perPage + i);
            String id = d.id;
            if (!pdata.isUnlocked(p.getUUID(), d)) {
                // まだ解放していない：鍵つきで見せる
                String hint = com.chigusa.partydungeon.api.PDApi.hint(d);
                b.button(i, icon(Items.IRON_BARS, "[鍵] " + d.name, ChatFormatting.DARK_GRAY,
                        hint.isEmpty() ? "まだ解放されていません" : "依頼「" + hint + "」で解放",
                        "解放した人がリーダーなら、いっしょに挑めます"), null);
                continue;
            }
            b.button2(i, dungeonIcon(server, d, "左クリック: ロビーへ  右クリック: ランキング"), (pl, button) -> {
                if (button == 1) {
                    ranking(server, pl, id, com.chigusa.partydungeon.data.Difficulty.NORMAL, pg);
                    return;
                }
                if (!PartyManager.canLead(pl.getUUID())) {
                    Msg.warn(pl, "ダンジョンを選べるのはリーダーだけです。");
                    lobby(server, pl);
                    return;
                }
                Party party = PartyManager.getOrCreate(pl.getUUID());
                party.dungeonId = id;
                refreshLobbies(server, party);
                lobby(server, pl);
            });
        }
        if (list.isEmpty()) {
            b.button(22, icon(Items.BARRIER, "公開されているダンジョンがありません", ChatFormatting.RED), null);
        }
        pager(b, pg, pages, np -> dungeonList(server, p, np));
        b.button(49, back(), pl -> main(server, pl));
        b.open(p);
    }

    public static void lobby(MinecraftServer server, ServerPlayer p) {
        Party party = PartyManager.get(p.getUUID());
        boolean leader = party == null || party.leader.equals(p.getUUID());
        DungeonDef d = party == null || party.dungeonId == null ? null
                : PDData.get(server).dungeons.get(party.dungeonId);
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("ロビー"), 5).kind("lobby");

        // 選んだダンジョン
        if (d != null && d.published) {
            b.button(4, dungeonIcon(server, d, leader ? "クリックで選び直す" : "リーダーが選んだダンジョン"),
                    leader ? pl -> dungeonList(server, pl, 0) : null);
        } else {
            b.button(4, icon(Items.MAP, "ダンジョンが選ばれていません", ChatFormatting.GRAY,
                    leader ? "クリックで選ぶ" : "リーダーが選ぶのを待っています"),
                    leader ? pl -> dungeonList(server, pl, 0) : null);
        }

        // 難易度
        com.chigusa.partydungeon.data.Difficulty diff = party == null ? com.chigusa.partydungeon.data.Difficulty.NORMAL
                : party.difficulty;
        ItemStack diffIcon = icon(diff.icon, "難易度: " + diff.label, diff.color,
                "敵の体力 ×" + diff.health + "  攻撃 ×" + diff.damage + "  数 ×" + diff.count,
                leader ? "左クリック: 難しく  右クリック: やさしく" : "リーダーが選びます");
        if (leader) {
            b.button2(6, diffIcon, (pl, button) -> {
                Party pt = PartyManager.getOrCreate(pl.getUUID());
                pt.difficulty = button == 1 ? pt.difficulty.prev() : pt.difficulty.next();
                refreshLobbies(server, pt);
                if (!(pl.containerMenu instanceof ButtonMenu bm && bm.kind.equals("lobby"))) {
                    lobby(server, pl);
                }
            });
        } else {
            b.button(6, diffIcon, null);
        }

        // メンバーの顔
        List<UUID> members = party == null ? List.of(p.getUUID()) : new ArrayList<>(party.members);
        UUID leaderId = party == null ? p.getUUID() : party.leader;
        int slot = 18;
        for (UUID id : members) {
            if (slot > 26) {
                break;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            String name = PartyManager.nameOf(server, id) + (id.equals(leaderId) ? " ★リーダー" : "");
            List<String> lore = new ArrayList<>();
            if (sp == null) {
                lore.add("オフライン");
            }
            boolean canKick = leader && !id.equals(p.getUUID());
            if (canKick) {
                lore.add("右クリックでパーティから外す");
            }
            ItemStack icon = sp != null ? head(sp, name, lore.toArray(String[]::new))
                    : icon(Items.SKELETON_SKULL, name, ChatFormatting.GRAY, lore.toArray(String[]::new));
            if (canKick) {
                b.button2(slot, icon, (pl, button) -> {
                    if (button == 1) {
                        PartyManager.kick(server, pl, id);
                        refreshAll(server, pl, id);
                    }
                });
            } else {
                b.button(slot, icon, null);
            }
            slot++;
        }
        int max = PDConfig.MAX_PARTY_SIZE.get();
        for (int i = members.size(); i < max && slot <= 26; i++, slot++) {
            b.button(slot, icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, "空き", ChatFormatting.DARK_GRAY), null);
        }

        // 下の段
        b.button(36, back(), pl -> main(server, pl));
        if (leader) {
            b.button(38, icon(Items.WRITABLE_BOOK, "メンバーを招待", ChatFormatting.AQUA,
                    "オンラインの人から選んで招待します"), pl -> playerPicker(server, pl, 0));
            boolean rec = party != null && party.recruiting;
            b.button(40, icon(rec ? Items.GLOWSTONE : Items.BELL, rec ? "募集中（クリックでやめる）" : "仲間を募集する",
                    rec ? ChatFormatting.YELLOW : ChatFormatting.AQUA,
                    rec ? "「募集中のパーティ」から誰でも参加できます" : "サーバー全員に知らせます"), pl -> {
                        if (d == null) {
                            Msg.warn(pl, "先にダンジョンを選んでください。");
                            return;
                        }
                        PartyManager.setRecruiting(server, pl, !rec, d.name);
                        lobby(server, pl);
                    });
            int qpos = RunManager.queuePos(p.getUUID());
            if (qpos > 0) {
                b.button(42, icon(Items.CLOCK, "順番待ち中（" + qpos + " 番目）", ChatFormatting.YELLOW,
                        "部屋が空いたら自動で始まります", "クリックで順番待ちをやめる"), pl -> {
                            RunManager.cancelQueue(pl.getUUID());
                            Msg.info(pl, "順番待ちをやめました。");
                            lobby(server, pl);
                        });
            } else
            b.button(42, icon(Items.EMERALD_BLOCK, "スタート！", ChatFormatting.GREEN,
                    d == null ? "先にダンジョンを選んでください" : "パーティ全員で「" + d.name + "」に入ります"), pl -> {
                        if (d == null) {
                            Msg.warn(pl, "先にダンジョンを選んでください。");
                            return;
                        }
                        pl.closeContainer();
                        Party pt = PartyManager.get(pl.getUUID());
                        if (pt != null) {
                            pt.recruiting = false;
                            for (UUID id : pt.members) {
                                ServerPlayer m = server.getPlayerList().getPlayer(id);
                                if (m != null && m != pl && m.containerMenu instanceof ButtonMenu bm
                                        && bm.kind.equals("lobby")) {
                                    m.closeContainer();
                                }
                            }
                        }
                        if (!PDData.get(server).isUnlocked(pl.getUUID(), d)) {
                            Msg.warn(pl, "このダンジョンはまだ解放されていません。");
                            return;
                        }
                        RunManager.start(server, pl, d,
                                pt == null ? com.chigusa.partydungeon.data.Difficulty.NORMAL : pt.difficulty);
                    });
        } else {
            b.button(40, icon(Items.CLOCK, "リーダーのスタートを待っています", ChatFormatting.GRAY), null);
        }
        if (party != null && party.members.size() > 1) {
            b.button(44, icon(Items.OAK_DOOR, "パーティを抜ける", ChatFormatting.RED), pl -> {
                Party before = PartyManager.get(pl.getUUID());
                PartyManager.leave(server, pl.getUUID(), pl.getName().getString());
                Msg.info(pl, "パーティを抜けました。");
                if (before != null) {
                    refreshLobbies(server, before);
                }
                main(server, pl);
            });
        }
        b.open(p);
    }

    public static void playerPicker(MinecraftServer server, ServerPlayer p, int page) {
        Party party = PartyManager.getOrCreate(p.getUUID());
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp == p || party.members.contains(sp.getUUID()) || RunManager.isInRun(sp.getUUID())) {
                continue;
            }
            Party theirs = PartyManager.get(sp.getUUID());
            if (theirs != null && theirs.members.size() > 1) {
                continue;
            }
            list.add(sp);
        }
        int perPage = 45;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("招待する人を選ぶ"), 6);
        for (int i = 0; i < perPage && pg * perPage + i < list.size(); i++) {
            ServerPlayer target = list.get(pg * perPage + i);
            UUID tid = target.getUUID();
            b.button(i, head(target, target.getName().getString(), "クリックで招待"), pl -> {
                ServerPlayer t = server.getPlayerList().getPlayer(tid);
                if (t != null) {
                    PartyManager.invite(pl, t);
                }
                lobby(server, pl);
            });
        }
        if (list.isEmpty()) {
            b.button(22, icon(Items.BARRIER, "招待できる人がいません", ChatFormatting.GRAY,
                    "ほかのパーティに入っている人や、挑戦中の人は出ません"), null);
        }
        pager(b, pg, pages, np -> playerPicker(server, p, np));
        b.button(49, back(), pl -> lobby(server, pl));
        b.open(p);
    }

    public static void recruitBoard(MinecraftServer server, ServerPlayer p) {
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("募集中のパーティ"), 6).kind("board");
        List<Party> list = PartyManager.recruiting();
        int i = 0;
        for (Party party : list) {
            if (i >= 45) {
                break;
            }
            ServerPlayer leader = server.getPlayerList().getPlayer(party.leader);
            if (leader == null) {
                continue;
            }
            DungeonDef d = party.dungeonId == null ? null : PDData.get(server).dungeons.get(party.dungeonId);
            UUID lid = party.leader;
            b.button(i++, head(leader, leader.getName().getString() + " さんのパーティ",
                    "ダンジョン: " + (d == null ? "未定" : d.name),
                    "人数: " + party.members.size() + "/" + PDConfig.MAX_PARTY_SIZE.get(),
                    "クリックで参加"), pl -> {
                        if (PartyManager.joinRecruit(server, pl, lid)) {
                            Party joined = PartyManager.get(pl.getUUID());
                            if (joined != null) {
                                refreshLobbies(server, joined);
                            }
                            lobby(server, pl);
                        } else {
                            recruitBoard(server, pl);
                        }
                    });
        }
        if (i == 0) {
            b.button(22, icon(Items.BARRIER, "今は募集がありません", ChatFormatting.GRAY,
                    "ロビーの「仲間を募集する」で、自分から募集できます"), null);
        }
        b.button(49, back(), pl -> main(server, pl));
        b.open(p);
    }

    /** パーティのメンバーでロビー画面を開いている人の画面を、最新に開き直す */
    public static void refreshLobbies(MinecraftServer server, Party party) {
        for (UUID id : party.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null && sp.containerMenu instanceof ButtonMenu bm && bm.kind.equals("lobby")) {
                lobby(server, sp);
            }
        }
    }

    private static void refreshAll(MinecraftServer server, ServerPlayer leader, UUID kicked) {
        Party party = PartyManager.get(leader.getUUID());
        if (party != null) {
            refreshLobbies(server, party);
        }
        ServerPlayer k = server.getPlayerList().getPlayer(kicked);
        if (k != null && k.containerMenu instanceof ButtonMenu bm && bm.kind.equals("lobby")) {
            lobby(server, k);
        }
    }

    // ================================================================ 作る（OP）

    /** 製作メニュー（専用の画面） */
    public static void workshop(MinecraftServer server, ServerPlayer p) {
        if (!p.hasPermissions(2)) {
            return;
        }
        p.closeContainer();
        com.chigusa.partydungeon.net.ScreenActions.openManager(server, p, "");
    }

    /** 古い呼び出し口：製作メニューを開く */
    public static void editor(MinecraftServer server, ServerPlayer p, DungeonDef d) {
        p.closeContainer();
        com.chigusa.partydungeon.net.ScreenActions.openManager(server, p, d.id);
    }

    // ================================================================ 部品

    private static void withDef(MinecraftServer server, ServerPlayer p, String id, java.util.function.Consumer<DungeonDef> f) {
        DungeonDef d = PDData.get(server).dungeons.get(id);
        if (d == null) {
            Msg.warn(p, "そのダンジョンは見つかりません。");
            return;
        }
        f.accept(d);
        PDData.get(server).setDirty();
    }

    /** 区画の中での相対位置。区画の外なら null（メッセージを出す） */
    @Nullable
    private static BlockPos here(ServerPlayer p, DungeonDef d) {
        if (!WorkshopManager.isIn(p) || !d.containsWorld(p.blockPosition())) {
            Msg.warn(p, "「" + d.name + "」の区画の中に立ってから押してください。");
            return null;
        }
        return p.blockPosition().subtract(d.plotOrigin());
    }

    private static void pager(ButtonMenu.Builder b, int pg, int pages, java.util.function.IntConsumer go) {
        if (pg > 0) {
            b.button(45, icon(Items.ARROW, "前のページ", ChatFormatting.YELLOW), pl -> go.accept(pg - 1));
        }
        if (pg < pages - 1) {
            b.button(53, icon(Items.ARROW, "次のページ", ChatFormatting.YELLOW), pl -> go.accept(pg + 1));
        }
    }

    private static ItemStack back() {
        return icon(Items.SPRUCE_DOOR, "戻る", ChatFormatting.GRAY);
    }

    private static final Item[] RANK_ICONS = {Items.GOLD_BLOCK, Items.IRON_BLOCK, Items.COPPER_BLOCK,
            Items.OAK_PLANKS, Items.OAK_PLANKS};

    /** ランキング画面：1位〜5位を1段ずつ。左の数字の段にタイム、右にメンバーの顔 */
    public static void ranking(MinecraftServer server, ServerPlayer p, String dungeonId,
            com.chigusa.partydungeon.data.Difficulty diff, int backPage) {
        DungeonDef d = PDData.get(server).dungeons.get(dungeonId);
        if (d == null) {
            dungeonList(server, p, backPage);
            return;
        }
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("ランキング: " + d.name), 6);
        List<com.chigusa.partydungeon.data.RankEntry> list = PDData.get(server).ranking(d.id, diff.id);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm");
        for (int r = 0; r < com.chigusa.partydungeon.data.RankEntry.KEEP; r++) {
            int row = r * 9;
            if (r >= list.size()) {
                b.button(row, icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, (r + 1) + "位  ---", ChatFormatting.DARK_GRAY,
                        "まだ記録がありません"), null);
                continue;
            }
            com.chigusa.partydungeon.data.RankEntry e = list.get(r);
            ChatFormatting color = r == 0 ? ChatFormatting.GOLD : r == 1 ? ChatFormatting.WHITE
                    : r == 2 ? ChatFormatting.RED : ChatFormatting.GRAY;
            b.button(row, icon(RANK_ICONS[r], (r + 1) + "位  " + Msg.formatTime(e.ticks()), color,
                    e.ids().size() + " 人", fmt.format(new java.util.Date(e.when()))), null);
            b.button(row + 1, icon(Items.CLOCK, Msg.formatTime(e.ticks()), color), null);
            for (int m = 0; m < e.ids().size() && m < 5; m++) {
                String name = m < e.names().size() ? e.names().get(m) : "?";
                b.button(row + 3 + m, headOf(e.ids().get(m), name), null);
            }
        }
        b.button(45, back(), pl -> dungeonList(server, pl, backPage));
        b.button2(49, icon(diff.icon, "難易度: " + diff.label, diff.color, "左クリック: 次へ  右クリック: 前へ"),
                (pl, button) -> ranking(server, pl, dungeonId, button == 1 ? diff.prev() : diff.next(), backPage));
        b.open(p);
    }

    /** オフラインの人の顔も出せるように、ID と名前から顔を作る */
    private static ItemStack headOf(UUID id, String name) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, new ResolvableProfile(new com.mojang.authlib.GameProfile(id, name)));
        return named(stack, name, ChatFormatting.WHITE);
    }

    private static ItemStack dungeonIcon(MinecraftServer server, DungeonDef d, String action) {
        ItemStack stack = dungeonIcon(d, action);
        List<Component> lines = new ArrayList<>();
        ItemLore old = stack.get(DataComponents.LORE);
        if (old != null) {
            lines.addAll(old.lines());
        }
        List<Component> best = new ArrayList<>();
        for (com.chigusa.partydungeon.data.Difficulty diff : com.chigusa.partydungeon.data.Difficulty.values()) {
            List<com.chigusa.partydungeon.data.RankEntry> list = PDData.get(server).ranking(d.id, diff.id);
            if (list.isEmpty()) {
                continue;
            }
            com.chigusa.partydungeon.data.RankEntry top = list.get(0);
            best.add(Component.literal(" " + diff.label + " " + Msg.formatTime(top.ticks()) + "  "
                    + String.join("、", top.names())).withStyle(s -> s.withItalic(false).withColor(diff.color)));
        }
        if (!best.isEmpty()) {
            best.add(0, Component.literal("★ 1位の記録").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
            lines.addAll(Math.max(0, lines.size() - 1), best);
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    private static ItemStack dungeonIcon(DungeonDef d, String action) {
        ItemStack stack = new ItemStack(Items.FILLED_MAP);
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
        List<String> lore = new ArrayList<>();
        lore.add("クリア条件: " + d.clear.label);
        lore.add("広さ: " + d.size + "×" + d.size + "  高さ: " + d.height);
        int playing = RunManager.activeCount(d.id);
        if (playing > 0) {
            lore.add("挑戦中のパーティ: " + playing);
        }
        lore.add(action);
        return named(stack, d.name, ChatFormatting.GOLD, lore.toArray(String[]::new));
    }

    private static ItemStack mobIcon(@Nullable EntityType<?> type, ResourceLocation id, String... lore) {
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        ItemStack stack = new ItemStack(egg != null ? egg : Items.SPAWNER);
        String name = type == null ? id.toString() : type.getDescription().getString();
        List<String> l = new ArrayList<>();
        l.add(id.toString());
        l.addAll(List.of(lore));
        return named(stack, name, ChatFormatting.WHITE, l.toArray(String[]::new));
    }

    public static ItemStack icon(Item item, String name, ChatFormatting color, String... lore) {
        return named(new ItemStack(item), name, color, lore);
    }

    private static ItemStack head(ServerPlayer owner, String name, String... lore) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, new ResolvableProfile(owner.getGameProfile()));
        return named(stack, name, ChatFormatting.WHITE, lore);
    }

    private static ItemStack named(ItemStack stack, String name, ChatFormatting color, String... lore) {
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(name).withStyle(s -> s.withItalic(false).withColor(color)));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String l : lore) {
                lines.add(Component.literal(l).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)));
            }
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }
        return stack;
    }

    private Menus() {
    }
}
