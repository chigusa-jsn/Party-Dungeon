package com.chigusa.partydungeon.workshop;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDDimensions;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.ReturnInfo;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.registry.PDRegistry;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 床に描いた「間取り」を読み取り、大きく引き伸ばして本物の部屋と通路を建てる。
 *
 * ・1マス = scale × scale ブロック
 * ・2×2 以上のかたまりの一部なら「部屋」、細い線は「通路」（幅は別に決められる）
 * ・赤い間取りブロックの場所がスタート地点
 */
public final class PlanBuilder {
    /** 1つの間取りで読み取るマスの上限 */
    private static final int MAX_CELLS = 2048;
    /** 区画の最大の広さ（区画どうしの間隔より小さく） */
    public static final int MAX_SIZE = PDDimensions.PLOT_SPACING - 16;

    /** 読み取った間取り */
    public record Plan(int w, int h, boolean[][] cells, @Nullable int[] start) {
        public int cellCount() {
            int n = 0;
            for (boolean[] row : cells) {
                for (boolean c : row) {
                    if (c) {
                        n++;
                    }
                }
            }
            return n;
        }
    }

    private static boolean isPlan(Block b) {
        return b == PDRegistry.PLAN_ROOM.get() || b == PDRegistry.PLAN_START.get();
    }

    /** クリックしたブロックからつながっている間取りを読み取る。間取りでなければ null */
    @Nullable
    public static Plan read(ServerLevel level, BlockPos clicked) {
        if (!isPlan(level.getBlockState(clicked).getBlock())) {
            return null;
        }
        int y = clicked.getY();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(clicked);
        seen.add(clicked.asLong());
        int minX = clicked.getX();
        int maxX = minX;
        int minZ = clicked.getZ();
        int maxZ = minZ;
        while (!queue.isEmpty() && seen.size() <= MAX_CELLS) {
            BlockPos p = queue.poll();
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            for (BlockPos n : new BlockPos[] {p.north(), p.south(), p.east(), p.west()}) {
                if (n.getY() == y && !seen.contains(n.asLong()) && isPlan(level.getBlockState(n).getBlock())) {
                    seen.add(n.asLong());
                    queue.add(n);
                }
            }
        }
        int w = maxX - minX + 1;
        int h = maxZ - minZ + 1;
        boolean[][] cells = new boolean[w][h];
        int[] start = null;
        for (long l : seen) {
            BlockPos p = BlockPos.of(l);
            cells[p.getX() - minX][p.getZ() - minZ] = true;
            if (level.getBlockState(p).getBlock() == PDRegistry.PLAN_START.get() && start == null) {
                start = new int[] {p.getX() - minX, p.getZ() - minZ};
            }
        }
        return new Plan(w, h, cells, start);
    }

    /** 部屋の一部か（2×2 のかたまりに入っているか）。入っていなければ通路 */
    static boolean isRoomCell(Plan plan, int cx, int cz) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                if (filled(plan, cx + dx, cz + dz) && filled(plan, cx + dx + 1, cz + dz)
                        && filled(plan, cx + dx, cz + dz + 1) && filled(plan, cx + dx + 1, cz + dz + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean filled(Plan plan, int x, int z) {
        return x >= 0 && z >= 0 && x < plan.w && z < plan.h && plan.cells[x][z];
    }

    /** 床になる場所（ブロック単位）を計算する */
    static boolean[][] floorMap(Plan plan, int scale, int corridor) {
        int bw = plan.w * scale;
        int bh = plan.h * scale;
        boolean[][] floor = new boolean[bw][bh];
        int c = Math.max(1, Math.min(scale, corridor));
        int lo = (scale - c) / 2;
        int hi = lo + c; // [lo, hi)
        for (int cx = 0; cx < plan.w; cx++) {
            for (int cz = 0; cz < plan.h; cz++) {
                if (!plan.cells[cx][cz]) {
                    continue;
                }
                boolean room = isRoomCell(plan, cx, cz);
                for (int x = 0; x < scale; x++) {
                    for (int z = 0; z < scale; z++) {
                        boolean on;
                        if (room) {
                            on = true;
                        } else {
                            boolean inX = x >= lo && x < hi;
                            boolean inZ = z >= lo && z < hi;
                            // 中央の四角 ＋ つながっている方向へのびる道
                            on = (inX && inZ)
                                    || (inX && z < lo && filled(plan, cx, cz - 1))
                                    || (inX && z >= hi && filled(plan, cx, cz + 1))
                                    || (inZ && x < lo && filled(plan, cx - 1, cz))
                                    || (inZ && x >= hi && filled(plan, cx + 1, cz));
                        }
                        if (on) {
                            floor[cx * scale + x][cz * scale + z] = true;
                        }
                    }
                }
            }
        }
        return floor;
    }

    /** 間取りから新しいダンジョンを作り、建て始める */
    public static void build(MinecraftServer server, ServerPlayer p, Plan plan, String name, int scale, int corridor,
            int height, BlockState wall) {
        ServerLevel ws = WorkshopManager.workshop(server);
        PDData data = PDData.get(server);
        if (name.isBlank()) {
            Msg.warn(p, "名前を入れてください。");
            return;
        }
        if (data.findByName(name) != null) {
            Msg.warn(p, "「" + name + "」という名前のダンジョンは、もうあります。");
            return;
        }
        int size = Math.max(plan.w, plan.h) * scale + 2;
        if (size > MAX_SIZE) {
            Msg.warn(p, "大きすぎます（" + size + "ブロック）。最大 " + MAX_SIZE + " ブロックまでです。1マスの大きさを小さくしてください。");
            return;
        }
        int h = Math.max(5, Math.min(96, height));
        boolean[][] floor = floorMap(plan, scale, corridor);
        int bw = floor.length;
        int bh = floor[0].length;

        if (!WorkshopManager.isIn(p)) {
            data.workshopReturns.put(p.getUUID(), ReturnInfo.of(p));
        }
        String id = "d" + data.nextId++;
        DungeonDef d = new DungeonDef(id, name, p.getUUID(), data.nextPlot++, size, h);
        if (plan.start != null) {
            d.start = new BlockPos(1 + plan.start[0] * scale + scale / 2, 1, 1 + plan.start[1] * scale + scale / 2);
        } else {
            // 最初の床の場所
            outer:
            for (int x = 0; x < bw; x++) {
                for (int z = 0; z < bh; z++) {
                    if (floor[x][z]) {
                        d.start = new BlockPos(1 + x, 1, 1 + z);
                        break outer;
                    }
                }
            }
        }
        data.dungeons.put(id, d);
        data.setDirty();

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockPos startWorld = d.plotOrigin().offset(d.start);
        Msg.info(p, "「" + name + "」を建てています…（" + size + "×" + size + "、高さ" + h + "）");
        BlockJobs.add(BlockJobs.custom(ws, d.plotOrigin(), size, h, size, (x, y, z) -> {
            // 区画の座標 → 床マップの座標（周りに壁の1マスぶん余白）
            int fx = x - 1;
            int fz = z - 1;
            boolean isFloor = fx >= 0 && fz >= 0 && fx < bw && fz < bh && floor[fx][fz];
            boolean isWall = !isFloor && nearFloor(floor, fx, fz);
            if (!isFloor && !isWall) {
                return air;
            }
            if (y == 0) {
                return wall;
            }
            if (y == h - 1) {
                return isFloor && fx % 6 == 3 && fz % 6 == 3 ? light : wall;
            }
            return isWall ? wall : air;
        }, () -> {
            // スタート地点にスタートブロックを置く
            ws.setBlockAndUpdate(startWorld, PDRegistry.START_MARKER.get().defaultBlockState());
            ServerPlayer now = server.getPlayerList().getPlayer(p.getUUID());
            if (now != null) {
                now.teleportTo(ws, startWorld.getX() + 0.5, startWorld.getY() + 1, startWorld.getZ() + 0.5,
                        now.getYRot(), 0);
                now.setGameMode(GameType.CREATIVE);
                Msg.ok(now, "できました！ スタート地点（緑のブロック）にいます。");
                Msg.info(now, "敵ブロック・ゴールブロックを置いて右クリックで設定 → P キー「製作メニュー」で公開・テストプレイ");
            }
        }));
    }

    private static boolean nearFloor(boolean[][] floor, int fx, int fz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = fx + dx;
                int z = fz + dz;
                if (x >= 0 && z >= 0 && x < floor.length && z < floor[0].length && floor[x][z]) {
                    return true;
                }
            }
        }
        return false;
    }

    private PlanBuilder() {
    }
}
