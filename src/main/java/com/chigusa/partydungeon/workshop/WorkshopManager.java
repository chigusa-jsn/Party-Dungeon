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
            data.returns.put(p.getUUID(), ReturnInfo.of(p));
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
        Msg.ok(p, "作業場に入りました。/pd create <名前> で新しいダンジョンの区画を作れます。/pd help で使い方。");
    }

    private static void ensureLobby(ServerLevel ws) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
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

    /** /pd exit（作業場から出る） */
    public static boolean exit(MinecraftServer server, ServerPlayer p) {
        PDData data = PDData.get(server);
        ReturnInfo info = data.returns.get(p.getUUID());
        if (!isIn(p) && info == null) {
            return false;
        }
        data.returns.remove(p.getUUID());
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
            data.returns.put(p.getUUID(), ReturnInfo.of(p));
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
            if (now != null) {
                teleportIntoPlot(ws, now, d);
                now.setGameMode(GameType.CREATIVE);
                Msg.ok(now, "外枠ができました！ 中を自由に建築してください。");
                Msg.info(now, "スタート地点は中央にしてあります。変えるなら立って /pd setstart");
                Msg.info(now, "敵は /pd spawn add <モブ> 、終わったら /pd publish で公開");
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
        String problem = d.validate();
        if (problem != null) {
            Msg.warn(p, "公開できません: " + problem);
            return;
        }
        ServerLevel ws = workshop(server);
        DungeonTemplate tpl = DungeonTemplate.capture(ws, d.plotOrigin(), d.size, d.height, d.size);
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
