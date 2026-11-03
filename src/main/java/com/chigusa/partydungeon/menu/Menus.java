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
        for (int i = 0; i < perPage && pg * perPage + i < list.size(); i++) {
            DungeonDef d = list.get(pg * perPage + i);
            String id = d.id;
            b.button(i, dungeonIcon(d, "クリックでロビーへ"), pl -> {
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
            b.button(4, dungeonIcon(d, leader ? "クリックで選び直す" : "リーダーが選んだダンジョン"),
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

    /** 製作メニュー：区画の中なら編集画面、外なら一覧 */
    public static void workshop(MinecraftServer server, ServerPlayer p) {
        if (!p.hasPermissions(2)) {
            return;
        }
        DungeonDef d = WorkshopManager.isIn(p) ? PDData.get(server).findByPlotPos(p.blockPosition()) : null;
        if (d != null) {
            editor(server, p, d);
        } else {
            buildList(server, p, 0);
        }
    }

    public static void buildList(MinecraftServer server, ServerPlayer p, int page) {
        List<DungeonDef> list = new ArrayList<>(PDData.get(server).dungeons.values());
        int perPage = 36;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("製作メニュー"), 6);
        b.button(4, icon(Items.EMERALD, "新しいダンジョンを作る", ChatFormatting.GREEN,
                "名前 → 広さ → 高さ を選ぶと、外枠が自動で建ちます"), pl ->
                        ChatInput.ask(pl, "新しいダンジョンの名前を、チャットに入力してください。", name -> {
                            if (PDData.get(server).findByName(name) != null) {
                                Msg.warn(pl, "その名前はもう使われています。");
                                return;
                            }
                            sizeMenu(server, pl, name);
                        }));
        for (int i = 0; i < perPage && pg * perPage + i < list.size(); i++) {
            DungeonDef d = list.get(pg * perPage + i);
            String id = d.id;
            ItemStack ic = icon(d.published ? Items.FILLED_MAP : Items.MAP, d.name,
                    d.published ? ChatFormatting.GOLD : ChatFormatting.GRAY,
                    d.published ? "公開中（第" + d.revision + "版）" : "未公開",
                    d.size + "×" + d.size + " 高さ" + d.height,
                    "クリックでこの区画へ移動");
            b.button(9 + i, ic, pl -> {
                DungeonDef now = PDData.get(server).dungeons.get(id);
                if (now == null) {
                    return;
                }
                if (!WorkshopManager.isIn(pl)) {
                    WorkshopManager.enter(server, pl);
                }
                BlockPos o = now.plotOrigin();
                pl.teleportTo(WorkshopManager.workshop(server), o.getX() + now.size / 2.0, o.getY() + 1,
                        o.getZ() + now.size / 2.0, pl.getYRot(), 0);
                editor(server, pl, now);
            });
        }
        pager(b, pg, pages, np -> buildList(server, p, np));
        b.button(49, back(), pl -> main(server, pl));
        b.open(p);
    }

    private static void sizeMenu(MinecraftServer server, ServerPlayer p, String name) {
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("「" + name + "」の広さ"), 3);
        for (int i = 0; i < SIZES.length; i++) {
            int size = SIZES[i];
            b.button(10 + i, icon(Items.GRASS_BLOCK, size + " × " + size, ChatFormatting.GREEN,
                    size <= 32 ? "小さめ" : size <= 64 ? "ふつう" : "大きめ"), pl -> heightMenu(server, pl, name, size));
        }
        b.button(22, back(), pl -> buildList(server, pl, 0));
        b.open(p);
    }

    private static void heightMenu(MinecraftServer server, ServerPlayer p, String name, int size) {
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("「" + name + "」の高さ"), 3);
        for (int i = 0; i < HEIGHTS.length; i++) {
            int h = HEIGHTS[i];
            b.button(9 + i, icon(Items.LADDER, "高さ " + h, ChatFormatting.GREEN), pl -> {
                pl.closeContainer();
                if (!WorkshopManager.isIn(pl)) {
                    WorkshopManager.enter(server, pl);
                }
                WorkshopManager.create(server, pl, name, size, h, Blocks.STONE_BRICKS.defaultBlockState());
            });
        }
        b.button(22, back(), pl -> sizeMenu(server, pl, name));
        b.open(p);
    }

    public static void editor(MinecraftServer server, ServerPlayer p, DungeonDef d) {
        String id = d.id;
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("編集: " + d.name), 5).kind("editor");
        String problem = d.validate();
        b.button(4, icon(Items.PAPER, d.name, ChatFormatting.GOLD,
                d.size + "×" + d.size + " 高さ" + d.height,
                d.published ? "公開中（第" + d.revision + "版）" : "未公開",
                "敵: " + d.spawns.size() + " か所",
                problem == null ? "✔ 公開できます" : "✘ " + problem), null);

        b.button(19, icon(Items.LIME_BED, "スタート地点をここに", ChatFormatting.GREEN,
                "今立っている場所をスタートにします", "目印: 緑のキラキラ"), pl -> withDef(server, pl, id, def -> {
                    if (here(pl, def) == null) {
                        return;
                    }
                    def.start = here(pl, def);
                    Msg.ok(pl, "スタート地点をここにしました。");
                    editor(server, pl, def);
                }));
        b.button2(20, icon(Items.BEACON, "ゴール地点をここに（半径 " + d.goalRadius + "）", ChatFormatting.WHITE,
                "左クリック: 今立っている場所をゴールに", "右クリック: 半径を変える", "目印: 白い光"),
                (pl, button) -> withDef(server, pl, id, def -> {
                    if (button == 1) {
                        def.goalRadius = def.goalRadius >= 8 ? 1 : def.goalRadius + 1;
                    } else {
                        BlockPos h = here(pl, def);
                        if (h == null) {
                            return;
                        }
                        def.goal = h;
                        Msg.ok(pl, "ゴール地点をここにしました。");
                    }
                    editor(server, pl, def);
                }));
        b.button(21, icon(Items.ZOMBIE_HEAD, "敵をここに置く", ChatFormatting.RED,
                "モブの一覧から選びます（ほかのMODのモブも出ます）"), pl -> mobPicker(server, pl, id, 0, null));
        b.button(22, icon(Items.BOOK, "置いた敵の一覧", ChatFormatting.YELLOW,
                d.spawns.size() + " か所", "移動したり消したりできます"), pl -> spawnList(server, pl, id, 0));
        b.button(24, icon(Items.TARGET, "クリア条件: " + d.clear.label, ChatFormatting.AQUA,
                "クリックで切り替え", "敵を全部倒す → ボスを全部倒す → ゴール地点に着く"),
                pl -> withDef(server, pl, id, def -> {
                    // 順番: 全部 → ボス → ゴール
                    def.clear = switch (def.clear) {
                        case ALL -> ClearCondition.BOSS;
                        case BOSS -> ClearCondition.GOAL;
                        case GOAL -> ClearCondition.ALL;
                    };
                    editor(server, pl, def);
                }));
        b.button(23, icon(Items.CHEST, "報酬", ChatFormatting.GOLD,
                "難易度ごとに、クリア報酬を入れます", "アイテムを入れるだけ。確率も決められます"),
                pl -> RewardMenu.open(server, pl, id, com.chigusa.partydungeon.data.Difficulty.NORMAL));
        b.button(25, icon(Items.NAME_TAG, "名前を変える", ChatFormatting.WHITE), pl ->
                ChatInput.ask(pl, "新しい名前を、チャットに入力してください。", name -> withDef(server, pl, id, def -> {
                    if (PDData.get(server).findByName(name) != null) {
                        Msg.warn(pl, "その名前はもう使われています。");
                        return;
                    }
                    def.name = name;
                    Msg.ok(pl, "名前を「" + name + "」に変えました。");
                    editor(server, pl, def);
                })));

        b.button(29, icon(Items.ENCHANTED_BOOK, "公開する", ChatFormatting.GOLD,
                "今の建物と設定で公開します", "直したあとも、もう一度押せば反映されます"),
                pl -> withDef(server, pl, id, def -> {
                    WorkshopManager.publish(server, pl, def);
                    editor(server, pl, def);
                }));
        b.button(31, icon(Items.ENDER_EYE, "公開してテストプレイ", ChatFormatting.LIGHT_PURPLE,
                "公開して、すぐに自分で挑戦します", "終わると作業場のこの場所に戻ります"),
                pl -> withDef(server, pl, id, def -> {
                    pl.closeContainer();
                    WorkshopManager.publish(server, pl, def);
                    if (def.published && def.validate() == null) {
                        RunManager.start(server, pl, def);
                    }
                }));
        if (d.published) {
            b.button(33, icon(Items.GRAY_DYE, "非公開にする", ChatFormatting.GRAY, "一覧に出なくなります（建物は残ります）"),
                    pl -> withDef(server, pl, id, def -> {
                        def.published = false;
                        Msg.ok(pl, "非公開にしました。");
                        editor(server, pl, def);
                    }));
        }
        b.button(36, back(), pl -> main(server, pl));
        b.button(44, icon(Items.CHEST, "区画の一覧へ", ChatFormatting.YELLOW), pl -> buildList(server, pl, 0));
        b.open(p);
    }

    // ---------------------------------------------------------------- 敵

    private record MobEntry(ResourceLocation id, EntityType<?> type) {
    }

    private static List<MobEntry> allMobs(@Nullable String filter) {
        List<MobEntry> list = new ArrayList<>();
        String f = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type == EntityType.PLAYER) {
                continue;
            }
            boolean living = type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type) != null
                    || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type == EntityType.IRON_GOLEM
                    || type == EntityType.SNOW_GOLEM;
            if (!living || !type.canSummon()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (f != null && !id.toString().contains(f)
                    && !type.getDescription().getString().toLowerCase(Locale.ROOT).contains(f)) {
                continue;
            }
            list.add(new MobEntry(id, type));
        }
        // バニラが先、そのあと MOD ごと
        list.sort(Comparator.comparing((MobEntry e) -> !e.id.getNamespace().equals("minecraft"))
                .thenComparing(e -> e.id.toString()));
        return list;
    }

    public static void mobPicker(MinecraftServer server, ServerPlayer p, String defId, int page, @Nullable String filter) {
        List<MobEntry> list = allMobs(filter);
        int perPage = 45;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal(
                "敵を選ぶ" + (filter == null ? "" : "「" + filter + "」") + " " + (pg + 1) + "/" + pages), 6);
        for (int i = 0; i < perPage && pg * perPage + i < list.size(); i++) {
            MobEntry e = list.get(pg * perPage + i);
            b.button(i, mobIcon(e.type, e.id, "クリックで選ぶ"), pl -> spawnConfig(server, pl, defId, e.id, 1, 0, false));
        }
        if (list.isEmpty()) {
            b.button(22, icon(Items.BARRIER, "見つかりません", ChatFormatting.GRAY), null);
        }
        pager(b, pg, pages, np -> mobPicker(server, p, defId, np, filter));
        b.button(47, back(), pl -> withDef(server, pl, defId, def -> editor(server, pl, def)));
        b.button(49, icon(Items.COMPASS, "名前で探す", ChatFormatting.AQUA, "例: zombie、ゾンビ、modの名前"),
                pl -> ChatInput.ask(pl, "探したいモブの名前（英語のID か 表示名の一部）を入力してください。",
                        text -> mobPicker(server, pl, defId, 0, text)));
        if (filter != null) {
            b.button(51, icon(Items.BUCKET, "検索をやめる", ChatFormatting.GRAY), pl -> mobPicker(server, pl, defId, 0, null));
        }
        b.open(p);
    }

    private static void spawnConfig(MinecraftServer server, ServerPlayer p, String defId, ResourceLocation mob,
            int count, int distIndex, boolean boss) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(mob).orElse(null);
        if (type == null) {
            return;
        }
        int dist = DISTANCES[distIndex];
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("敵の設定"), 3);
        b.button(4, mobIcon(type, mob), null);
        b.button2(10, icon(Items.SLIME_BALL, "数: " + count, ChatFormatting.WHITE, "左クリック: 増やす", "右クリック: 減らす"),
                (pl, button) -> spawnConfig(server, pl, defId, mob,
                        Math.max(1, Math.min(64, count + (button == 1 ? -1 : 1))), distIndex, boss));
        b.button2(12, icon(Items.CLOCK, dist == 0 ? "出るタイミング: 開始と同時" : "出るタイミング: " + dist + " ブロックまで近づいたら",
                ChatFormatting.WHITE, "左クリック: 遠く", "右クリック: 近く"),
                (pl, button) -> spawnConfig(server, pl, defId, mob, count,
                        Math.max(0, Math.min(DISTANCES.length - 1, distIndex + (button == 1 ? -1 : 1))), boss));
        b.button(14, icon(boss ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL,
                boss ? "ボス: はい" : "ボス: いいえ", boss ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY,
                "クリックで切り替え", "クリア条件「ボスを全部倒す」で使います"),
                pl -> spawnConfig(server, pl, defId, mob, count, distIndex, !boss));
        b.button(16, icon(Items.LIME_CONCRETE, "今立っている場所に置く", ChatFormatting.GREEN),
                pl -> withDef(server, pl, defId, def -> {
                    BlockPos h = here(pl, def);
                    if (h == null) {
                        return;
                    }
                    def.spawns.add(new SpawnPoint(h, mob, count, dist, boss));
                    Msg.ok(pl, (boss ? "ボス " : "敵 ") + type.getDescription().getString() + " ×" + count + " を置きました。");
                    editor(server, pl, def);
                }));
        b.button(22, back(), pl -> mobPicker(server, pl, defId, 0, null));
        b.open(p);
    }

    public static void spawnList(MinecraftServer server, ServerPlayer p, String defId, int page) {
        DungeonDef d = PDData.get(server).dungeons.get(defId);
        if (d == null) {
            return;
        }
        int perPage = 45;
        int pages = Math.max(1, (d.spawns.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(page, pages - 1));
        ButtonMenu.Builder b = new ButtonMenu.Builder(Component.literal("置いた敵（" + d.spawns.size() + "）"), 6);
        for (int i = 0; i < perPage && pg * perPage + i < d.spawns.size(); i++) {
            SpawnPoint s = d.spawns.get(pg * perPage + i);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.entity()).orElse(null);
            String when = s.triggerRadius() <= 0 ? "開始と同時" : s.triggerRadius() + " ブロックまで近づいたら";
            ItemStack ic = mobIcon(type, s.entity(),
                    (s.boss() ? "ボス  " : "") + "×" + s.count() + "  " + when,
                    "左クリック: その場所へ移動", "右クリック: 消す");
            b.button2(i, ic, (pl, button) -> withDef(server, pl, defId, def -> {
                if (button == 1) {
                    def.spawns.remove(s);
                    Msg.ok(pl, "消しました。");
                    spawnList(server, pl, defId, pg);
                } else {
                    BlockPos w = def.plotOrigin().offset(s.rel());
                    pl.closeContainer();
                    pl.teleportTo(WorkshopManager.workshop(server), w.getX() + 0.5, w.getY(), w.getZ() + 0.5,
                            pl.getYRot(), pl.getXRot());
                }
            }));
        }
        if (d.spawns.isEmpty()) {
            b.button(22, icon(Items.BARRIER, "まだ敵がいません", ChatFormatting.GRAY), null);
        }
        pager(b, pg, pages, np -> spawnList(server, p, defId, np));
        b.button(49, back(), pl -> withDef(server, pl, defId, def -> editor(server, pl, def)));
        b.open(p);
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
