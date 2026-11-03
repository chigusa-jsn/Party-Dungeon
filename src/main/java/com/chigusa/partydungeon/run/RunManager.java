package com.chigusa.partydungeon.run;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDConfig;
import com.chigusa.partydungeon.PDDimensions;
import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.ClearCondition;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.ReturnInfo;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.party.PartyManager;
import com.chigusa.partydungeon.template.DungeonTemplate;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * 挑戦用の部屋と、挑戦（ラン）の管理。
 *
 * 流れ: 空き部屋を確保 → 設計図から少しずつ組み立て → 全員をテレポート → 敵を出す
 * → クリア判定 → 数秒後に元の場所へ戻す → 部屋を片付けて空きに戻す
 */
public final class RunManager {

    public enum State {
        PREPARING, ACTIVE, CLEARED
    }

    /** 共用の部屋 */
    public static final class Room {
        public final int index;
        public final BlockPos origin;
        @Nullable
        public Run run;
        /** 片付け中 */
        public boolean cleaning;

        Room(int index) {
            this.index = index;
            this.origin = PDDimensions.roomOrigin(index);
        }

        public boolean isFree() {
            return run == null && !cleaning;
        }
    }

    /** 出現ポイント1つぶんの状態 */
    static final class SpawnState {
        final SpawnPoint point;
        boolean triggered;
        final List<UUID> mobs = new ArrayList<>();

        SpawnState(SpawnPoint point) {
            this.point = point;
        }
    }

    /** 1回の挑戦 */
    public static final class Run {
        public final DungeonDef def;
        final DungeonTemplate tpl;
        public final Room room;
        public final Set<UUID> members = new LinkedHashSet<>();
        public State state = State.PREPARING;
        long startTick;
        long clearTick;
        long returnTick;
        final List<SpawnState> spawns = new ArrayList<>();
        final Set<UUID> deadMobs = new java.util.HashSet<>();
        final Map<UUID, BlockPos> mobLastPos = new HashMap<>();

        Run(DungeonDef def, DungeonTemplate tpl, Room room) {
            this.def = def;
            this.tpl = tpl;
            this.room = room;
            for (SpawnPoint sp : def.spawns) {
                spawns.add(new SpawnState(sp));
            }
        }

        BlockPos world(BlockPos rel) {
            return room.origin.offset(rel);
        }

        AABB box() {
            BlockPos o = room.origin;
            return new AABB(o.getX(), o.getY(), o.getZ(),
                    o.getX() + tpl.sizeX, o.getY() + tpl.sizeY, o.getZ() + tpl.sizeZ);
        }
    }

    private static final List<Room> ROOMS = new ArrayList<>();
    private static final Map<UUID, Run> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, Run> BY_MOB = new HashMap<>();
    /** 死んだときに預かった持ち物 */
    private static final Map<UUID, net.minecraft.nbt.ListTag> SAVED_INV = new HashMap<>();
    private static final Map<UUID, int[]> SAVED_XP = new HashMap<>();
    private static final Map<UUID, Float> SAVED_XP_PROGRESS = new HashMap<>();
    private static int tickCounter;

    // ------------------------------------------------------------------ 参照

    @Nullable
    public static Run runOf(UUID player) {
        return BY_PLAYER.get(player);
    }

    public static boolean isInRun(UUID player) {
        return BY_PLAYER.containsKey(player);
    }

    public static List<Room> rooms() {
        ensureRooms();
        return ROOMS;
    }

    public static ServerLevel arena(MinecraftServer server) {
        return server.getLevel(PDDimensions.ARENA);
    }

    private static void ensureRooms() {
        int want = PDConfig.ROOM_COUNT.get();
        while (ROOMS.size() < want) {
            ROOMS.add(new Room(ROOMS.size()));
        }
    }

    public static int activeCount(String dungeonId) {
        int n = 0;
        for (Room r : rooms()) {
            if (r.run != null && r.run.def.id.equals(dungeonId)) {
                n++;
            }
        }
        return n;
    }

    public static int freeRooms() {
        int n = 0;
        int max = PDConfig.ROOM_COUNT.get();
        for (Room r : rooms()) {
            if (r.index < max && r.isFree()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 開始

    public static void start(MinecraftServer server, ServerPlayer leader, DungeonDef stored) {
        if (!PartyManager.canLead(leader.getUUID())) {
            Msg.warn(leader, "ダンジョンを選べるのはパーティのリーダーだけです。");
            return;
        }
        if (!stored.published) {
            Msg.warn(leader, "このダンジョンはまだ公開されていません。");
            return;
        }
        PDData data = PDData.get(server);
        List<ServerPlayer> members = PartyManager.onlineMembers(server, leader);
        for (ServerPlayer p : members) {
            if (isInRun(p.getUUID())) {
                Msg.warn(leader, p.getName().getString() + " さんは、もうダンジョンに入っています。");
                return;
            }
            if (data.returns.containsKey(p.getUUID()) || p.level().dimension() == PDDimensions.WORKSHOP) {
                Msg.warn(leader, p.getName().getString() + " さんが作業場にいます。先に /pd exit で出てもらってください。");
                return;
            }
        }
        int cap = PDConfig.ROOMS_PER_DUNGEON.get();
        if (cap > 0 && activeCount(stored.id) >= cap) {
            Msg.warn(leader, "このダンジョンは今、同時に遊べる上限（" + cap + "部屋）に達しています。少し待ってください。");
            return;
        }
        Room room = null;
        int max = PDConfig.ROOM_COUNT.get();
        for (Room r : rooms()) {
            if (r.index < max && r.isFree()) {
                room = r;
                break;
            }
        }
        if (room == null) {
            Msg.warn(leader, "全部の部屋が使用中です。少し待ってから、もう一度選んでください。");
            return;
        }
        DungeonTemplate tpl = DungeonTemplate.load(server, stored.id);
        if (tpl == null) {
            Msg.warn(leader, "ダンジョンの設計図が見つかりません。作った人に、もう一度公開（/pd publish）してもらってください。");
            return;
        }
        ServerLevel arena = arena(server);
        if (arena == null) {
            Msg.warn(leader, "挑戦用のディメンションが読み込めません。");
            return;
        }

        Run run = new Run(stored.copy(), tpl, room);
        room.run = run;
        for (ServerPlayer p : members) {
            run.members.add(p.getUUID());
            BY_PLAYER.put(p.getUUID(), run);
            Msg.info(p, "「" + stored.name + "」の部屋 " + (room.index + 1) + " 番を準備しています…");
        }
        data.dirtyRooms.put(room.index, new int[] {tpl.sizeX, tpl.sizeY, tpl.sizeZ});
        data.setDirty();

        final Room fRoom = room;
        BlockJobs.add(BlockJobs.place(arena, room.origin, tpl, () -> begin(server, run, fRoom)));
    }

    private static void begin(MinecraftServer server, Run run, Room room) {
        ServerLevel arena = arena(server);
        // 前回の残りの敵やアイテムを消す
        discardEntities(arena, run.box());

        PDData data = PDData.get(server);
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : run.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                online.add(p);
            }
        }
        if (online.isEmpty()) {
            finish(server, run, false);
            return;
        }
        BlockPos start = run.world(run.def.start);
        for (ServerPlayer p : online) {
            data.returns.put(p.getUUID(), ReturnInfo.of(p));
            p.teleportTo(arena, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, p.getYRot(), 0);
            p.setGameMode(GameType.ADVENTURE);
            Msg.ok(p, "「" + run.def.name + "」開始！ クリア条件: " + run.def.clear.label);
        }
        data.setDirty();
        run.state = State.ACTIVE;
        run.startTick = server.getTickCount();
        for (SpawnState s : run.spawns) {
            if (s.point.triggerRadius() <= 0) {
                spawn(arena, run, s);
            }
        }
        PartyDungeon.LOGGER.info("[Party Dungeon] 部屋{} 「{}」開始 {}人", room.index + 1, run.def.name, online.size());
    }

    // ------------------------------------------------------------------ 毎tick

    public static void tick(MinecraftServer server) {
        BlockJobs.tick();
        if (++tickCounter % 10 != 0) {
            return;
        }
        ServerLevel arena = arena(server);
        if (arena == null) {
            return;
        }
        long now = server.getTickCount();
        for (Room room : rooms()) {
            Run run = room.run;
            if (run == null) {
                continue;
            }
            if (run.state == State.ACTIVE) {
                tickActive(server, arena, run, now);
            } else if (run.state == State.CLEARED && now >= run.returnTick) {
                finish(server, run, true);
            }
        }
    }

    private static void tickActive(MinecraftServer server, ServerLevel arena, Run run, long now) {
        List<ServerPlayer> online = onlineMembers(server, run);
        if (online.isEmpty()) {
            finish(server, run, false);
            return;
        }
        // 近づいたら出る敵
        for (SpawnState s : run.spawns) {
            if (s.triggered) {
                continue;
            }
            BlockPos at = run.world(s.point.rel());
            double r2 = (double) s.point.triggerRadius() * s.point.triggerRadius();
            for (ServerPlayer p : online) {
                if (p.level() == arena && p.blockPosition().distSqr(at) <= r2) {
                    spawn(arena, run, s);
                    break;
                }
            }
        }
        if (isCleared(arena, run, online)) {
            PartyDungeon.LOGGER.info("[Party Dungeon] 部屋{} 「{}」クリア", run.room.index + 1, run.def.name);
            run.state = State.CLEARED;
            run.clearTick = now;
            int delay = PDConfig.RETURN_DELAY_SECONDS.get();
            run.returnTick = now + delay * 20L;
            String time = Msg.formatTicks(now - run.startTick);
            for (ServerPlayer p : online) {
                Msg.ok(p, "★ クリア！ タイム " + time + "  " + delay + " 秒後に元の場所へ戻ります。");
                p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1f, 1f);
            }
            // 残っている敵は片付ける
            for (SpawnState s : run.spawns) {
                for (UUID id : s.mobs) {
                    Entity e = arena.getEntity(id);
                    if (e != null && e.isAlive()) {
                        e.discard();
                    }
                }
            }
        }
    }

    private static boolean isCleared(ServerLevel arena, Run run, List<ServerPlayer> online) {
        ClearCondition c = run.def.clear;
        if (c == ClearCondition.GOAL) {
            if (run.def.goal == null) {
                return false;
            }
            BlockPos goal = run.world(run.def.goal);
            double r2 = (double) run.def.goalRadius * run.def.goalRadius + 0.5;
            for (ServerPlayer p : online) {
                if (p.level() == arena && p.blockPosition().distSqr(goal) <= r2) {
                    return true;
                }
            }
            return false;
        }
        boolean any = false;
        for (SpawnState s : run.spawns) {
            if (c == ClearCondition.BOSS && !s.point.boss()) {
                continue;
            }
            any = true;
            if (!s.triggered) {
                return false;
            }
            for (UUID id : s.mobs) {
                if (!isDead(arena, run, id)) {
                    return false;
                }
            }
        }
        return any;
    }

    private static boolean isDead(ServerLevel arena, Run run, UUID id) {
        if (run.deadMobs.contains(id)) {
            return true;
        }
        Entity e = arena.getEntity(id);
        if (e != null) {
            run.mobLastPos.put(id, e.blockPosition());
            return !e.isAlive();
        }
        PartyDungeon.LOGGER.debug("[Party Dungeon] モブが見つからない: {}", id);
        // 見つからない：チャンクが読み込まれているのにいないなら、消えたとみなす
        BlockPos last = run.mobLastPos.get(id);
        return last != null && arena.isLoaded(last);
    }

    private static void spawn(ServerLevel arena, Run run, SpawnState s) {
        s.triggered = true;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(s.point.entity()).orElse(null);
        if (type == null) {
            PartyDungeon.LOGGER.warn("[Party Dungeon] モブが見つかりません（MODが外された？）: {}", s.point.entity());
            return;
        }
        BlockPos at = run.world(s.point.rel());
        for (int i = 0; i < s.point.count(); i++) {
            Entity e;
            try {
                e = type.spawn(arena, at, MobSpawnType.EVENT);
            } catch (Exception ex) {
                PartyDungeon.LOGGER.warn("[Party Dungeon] モブを出せません: {}", s.point.entity(), ex);
                continue;
            }
            if (e == null) {
                PartyDungeon.LOGGER.warn("[Party Dungeon] モブを出せませんでした: {} at {}", s.point.entity(), at);
                continue;
            }
            if (e instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            s.mobs.add(e.getUUID());
            run.mobLastPos.put(e.getUUID(), at);
            BY_MOB.put(e.getUUID(), run);
        }
    }

    // ------------------------------------------------------------------ 終了

    /** 挑戦を終わらせて、全員を戻し、部屋を片付ける */
    public static void finish(MinecraftServer server, Run run, boolean cleared) {
        PartyDungeon.LOGGER.info("[Party Dungeon] 部屋{} 「{}」終了（{}）", run.room.index + 1, run.def.name,
                cleared ? "クリア" : "中断");
        PDData data = PDData.get(server);
        for (UUID id : run.members) {
            BY_PLAYER.remove(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                ReturnInfo info = data.returns.remove(id);
                if (info != null) {
                    info.restore(p);
                }
                if (!cleared && run.state != State.PREPARING) {
                    Msg.info(p, "ダンジョンが終了しました。");
                }
            }
        }
        data.setDirty();
        run.members.clear();
        for (SpawnState s : run.spawns) {
            for (UUID id : s.mobs) {
                BY_MOB.remove(id);
            }
        }
        cleanRoom(server, run.room, run.tpl.sizeX, run.tpl.sizeY, run.tpl.sizeZ);
    }

    private static void cleanRoom(MinecraftServer server, Room room, int sx, int sy, int sz) {
        ServerLevel arena = arena(server);
        room.run = null;
        room.cleaning = true;
        BlockPos o = room.origin;
        discardEntities(arena, new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + sx, o.getY() + sy, o.getZ() + sz));
        BlockJobs.add(BlockJobs.clear(arena, o, sx, sy, sz, () -> {
            room.cleaning = false;
            PDData d = PDData.get(server);
            d.dirtyRooms.remove(room.index);
            d.setDirty();
        }));
    }

    private static void discardEntities(ServerLevel level, AABB box) {
        for (Entity e : level.getEntities((Entity) null, box.inflate(2), e -> !(e instanceof Player))) {
            e.discard();
        }
    }

    /** 自分だけ抜ける（/pd exit） */
    public static void leave(MinecraftServer server, ServerPlayer p) {
        Run run = BY_PLAYER.remove(p.getUUID());
        if (run == null) {
            return;
        }
        run.members.remove(p.getUUID());
        PDData data = PDData.get(server);
        ReturnInfo info = data.returns.remove(p.getUUID());
        data.setDirty();
        if (info != null) {
            info.restore(p);
        }
        Msg.info(p, "ダンジョンから抜けました。");
        for (ServerPlayer other : onlineMembers(server, run)) {
            Msg.info(other, p.getName().getString() + " さんがダンジョンから抜けました。");
        }
        if (run.members.isEmpty() && run.state != State.PREPARING) {
            finish(server, run, false);
        }
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, Run run) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID id : run.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ イベントから呼ばれる

    public static void onMobDeath(Entity entity) {
        Run run = BY_MOB.remove(entity.getUUID());
        if (run != null) {
            run.deadMobs.add(entity.getUUID());
        }
    }

    /** ダンジョン内で死んだ：持ち物を預かる（落とさない） */
    public static void onPlayerDeath(ServerPlayer p) {
        if (!isInRun(p.getUUID()) || !PDConfig.KEEP_INVENTORY.get()) {
            return;
        }
        SAVED_INV.put(p.getUUID(), p.getInventory().save(new net.minecraft.nbt.ListTag()));
        SAVED_XP.put(p.getUUID(), new int[] {p.experienceLevel, p.totalExperience});
        SAVED_XP_PROGRESS.put(p.getUUID(), p.experienceProgress);
        p.getInventory().clearContent();
    }

    public static boolean keepsExperience(UUID id) {
        return SAVED_XP.containsKey(id);
    }

    /** 復活した新しいプレイヤーに持ち物を返す */
    public static void onPlayerClone(ServerPlayer newPlayer) {
        net.minecraft.nbt.ListTag inv = SAVED_INV.remove(newPlayer.getUUID());
        if (inv != null) {
            newPlayer.getInventory().load(inv);
        }
        int[] xp = SAVED_XP.remove(newPlayer.getUUID());
        Float progress = SAVED_XP_PROGRESS.remove(newPlayer.getUUID());
        if (xp != null) {
            newPlayer.experienceLevel = xp[0];
            newPlayer.totalExperience = xp[1];
            newPlayer.experienceProgress = progress == null ? 0f : progress;
        }
    }

    /** 復活したらスタート地点へ */
    public static void onRespawn(MinecraftServer server, ServerPlayer p) {
        Run run = BY_PLAYER.get(p.getUUID());
        if (run == null || run.state == State.PREPARING) {
            return;
        }
        ServerLevel arena = arena(server);
        BlockPos start = run.world(run.def.start);
        p.teleportTo(arena, start.getX() + 0.5, start.getY(), start.getZ() + 0.5, p.getYRot(), 0);
        p.setGameMode(GameType.ADVENTURE);
        Msg.info(p, "スタート地点から再開します。");
    }

    public static void onLogout(MinecraftServer server, ServerPlayer p) {
        Run run = BY_PLAYER.remove(p.getUUID());
        if (run == null) {
            return;
        }
        run.members.remove(p.getUUID());
        // 戻り先は残しておき、次にログインしたときに戻す
        if (run.members.isEmpty() && run.state != State.PREPARING) {
            finish(server, run, false);
        }
    }

    /** ログインしたとき、挑戦中でないのに挑戦用の世界にいたら元の場所へ戻す */
    public static void onLogin(MinecraftServer server, ServerPlayer p) {
        if (p.level().dimension() != PDDimensions.ARENA || isInRun(p.getUUID())) {
            return;
        }
        PDData data = PDData.get(server);
        ReturnInfo info = data.returns.remove(p.getUUID());
        data.setDirty();
        if (info != null) {
            info.restore(p);
        } else {
            ServerLevel ow = server.overworld();
            BlockPos spawn = ow.getSharedSpawnPos();
            p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            p.setGameMode(server.getDefaultGameType());
        }
        Msg.info(p, "ダンジョンが終わっていたので、元の場所に戻しました。");
    }

    /** サーバー起動時：前回ブロックが残ったままの部屋を片付ける */
    public static void onServerStarted(MinecraftServer server) {
        reset();
        PDData data = PDData.get(server);
        for (Map.Entry<Integer, int[]> e : new HashMap<>(data.dirtyRooms).entrySet()) {
            int idx = e.getKey();
            int[] sz = e.getValue();
            if (sz.length < 3) {
                data.dirtyRooms.remove(idx);
                continue;
            }
            ensureRooms();
            while (ROOMS.size() <= idx) {
                ROOMS.add(new Room(ROOMS.size()));
            }
            cleanRoom(server, ROOMS.get(idx), sz[0], sz[1], sz[2]);
        }
    }

    public static void reset() {
        ROOMS.clear();
        BY_PLAYER.clear();
        BY_MOB.clear();
        SAVED_INV.clear();
        SAVED_XP.clear();
        SAVED_XP_PROGRESS.clear();
        BlockJobs.clearAll();
    }

    private RunManager() {
    }
}
