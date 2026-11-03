package com.chigusa.partydungeon.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDConfig;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * パーティの管理。パーティはサーバーを再起動すると解散する（保存しない）。
 * パーティに入っていない人は「1人パーティ」として扱う。
 * ロビー画面でダンジョンを選ぶと、1人でもパーティができる（そこに招待・募集できる）。
 */
public final class PartyManager {
    /** 招待の有効時間（tick） */
    private static final long INVITE_TICKS = 20 * 120;

    public static final class Party {
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
        /** ロビーで選んでいるダンジョン */
        @Nullable
        public String dungeonId;
        /** ロビーで選んでいる難易度 */
        public com.chigusa.partydungeon.data.Difficulty difficulty = com.chigusa.partydungeon.data.Difficulty.NORMAL;
        /** 仲間を募集中か */
        public boolean recruiting;

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
        }

        public boolean isFull() {
            return members.size() >= PDConfig.MAX_PARTY_SIZE.get();
        }
    }

    private record Invite(UUID leader, long expires) {
    }

    private static final Map<UUID, Party> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    @Nullable
    public static Party get(UUID player) {
        return BY_PLAYER.get(player);
    }

    /** パーティに入っていなければ、自分がリーダーの1人パーティを作る */
    public static Party getOrCreate(UUID player) {
        return BY_PLAYER.computeIfAbsent(player, Party::new);
    }

    /** そのプレイヤーがリーダーか、パーティに入っていない */
    public static boolean canLead(UUID player) {
        Party p = BY_PLAYER.get(player);
        return p == null || p.leader.equals(player);
    }

    /** 募集中のパーティ一覧 */
    public static List<Party> recruiting() {
        List<Party> list = new ArrayList<>();
        for (Party p : new LinkedHashSet<>(BY_PLAYER.values())) {
            if (p.recruiting && !p.isFull()) {
                list.add(p);
            }
        }
        return list;
    }

    /** 一緒に入場するメンバー（オンラインの人だけ。リーダーが先頭） */
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, ServerPlayer leader) {
        List<ServerPlayer> list = new ArrayList<>();
        list.add(leader);
        Party p = BY_PLAYER.get(leader.getUUID());
        if (p == null) {
            return list;
        }
        for (UUID id : p.members) {
            if (id.equals(leader.getUUID())) {
                continue;
            }
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                list.add(sp);
            }
        }
        return list;
    }

    public static void invite(ServerPlayer from, ServerPlayer to) {
        if (from == to) {
            Msg.warn(from, "自分は招待できません。");
            return;
        }
        Party p = getOrCreate(from.getUUID());
        if (!p.leader.equals(from.getUUID())) {
            Msg.warn(from, "招待できるのはリーダーだけです。");
            return;
        }
        Party theirs = BY_PLAYER.get(to.getUUID());
        if (theirs != null && theirs.members.size() > 1) {
            Msg.warn(from, to.getName().getString() + " さんは、もう別のパーティに入っています。");
            return;
        }
        if (p.isFull()) {
            Msg.warn(from, "パーティは最大 " + PDConfig.MAX_PARTY_SIZE.get() + " 人までです。");
            return;
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), from.serverLevel().getGameTime() + INVITE_TICKS));
        Msg.ok(from, to.getName().getString() + " さんを招待しました。");
        to.sendSystemMessage(Msg.info(from.getName().getString() + " さんからパーティに招待されました。 ")
                .copy()
                .append(button("[参加する]", "/pd party accept", ChatFormatting.GREEN))
                .append(" ")
                .append(button("[断る]", "/pd party decline", ChatFormatting.RED)));
    }

    /** クリックするとコマンドが実行されるチャットのボタン */
    public static MutableComponent button(String label, String command, ChatFormatting color) {
        return Component.literal(label).withStyle(s -> s.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("クリック"))));
    }

    public static boolean accept(MinecraftServer server, ServerPlayer player) {
        Invite inv = INVITES.remove(player.getUUID());
        if (inv == null || inv.expires < player.serverLevel().getGameTime()) {
            Msg.warn(player, "有効な招待がありません。");
            return false;
        }
        return joinParty(server, player, inv.leader);
    }

    /** 募集中のパーティに参加する */
    public static boolean joinRecruit(MinecraftServer server, ServerPlayer player, UUID leader) {
        Party p = BY_PLAYER.get(leader);
        if (p == null || !p.recruiting || !p.leader.equals(leader)) {
            Msg.warn(player, "そのパーティは、もう募集していません。");
            return false;
        }
        return joinParty(server, player, leader);
    }

    private static boolean joinParty(MinecraftServer server, ServerPlayer player, UUID leader) {
        Party mine = BY_PLAYER.get(player.getUUID());
        if (mine != null && mine.members.size() > 1) {
            Msg.warn(player, "先に今のパーティを抜けてください。");
            return false;
        }
        if (server.getPlayerList().getPlayer(leader) == null) {
            Msg.warn(player, "リーダーがオフラインです。");
            return false;
        }
        Party p = getOrCreate(leader);
        if (p.members.contains(player.getUUID())) {
            return true;
        }
        if (p.isFull()) {
            Msg.warn(player, "パーティが満員です。");
            return false;
        }
        if (mine != null) {
            BY_PLAYER.remove(player.getUUID());
        }
        p.members.add(player.getUUID());
        BY_PLAYER.put(player.getUUID(), p);
        broadcast(server, p, player.getName().getString() + " さんがパーティに参加しました。（"
                + p.members.size() + "/" + PDConfig.MAX_PARTY_SIZE.get() + "）");
        if (p.isFull()) {
            p.recruiting = false;
        }
        return true;
    }

    public static void decline(ServerPlayer player) {
        if (INVITES.remove(player.getUUID()) != null) {
            Msg.info(player, "招待を断りました。");
        } else {
            Msg.warn(player, "招待はありません。");
        }
    }

    public static void leave(MinecraftServer server, UUID playerId, String name) {
        Party p = BY_PLAYER.remove(playerId);
        if (p == null) {
            return;
        }
        p.members.remove(playerId);
        if (p.members.isEmpty()) {
            return;
        }
        if (p.leader.equals(playerId)) {
            p.leader = p.members.iterator().next();
        }
        String leaderName = nameOf(server, p.leader);
        broadcast(server, p, name + " さんがパーティを抜けました。リーダー: " + leaderName);
    }

    public static void kick(MinecraftServer server, ServerPlayer leader, UUID target) {
        Party p = BY_PLAYER.get(leader.getUUID());
        if (p == null || !p.leader.equals(leader.getUUID())) {
            Msg.warn(leader, "外せるのはリーダーだけです。");
            return;
        }
        if (!p.members.contains(target) || target.equals(leader.getUUID())) {
            Msg.warn(leader, "その人はパーティのメンバーではありません。");
            return;
        }
        ServerPlayer sp = server.getPlayerList().getPlayer(target);
        if (sp != null) {
            Msg.info(sp, "パーティから外されました。");
        }
        leave(server, target, nameOf(server, target));
    }

    /** 募集を始める／やめる。始めたときはサーバー全員に知らせる */
    public static void setRecruiting(MinecraftServer server, ServerPlayer leader, boolean on, String dungeonName) {
        Party p = getOrCreate(leader.getUUID());
        if (!p.leader.equals(leader.getUUID())) {
            Msg.warn(leader, "募集できるのはリーダーだけです。");
            return;
        }
        p.recruiting = on;
        if (!on) {
            Msg.info(leader, "募集をやめました。");
            return;
        }
        Component msg = Msg.info(leader.getName().getString() + " さんが「" + dungeonName + "」の仲間を募集中！（"
                + p.members.size() + "/" + PDConfig.MAX_PARTY_SIZE.get() + "） ").copy()
                .append(button("[参加する]", "/pd join " + leader.getName().getString(), ChatFormatting.GREEN));
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!p.members.contains(sp.getUUID())) {
                sp.sendSystemMessage(msg);
            }
        }
        Msg.ok(leader, "募集を始めました。サーバーのみんなに知らせました。");
    }

    public static void list(MinecraftServer server, ServerPlayer player) {
        Party p = BY_PLAYER.get(player.getUUID());
        if (p == null) {
            Msg.info(player, "パーティに入っていません（1人で挑戦できます）。");
            return;
        }
        StringBuilder sb = new StringBuilder("パーティ（" + p.members.size() + "/" + PDConfig.MAX_PARTY_SIZE.get() + "）: ");
        boolean first = true;
        for (UUID id : p.members) {
            if (!first) {
                sb.append("、");
            }
            first = false;
            sb.append(nameOf(server, id));
            if (id.equals(p.leader)) {
                sb.append("★");
            }
        }
        Msg.info(player, sb.toString());
    }

    public static void broadcast(MinecraftServer server, Party p, String text) {
        for (UUID id : p.members) {
            ServerPlayer sp = server.getPlayerList().getPlayer(id);
            if (sp != null) {
                Msg.info(sp, text);
            }
        }
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) {
            return sp.getName().getString();
        }
        return server.getProfileCache() == null ? "?"
                : server.getProfileCache().get(id).map(g -> g.getName()).orElse("?");
    }

    public static void reset() {
        BY_PLAYER.clear();
        INVITES.clear();
    }

    private PartyManager() {
    }
}
