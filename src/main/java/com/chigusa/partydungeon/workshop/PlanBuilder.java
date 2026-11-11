package com.chigusa.partydungeon.workshop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

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
 * ・白: 2×2 以上のかたまりの一部なら「部屋」、細い線は「通路」（幅は別に決められる）
 * ・紫: ボス部屋。つながったかたまりが、天井の高さ 20 の円形の部屋になる
 * ・赤: スタート地点
 */
public final class PlanBuilder {
    /** 1つの間取りで読み取るマスの上限 */
    private static final int MAX_CELLS = 4096;
    /** 広さの上限 */
    public static final int MAX_SIZE = 512;
    /** 高さの上限 */
    public static final int MAX_HEIGHT = 160;
    /** ボス部屋の高さ（床から天井まで） */
    public static final int BOSS_HEIGHT = 20;

    static final int EMPTY = 0;
    static final int NORMAL = 1;
    static final int BOSS = 2;

    /** 読み取った間取り。cells は EMPTY / NORMAL / BOSS */
    public record Plan(int w, int h, int[][] cells, @Nullable int[] start) {
        public int cellCount() {
            int n = 0;
            for (int[] row : cells) {
                for (int c : row) {
                    if (c != EMPTY) {
                        n++;
                    }
                }
            }
            return n;
        }

        public boolean hasBoss() {
            for (int[] row : cells) {
                for (int c : row) {
                    if (c == BOSS) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private static int typeOf(Block b) {
        if (b == PDRegistry.PLAN_ROOM.get() || b == PDRegistry.PLAN_START.get()) {
            return NORMAL;
        }
        if (b == PDRegistry.PLAN_BOSS.get()) {
            return BOSS;
        }
        return EMPTY;
    }

    /** クリックしたブロックからつながっている間取りを読み取る。間取りでなければ null */
    @Nullable
    public static Plan read(ServerLevel level, BlockPos clicked) {
        if (typeOf(level.getBlockState(clicked).getBlock()) == EMPTY) {
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
                if (n.getY() == y && !seen.contains(n.asLong()) && typeOf(level.getBlockState(n).getBlock()) != EMPTY) {
                    seen.add(n.asLong());
                    queue.add(n);
                }
            }
        }
        int w = maxX - minX + 1;
        int h = maxZ - minZ + 1;
        int[][] cells = new int[w][h];
        int[] start = null;
        for (long l : seen) {
            BlockPos p = BlockPos.of(l);
            Block b = level.getBlockState(p).getBlock();
            cells[p.getX() - minX][p.getZ() - minZ] = typeOf(b);
            if (b == PDRegistry.PLAN_START.get() && start == null) {
                start = new int[] {p.getX() - minX, p.getZ() - minZ};
            }
        }
        return new Plan(w, h, cells, start);
    }

    static int cell(Plan plan, int x, int z) {
        return x >= 0 && z >= 0 && x < plan.w && z < plan.h ? plan.cells[x][z] : EMPTY;
    }

    static boolean filled(Plan plan, int x, int z) {
        return cell(plan, x, z) != EMPTY;
    }

    /** 普通の部屋の一部か（白の 2×2 のかたまりに入っているか）。入っていなければ通路 */
    static boolean isRoomCell(Plan plan, int cx, int cz) {
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                if (cell(plan, cx + dx, cz + dz) == NORMAL && cell(plan, cx + dx + 1, cz + dz) == NORMAL
                        && cell(plan, cx + dx, cz + dz + 1) == NORMAL && cell(plan, cx + dx + 1, cz + dz + 1) == NORMAL) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 床になる場所と、その天井の高さ（ブロック単位）。0 = 床ではない。
     * 普通の部屋・通路は normalTop、ボス部屋は BOSS_HEIGHT - 1。
     */
    static int[][] topMap(Plan plan, int scale, int corridor, int normalTop) {
        int bw = plan.w * scale;
        int bh = plan.h * scale;
        int[][] top = new int[bw][bh];
        int c = Math.max(1, Math.min(scale, corridor));
        int lo = (scale - c) / 2;
        int hi = lo + c; // [lo, hi)

        // 白（部屋・通路）
        for (int cx = 0; cx < plan.w; cx++) {
            for (int cz = 0; cz < plan.h; cz++) {
                if (plan.cells[cx][cz] != NORMAL) {
                    continue;
                }
                boolean room = isRoomCell(plan, cx, cz);
                for (int x = 0; x < scale; x++) {
                    for (int z = 0; z < scale; z++) {
                        boolean inX = x >= lo && x < hi;
                        boolean inZ = z >= lo && z < hi;
                        boolean on = room || (inX && inZ)
                                || (inX && z < lo && filled(plan, cx, cz - 1))
                                || (inX && z >= hi && filled(plan, cx, cz + 1))
                                || (inZ && x < lo && filled(plan, cx - 1, cz))
                                || (inZ && x >= hi && filled(plan, cx + 1, cz));
                        if (on) {
                            top[cx * scale + x][cz * scale + z] = normalTop;
                        }
                    }
                }
            }
        }

        // 紫（ボス部屋）：かたまりごとに円形
        boolean[][] done = new boolean[plan.w][plan.h];
        int bossTop = BOSS_HEIGHT - 1;
        for (int sx = 0; sx < plan.w; sx++) {
            for (int sz = 0; sz < plan.h; sz++) {
                if (plan.cells[sx][sz] != BOSS || done[sx][sz]) {
                    continue;
                }
                List<int[]> group = new ArrayList<>();
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[] {sx, sz});
                done[sx][sz] = true;
                int x0 = sx;
                int x1 = sx;
                int z0 = sz;
                int z1 = sz;
                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    group.add(p);
                    x0 = Math.min(x0, p[0]);
                    x1 = Math.max(x1, p[0]);
                    z0 = Math.min(z0, p[1]);
                    z1 = Math.max(z1, p[1]);
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : dirs) {
                        int nx = p[0] + d[0];
                        int nz = p[1] + d[1];
                        if (cell(plan, nx, nz) == BOSS && !done[nx][nz]) {
                            done[nx][nz] = true;
                            q.add(new int[] {nx, nz});
                        }
                    }
                }
                double cxB = (x0 * scale + (x1 + 1) * scale) / 2.0;
                double czB = (z0 * scale + (z1 + 1) * scale) / 2.0;
                double r = Math.min((x1 - x0 + 1) * scale, (z1 - z0 + 1) * scale) / 2.0 - 0.5;
                for (int x = x0 * scale; x < (x1 + 1) * scale; x++) {
                    for (int z = z0 * scale; z < (z1 + 1) * scale; z++) {
                        double dx = x + 0.5 - cxB;
                        double dz = z + 0.5 - czB;
                        if (dx * dx + dz * dz <= r * r) {
                            top[x][z] = bossTop;
                        }
                    }
                }
                // 外の白とつながる所から、円の中心へ向かって道をのばす
                for (int[] p : group) {
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    for (int[] d : dirs) {
                        if (cell(plan, p[0] + d[0], p[1] + d[1]) != NORMAL) {
                            continue;
                        }
                        if (d[0] != 0) {
                            int zs = p[1] * scale + lo;
                            int edge = d[0] > 0 ? (p[0] + 1) * scale - 1 : p[0] * scale;
                            int step = d[0] > 0 ? -1 : 1;
                            for (int x = edge; step < 0 ? x >= cxB : x < cxB; x += step) {
                                for (int z = zs; z < zs + c; z++) {
                                    top[x][z] = Math.max(top[x][z], normalTop);
                                }
                            }
                        } else {
                            int xs = p[0] * scale + lo;
                            int edge = d[1] > 0 ? (p[1] + 1) * scale - 1 : p[1] * scale;
                            int step = d[1] > 0 ? -1 : 1;
                            for (int z = edge; step < 0 ? z >= czB : z < czB; z += step) {
                                for (int x = xs; x < xs + c; x++) {
                                    top[x][z] = Math.max(top[x][z], normalTop);
                                }
                            }
                        }
                    }
                }
            }
        }
        return top;
    }

    /** まわり（自分を含む 3×3）の床の天井の高さの最大 */
    private static int[][] neighborTop(int[][] top) {
        int bw = top.length;
        int bh = top[0].length;
        int[][] nt = new int[bw + 2][bh + 2];
        for (int x = -1; x <= bw; x++) {
            for (int z = -1; z <= bh; z++) {
                int m = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int ax = x + dx;
                        int az = z + dz;
                        if (ax >= 0 && az >= 0 && ax < bw && az < bh) {
                            m = Math.max(m, top[ax][az]);
                        }
                    }
                }
                nt[x + 1][z + 1] = m;
            }
        }
        return nt;
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
        int h = Math.max(5, Math.min(MAX_HEIGHT, height));
        int boxH = plan.hasBoss() ? Math.max(h, BOSS_HEIGHT) : h;
        int normalTop = h - 1;
        int[][] top = topMap(plan, scale, corridor, normalTop);
        int[][] nt = neighborTop(top);
        int bw = top.length;
        int bh = top[0].length;

        if (!WorkshopManager.isIn(p)) {
            data.workshopReturns.put(p.getUUID(), ReturnInfo.of(p));
        }
        String id = "d" + data.nextId++;
        DungeonDef d = new DungeonDef(id, name, p.getUUID(), data.allocatePlot(size), size, boxH);
        if (plan.start != null) {
            d.start = new BlockPos(1 + plan.start[0] * scale + scale / 2, 1, 1 + plan.start[1] * scale + scale / 2);
        } else {
            outer:
            for (int x = 0; x < bw; x++) {
                for (int z = 0; z < bh; z++) {
                    if (top[x][z] > 0) {
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
        BlockState bossFloor = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        int bossTop = BOSS_HEIGHT - 1;
        BlockPos startWorld = d.plotOrigin().offset(d.start);
        Msg.info(p, "「" + name + "」を建てています…（" + size + "×" + size + "、高さ" + boxH + "）");
        BlockJobs.add(BlockJobs.custom(ws, d.plotOrigin(), size, boxH, size, (x, y, z) -> {
            int fx = x - 1;
            int fz = z - 1;
            int t = fx >= 0 && fz >= 0 && fx < bw && fz < bh ? top[fx][fz] : 0;
            int around = fx >= -1 && fz >= -1 && fx <= bw && fz <= bh ? nt[fx + 1][fz + 1] : 0;
            if (t > 0) {
                // 床の柱
                if (y == 0) {
                    return t == bossTop ? bossFloor : wall;
                }
                if (y < t) {
                    return air;
                }
                if (y == t) {
                    boolean lamp = t == bossTop ? fx % 4 == 2 && fz % 4 == 2 : fx % 6 == 3 && fz % 6 == 3;
                    return lamp ? light : wall;
                }
                // 天井より上：となりにもっと高い部屋があれば、その高さまで壁でふさぐ
                return y <= around ? wall : air;
            }
            if (around == 0) {
                return air;
            }
            // 壁の柱。ボス部屋の壁には明かりを埋め込む
            if (around == bossTop && y == 4 && (fx + fz) % 4 == 0) {
                return light;
            }
            return y <= around ? wall : air;
        }, () -> {
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

    private PlanBuilder() {
    }
}
