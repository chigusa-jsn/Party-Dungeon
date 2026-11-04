package com.chigusa.partydungeon.workshop;

import java.io.IOException;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDDimensions;
import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.ReturnInfo;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.run.RunManager;
import com.chigusa.partydungeon.template.DungeonTemplate;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * クリエイティブの作業場。ダンジョンの区画を作り、目印を置き、公開する。
 */
public final class WorkshopManager {
    /** 作業場の入口（区画がまだ無い人が最初に立つ足場） */
    private static final BlockPos LOBBY = new BlockPos(-64, PDDimensions.BASE_Y, 0);

    @Nullable
    public static ServerLevel workshop(MinecraftServer server) {
        return server.getLevel(PDDimensions.WORKSHOP);
    }

    public static boolean isIn(ServerPlayer p) {
        return p.level().dimension() == PDDimensions.WORKSHOP;
    }

    /** /pd workshop：作業場に入る */
    public static void enter(MinecraftServer server, ServerPlayer p) {
        if (RunManager.isInRun(p.getUUID())) {
            Msg.warn(p, "ダンジョンの挑戦中は作業場に入れません。");
            return;
        }
        ServerLevel ws = workshop(server);
        if (ws == null) {
            Msg.warn(p, "作業場のディメンションが読み込めません。");
            return;
        }
        PDData data = PDData.get(server);
        if (!isIn(p)) {
            data.workshopReturns.put(p.getUUID(), ReturnInfo.of(p));
            data.setDirty();
        }
        // 自分が最後に作った区画へ。無ければ入口へ
        DungeonDef mine = null;
        for (DungeonDef d : data.dungeons.values()) {
            if (d.owner.equals(p.getUUID())) {
                mine = d;
            }
        }
        if (mine != null) {
            teleportIntoPlot(ws, p, mine);
        } else {
            ensureLobby(ws);
            p.teleportTo(ws, LOBBY.getX() + 0.5, LOBBY.getY() + 1, LOBBY.getZ() + 0.5, p.getYRot(), 0);
        }
        p.setGameMode(GameType.CREATIVE);
        giveKit(p);
        Msg.ok(p, "作業場に入りました（製作モード）。道具を持ち物に入れました。");
        Msg.info(p, "間取りブロックで床に間取りを描いて、間取りの杖で右クリック → 部屋が自動で建ちます。");
        Msg.info(p, "P キー →「製作メニュー」で、ダンジョンの一覧・公開・テストプレイ。");
    }

    /** 製作用の道具を配る（持っていないものだけ） */
    public static void giveKit(ServerPlayer p) {
        net.minecraft.world.item.Item[] kit = {
                com.chigusa.partydungeon.registry.PDRegistry.PLAN_WAND.get(),
                com.chigusa.partydungeon.registry.PDRegistry.PLAN_ROOM_ITEM.get(),
                com.chigusa.partydungeon.registry.PDRegistry.PLAN_START_ITEM.get(),
                com.chigusa.partydungeon.registry.PDRegistry.START_MARKER_ITEM.get(),
                com.chigusa.partydungeon.registry.PDRegistry.SPAWNER_MARKER_ITEM.get(),
                com.chigusa.partydungeon.registry.PDRegistry.GOAL_MARKER_ITEM.get(),
                com.chigusa.partydungeon.registry.PDRegistry.REWARD_MARKER_ITEM.get()};
        for (net.minecraft.world.item.Item item : kit) {
            if (p.getInventory().countItem(item) == 0) {
                int n = item == com.chigusa.partydungeon.registry.PDRegistry.PLAN_ROOM_ITEM.get()
                        || item == com.chigusa.partydungeon.registry.PDRegistry.SPAWNER_MARKER_ITEM.get() ? 64 : 1;
                p.getInventory().add(new net.minecraft.world.item.ItemStack(item, n));
            }
        }
    }

    private static void ensureLobby(ServerLevel ws) {
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                BlockPos pos = LOBBY.offset(x, 0, z);
                if (ws.getBlockState(pos).isAir()) {
                    ws.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
                }
            }
        }
    }

    private static void teleportIntoPlot(ServerLevel ws, ServerPlayer p, DungeonDef d) {
        BlockPos o = d.plotOrigin();
        p.teleportTo(ws, o.getX() + d.size / 2.0, o.getY() + 1, o.getZ() + d.size / 2.0, p.getYRot(), 0);
    }

    /** 間取りを描くための広い足場へ */
    public static void enterLobby(MinecraftServer server, ServerPlayer p) {
        if (!isIn(p)) {
            enter(server, p);
        }
        ServerLevel ws = workshop(server);
        if (ws == null) {
            return;
        }
        ensureLobby(ws);
        p.teleportTo(ws, LOBBY.getX() + 0.5, LOBBY.getY() + 1, LOBBY.getZ() + 0.5, p.getYRot(), 0);
        Msg.info(p, "ここが設計スペースです。間取りブロックで床に間取りを描いて、間取りの杖で右クリックしてください。");
    }

    /** /pd exit（作業場から出る） */
    public static boolean exit(MinecraftServer server, ServerPlayer p) {
        PDData data = PDData.get(server);
        ReturnInfo info = data.workshopReturns.get(p.getUUID());
        if (!isIn(p) && info == null) {
            return false;
        }
        data.workshopReturns.remove(p.getUUID());
        data.setDirty();
        if (info != null) {
            info.restore(p);
        } else {
            ServerLevel ow = server.overworld();
            BlockPos s = ow.getSharedSpawnPos();
            p.teleportTo(ow, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
            p.setGameMode(server.getDefaultGameType());
        }
        Msg.info(p, "作業場から出ました。");
        return true;
    }

    /** /pd create：新しい区画を作り、外枠を自動で建てる */
    public static void create(MinecraftServer server, ServerPlayer p, String name, int size, int height,
            BlockState wall) {
        ServerLevel ws = workshop(server);
        if (ws == null) {
            Msg.warn(p, "作業場のディメンションが読み込めません。");
            return;
        }
        PDData data = PDData.get(server);
        if (data.findByName(name) != null) {
            Msg.warn(p, "「" + name + "」という名前のダンジョンは、もうあります。");
            return;
        }
        if (!isIn(p)) {
            data.workshopReturns.put(p.getUUID(), ReturnInfo.of(p));
        }
        String id = "d" + data.nextId++;
        DungeonDef d = new DungeonDef(id, name, p.getUUID(), data.nextPlot++, size, height);
        // スタート地点の初期値：床の中央
        d.start = new BlockPos(size / 2, 1, size / 2);
        data.dungeons.put(id, d);
        data.setDirty();

        Msg.info(p, "「" + name + "」の区画（" + size + "×" + size + "、高さ" + height + "）を建てています…");
        BlockJobs.add(BlockJobs.frame(ws, d.plotOrigin(), size, height, wall, () -> {
            ServerPlayer now = server.getPlayerList().getPlayer(p.getUUID());
            ws.setBlockAndUpdate(d.plotOrigin().offset(d.start),
                    com.chigusa.partydungeon.registry.PDRegistry.START_MARKER.get().defaultBlockState());
            if (now != null) {
                teleportIntoPlot(ws, now, d);
                now.setGameMode(GameType.CREATIVE);
                Msg.ok(now, "外枠ができました！ 中を自由に建築してください。");
                Msg.info(now, "スタートブロック（緑）は中央に置いてあります。動かしたければ壊して置き直してください。");
                Msg.info(now, "敵ブロック・ゴールブロックを置いて右クリックで設定 → P キー「製作メニュー」で公開");
            }
        }));
    }

    /** プレイヤーが立っている区画を探す。いなければメッセージを出して null */
    @Nullable
    public static DungeonDef here(MinecraftServer server, ServerPlayer p) {
        if (!isIn(p)) {
            Msg.warn(p, "作業場の中で使ってください（/pd workshop）。");
            return null;
        }
        DungeonDef d = PDData.get(server).findByPlotPos(p.blockPosition());
        if (d == null) {
            Msg.warn(p, "ダンジョンの区画の中に立って使ってください。");
        }
        return d;
    }

    public static BlockPos relPos(DungeonDef d, ServerPlayer p) {
        return p.blockPosition().subtract(d.plotOrigin());
    }

    /** /pd publish：今の建物を設計図にして公開する */
    public static void publish(MinecraftServer server, ServerPlayer p, DungeonDef d) {
        ServerLevel ws = workshop(server);
        java.util.List<DungeonTemplate.Marker> markers = new java.util.ArrayList<>();
        DungeonTemplate tpl = DungeonTemplate.capture(ws, d.plotOrigin(), d.size, d.height, d.size, markers);
        applyMarkers(p, d, markers);
        String problem = d.validate();
        if (problem != null) {
            Msg.warn(p, "公開できません: " + problem);
            return;
        }
        try {
            tpl.save(server, d.id);
        } catch (IOException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 設計図を保存できません", e);
            Msg.warn(p, "保存に失敗しました。サーバーのログを確認してください。");
            return;
        }
        d.published = true;
        d.revision++;
        PDData.get(server).setDirty();
        Msg.ok(p, "「" + d.name + "」を公開しました！（第" + d.revision + "版） /pd play から遊べます。");
        Msg.info(p, "建物を直したら、もう一度 /pd publish すると反映されます。");
    }

    /** 置いた目印ブロックから、スタート・敵・ゴールを決める */
    private static void applyMarkers(ServerPlayer p, DungeonDef d, java.util.List<DungeonTemplate.Marker> markers) {
        d.spawns.clear();
        boolean start = false;
        boolean goal = false;
        int unset = 0;
        for (DungeonTemplate.Marker m : markers) {
            if (m.block() == com.chigusa.partydungeon.registry.PDRegistry.START_MARKER.get()) {
                if (start) {
                    Msg.warn(p, "スタートブロックが2つ以上あります。1つ目を使います。");
                    continue;
                }
                d.start = m.rel();
                start = true;
            } else if (m.block() == com.chigusa.partydungeon.registry.PDRegistry.GOAL_MARKER.get()) {
                d.goal = m.rel();
                d.goalRadius = m.data() != null && m.data().contains("radius") ? m.data().getInt("radius") : 2;
                goal = true;
            } else if (m.block() == com.chigusa.partydungeon.registry.PDRegistry.SPAWNER_MARKER.get()) {
                net.minecraft.nbt.CompoundTag t = m.data();
                net.minecraft.resources.ResourceLocation id = t == null || !t.contains("entity") ? null
                        : net.minecraft.resources.ResourceLocation.tryParse(t.getString("entity"));
                if (id == null) {
                    unset++;
                    continue;
                }
                d.spawns.add(new SpawnPoint(m.rel(), id, Math.max(1, t.getInt("count")), Math.max(0, t.getInt("radius")),
                        t.getBoolean("boss")));
            }
        }
        if (!goal) {
            d.goal = null;
        }
        if (unset > 0) {
            Msg.warn(p, "モブが選ばれていない敵ブロックが " + unset + " 個あります（その分は出ません）。右クリックで選んでください。");
        }
        if (!start) {
            Msg.info(p, "スタートブロックが無いので、床の中央からスタートします。");
            d.start = new BlockPos(d.size / 2, 1, d.size / 2);
        }
    }

    /** 作業場にいる人に、目印をパーティクルで見せる（2秒ごと） */
    public static void tickMarkers(MinecraftServer server) {
        ServerLevel ws = workshop(server);
        if (ws == null || ws.players().isEmpty()) {
            return;
        }
        PDData data = PDData.get(server);
        for (ServerPlayer p : ws.players()) {
            DungeonDef d = data.findByPlotPos(p.blockPosition());
            if (d == null) {
                continue;
            }
            BlockPos o = d.plotOrigin();
            if (d.start != null) {
                show(ws, p, o.offset(d.start), ParticleTypes.HAPPY_VILLAGER);
            }
            if (d.goal != null) {
                show(ws, p, o.offset(d.goal), ParticleTypes.END_ROD);
            }
            for (SpawnPoint s : d.spawns) {
                show(ws, p, o.offset(s.rel()), s.boss() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME);
            }
        }
    }

    private static void show(ServerLevel ws, ServerPlayer p, BlockPos pos, ParticleOptions type) {
        ws.sendParticles(p, type, true, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 6, 0.2, 0.4, 0.2, 0.01);
    }

    private WorkshopManager() {
    }
}
