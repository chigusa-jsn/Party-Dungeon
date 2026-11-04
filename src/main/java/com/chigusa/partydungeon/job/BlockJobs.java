package com.chigusa.partydungeon.job;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.chigusa.partydungeon.PDConfig;
import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.template.DungeonTemplate;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 大量のブロックを置き換える作業を、1tick あたりの上限つきで少しずつ進める。
 * 一気にやるとサーバーが固まるため（部屋のリセット・組み立て・外枠の自動建築で使う）。
 */
public final class BlockJobs {
    /** 隣のブロックの更新やアイテムのドロップを起こさずに置き換える */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Deque<Job> QUEUE = new ArrayDeque<>();

    public interface Job {
        /** budget 個ぶん進める。終わったら true */
        boolean step(int budget);

        void onDone();
    }

    public static void add(Job job) {
        QUEUE.add(job);
    }

    /** その場で最後まで実行する（テスト用） */
    public static void runNow(Job job) {
        while (!job.step(1_000_000)) {
            // 続ける
        }
        job.onDone();
    }

    public static boolean isBusy() {
        return !QUEUE.isEmpty();
    }

    /** サーバーの毎tickで呼ばれる */
    public static void tick() {
        Job job = QUEUE.peek();
        if (job == null) {
            return;
        }
        boolean done;
        try {
            done = job.step(PDConfig.BLOCKS_PER_TICK.get());
        } catch (Exception e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] ブロック作業でエラー", e);
            done = true;
        }
        if (done) {
            QUEUE.poll();
            try {
                job.onDone();
            } catch (Exception e) {
                PartyDungeon.LOGGER.error("[Party Dungeon] ブロック作業の完了処理でエラー", e);
            }
        }
    }

    public static void clearAll() {
        QUEUE.clear();
    }

    // ------------------------------------------------------------------ 種類

    /** 箱の中を順番に処理する共通部分 */
    private abstract static class BoxJob implements Job {
        protected final ServerLevel level;
        protected final BlockPos origin;
        protected final int sx;
        protected final int sy;
        protected final int sz;
        private final Runnable done;
        protected int index;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        BoxJob(ServerLevel level, BlockPos origin, int sx, int sy, int sz, Runnable done) {
            this.level = level;
            this.origin = origin;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.done = done;
        }

        abstract BlockState stateAt(int index, int x, int y, int z);

        @Override
        public boolean step(int budget) {
            int total = sx * sy * sz;
            int end = Math.min(total, index + budget);
            for (; index < end; index++) {
                int x = index % sx;
                int rest = index / sx;
                int z = rest % sz;
                int y = rest / sz;
                m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                BlockState want = stateAt(index, x, y, z);
                if (level.getBlockState(m) != want) {
                    level.setBlock(m, want, FLAGS);
                }
            }
            return index >= total && finish();
        }

        /** ブロックを置き終わったあとの追加作業。終わったら true */
        boolean finish() {
            return true;
        }

        @Override
        public void onDone() {
            if (done != null) {
                done.run();
            }
        }
    }

    /** 範囲を空気にする（部屋のリセット） */
    public static Job clear(ServerLevel level, BlockPos origin, int sx, int sy, int sz, Runnable done) {
        return new BoxJob(level, origin, sx, sy, sz, done) {
            @Override
            BlockState stateAt(int index, int x, int y, int z) {
                return Blocks.AIR.defaultBlockState();
            }
        };
    }

    /** 床・壁・天井の外枠を建てて、中を空気にする（作業場の自動建築） */
    public static Job frame(ServerLevel level, BlockPos origin, int size, int height, BlockState wall, Runnable done) {
        return new BoxJob(level, origin, size, height, size, done) {
            @Override
            BlockState stateAt(int index, int x, int y, int z) {
                boolean edge = y == 0 || y == height - 1 || x == 0 || x == size - 1 || z == 0 || z == size - 1;
                // 天井に明かりを並べる（6ブロックおき）
                if (y == height - 1 && x % 6 == 3 && z % 6 == 3 && x < size - 1 && z < size - 1) {
                    return Blocks.SEA_LANTERN.defaultBlockState();
                }
                return edge ? wall : Blocks.AIR.defaultBlockState();
            }
        };
    }

    /** 位置ごとに置くブロックを決める関数 */
    @FunctionalInterface
    public interface StateFn {
        BlockState at(int x, int y, int z);
    }

    /** 自由な形で建てる（間取りからの自動建築） */
    public static Job custom(ServerLevel level, BlockPos origin, int sx, int sy, int sz, StateFn fn, Runnable done) {
        return new BoxJob(level, origin, sx, sy, sz, done) {
            @Override
            BlockState stateAt(int index, int x, int y, int z) {
                return fn.at(x, y, z);
            }
        };
    }

    /** 設計図どおりに組み立てる（挑戦の開始） */
    public static Job place(ServerLevel level, BlockPos origin, DungeonTemplate tpl, Runnable done) {
        return new BoxJob(level, origin, tpl.sizeX, tpl.sizeY, tpl.sizeZ, done) {
            private boolean entitiesDone;

            @Override
            BlockState stateAt(int index, int x, int y, int z) {
                return tpl.palette.get(tpl.data[index]);
            }

            @Override
            boolean finish() {
                if (entitiesDone) {
                    return true;
                }
                // チェストの中身などを入れる
                for (Map.Entry<Integer, CompoundTag> e : tpl.blockEntities.entrySet()) {
                    BlockPos pos = origin.offset(tpl.relPos(e.getKey()));
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        try {
                            be.loadWithComponents(e.getValue(), level.registryAccess());
                            be.setChanged();
                            BlockState s = level.getBlockState(pos);
                            level.sendBlockUpdated(pos, s, s, Block.UPDATE_CLIENTS);
                        } catch (Exception ex) {
                            PartyDungeon.LOGGER.warn("[Party Dungeon] ブロックの中身を復元できません: {}", pos, ex);
                        }
                    }
                }
                entitiesDone = true;
                return true;
            }
        };
    }

    private BlockJobs() {
    }
}
