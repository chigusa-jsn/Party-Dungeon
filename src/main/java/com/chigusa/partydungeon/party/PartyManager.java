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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * パーティの管理。パーティはサーバーを再起動すると解散する（保存しない）。
 * パーティに入っていない人は「1人パーティ」として扱う。
 */
public final class PartyManager {
    /** 招待の有効時間（tick） */
    private static final long INVITE_TICKS = 20 * 120;

    public static final class Party {
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();

        Party(UUID leader) {
            this.leader = leader;
            members.add(leader);
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

    /** そのプレイヤーがリーダーか、パーティに入っていない */
    public static boolean canLead(UUID player) {
        Party p = BY_PLAYER.get(player);
        return p == null || p.leader.equals(player);
    }

    /** 一緒に入場するメンバー（オンラインの人だけ。リーダーが先頭） */
    public static List<ServerPlayer> onlineMembers(MinecraftServer server, ServerPlayer leader) {
        List<ServerPlayer> list = new ArrayList<>();
        Party p = BY_PLAYER.get(leader.getUUID());
        if (p == null) {
            list.add(leader);
            return list;
        }
        list.add(leader);
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
        Party p = BY_PLAYER.get(from.getUUID());
        if (p != null && !p.leader.equals(from.getUUID())) {
            Msg.warn(from, "招待できるのはリーダーだけです。");
            return;
        }
        if (BY_PLAYER.containsKey(to.getUUID())) {
            Msg.warn(from, to.getName().getString() + " さんは、もう別のパーティに入っています。");
            return;
        }
        int size = p == null ? 1 : p.members.size();
        if (size >= PDConfig.MAX_PARTY_SIZE.get()) {
            Msg.warn(from, "パーティは最大 " + PDConfig.MAX_PARTY_SIZE.get() + " 人までです。");
            return;
        }
        INVITES.put(to.getUUID(), new Invite(from.getUUID(), from.serverLevel().getGameTime() + INVITE_TICKS));
        Msg.ok(from, to.getName().getString() + " さんを招待しました。");
        Msg.info(to, from.getName().getString() + " さんからパーティに招待されました。");
        Msg.info(to, "参加するなら /pd party accept 、断るなら /pd party decline （2分以内）");
    }

    public static void accept(MinecraftServer server, ServerPlayer player) {
        Invite inv = INVITES.remove(player.getUUID());
        if (inv == null || inv.expires < player.serverLevel().getGameTime()) {
            Msg.warn(player, "有効な招待がありません。");
            return;
        }
        if (BY_PLAYER.containsKey(player.getUUID())) {
            Msg.warn(player, "先に今のパーティを抜けてください（/pd party leave）。");
            return;
        }
        ServerPlayer leader = server.getPlayerList().getPlayer(inv.leader);
        if (leader == null) {
            Msg.warn(player, "招待した人がオフラインです。");
            return;
        }
        Party p = BY_PLAYER.get(inv.leader);
        if (p == null) {
            p = new Party(inv.leader);
            BY_PLAYER.put(inv.leader, p);
        }
        if (p.members.size() >= PDConfig.MAX_PARTY_SIZE.get()) {
            Msg.warn(player, "パーティが満員です。");
            return;
        }
        p.members.add(player.getUUID());
        BY_PLAYER.put(player.getUUID(), p);
        broadcast(server, p, player.getName().getString() + " さんがパーティに参加しました。（"
                + p.members.size() + "/" + PDConfig.MAX_PARTY_SIZE.get() + "）");
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
        if (p.members.size() == 1) {
            // 1人だけ残ったら解散
            UUID last = p.members.iterator().next();
            BY_PLAYER.remove(last);
            ServerPlayer sp = server.getPlayerList().getPlayer(last);
            if (sp != null) {
                Msg.info(sp, name + " さんが抜けたので、パーティは解散しました。");
            }
            return;
        }
        String leaderName = nameOf(server, p.leader);
        broadcast(server, p, name + " さんがパーティを抜けました。リーダー: " + leaderName);
    }

    public static void kick(MinecraftServer server, ServerPlayer leader, ServerPlayer target) {
        Party p = BY_PLAYER.get(leader.getUUID());
        if (p == null || !p.leader.equals(leader.getUUID())) {
            Msg.warn(leader, "外せるのはリーダーだけです。");
            return;
        }
        if (!p.members.contains(target.getUUID()) || target == leader) {
            Msg.warn(leader, "その人はパーティのメンバーではありません。");
            return;
        }
        Msg.info(target, "パーティから外されました。");
        leave(server, target.getUUID(), target.getName().getString());
    }

    public static void list(MinecraftServer server, ServerPlayer player) {
        Party p = BY_PLAYER.get(player.getUUID());
        if (p == null) {
            Msg.info(player, "パーティに入っていません（1人で挑戦できます）。/pd party invite <名前> で招待できます。");
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
            if (server.getPlayerList().getPlayer(id) == null) {
                sb.append("(オフライン)");
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

    private static String nameOf(MinecraftServer server, UUID id) {
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
