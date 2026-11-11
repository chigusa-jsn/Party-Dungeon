package com.chigusa.partydungeon.test;

import java.util.List;
import java.util.UUID;

import com.chigusa.partydungeon.PDDimensions;
import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.ClearCondition;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.run.RunManager;
import com.chigusa.partydungeon.workshop.WorkshopManager;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 自動テスト（GitHub 上でサーバーを起動して実行する。普段のプレイには影響しない）。
 * 作成 → 公開 → 入場 → 敵を倒す → クリア → 帰還 → 部屋の片付け を通しで確認する。
 */
@GameTestHolder(PartyDungeon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PDGameTests {

    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void fullRun(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // テスト用サーバーには MOD の世界が無いので、ネザーとエンドで代用する
        PDDimensions.ARENA = Level.NETHER;
        PDDimensions.WORKSHOP = Level.END;
        ServerLevel ws = server.getLevel(PDDimensions.WORKSHOP);
        ServerLevel arena = server.getLevel(PDDimensions.ARENA);
        helper.assertTrue(ws != null, "作業場のディメンションがない");
        helper.assertTrue(arena != null, "挑戦用のディメンションがない");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        UUID id = player.getUUID();

        // 区画を作る
        PDData data = PDData.get(server);
        DungeonDef d = new DungeonDef("test1", "テスト", id, 900, 16, 8);
        d.clear = ClearCondition.ALL;
        data.dungeons.put(d.id, d);
        BlockJobs.runNow(BlockJobs.frame(ws, d.plotOrigin(), 16, 8, Blocks.STONE_BRICKS.defaultBlockState(), null));
        helper.assertTrue(ws.getBlockState(d.plotOrigin()).is(Blocks.STONE_BRICKS), "外枠の床ができていない");
        helper.assertTrue(ws.getBlockState(d.plotOrigin().offset(5, 3, 5)).isAir(), "外枠の中が空気になっていない");
        helper.assertTrue(ws.getBlockState(d.plotOrigin().offset(5, 7, 5)).is(Blocks.STONE_BRICKS), "天井ができていない");

        // 普通のブロック（チェスト）と、目印ブロック（スタート・敵）を置いて公開
        ws.setBlockAndUpdate(d.plotOrigin().offset(2, 1, 2), Blocks.CHEST.defaultBlockState());
        ws.setBlockAndUpdate(d.plotOrigin().offset(8, 1, 8),
                com.chigusa.partydungeon.registry.PDRegistry.START_MARKER.get().defaultBlockState());
        BlockPos spawnerPos = d.plotOrigin().offset(4, 1, 4);
        ws.setBlockAndUpdate(spawnerPos, com.chigusa.partydungeon.registry.PDRegistry.SPAWNER_MARKER.get().defaultBlockState());
        net.minecraft.nbt.CompoundTag sd = new net.minecraft.nbt.CompoundTag();
        sd.putString("entity", "minecraft:zombie");
        sd.putInt("count", 2);
        ((com.chigusa.partydungeon.block.SpawnerMarkerBlockEntity) ws.getBlockEntity(spawnerPos)).loadData(sd);
        WorkshopManager.publish(server, player, d);
        helper.assertTrue(d.published, "公開できていない");
        helper.assertTrue(new BlockPos(8, 1, 8).equals(d.start), "スタートブロックの位置が読めていない: " + d.start);
        helper.assertTrue(d.spawns.size() == 1 && d.spawns.get(0).count() == 2, "敵ブロックが読めていない: " + d.spawns);

        Level original = player.level();
        // ハードの報酬：ダイヤ3個（100%）。ノーマルには何も入れない
        d.rewards(com.chigusa.partydungeon.data.Difficulty.HARD).add(com.chigusa.partydungeon.data.Reward.of(
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3), 100,
                server.registryAccess()));
        RunManager.start(server, player, d, com.chigusa.partydungeon.data.Difficulty.HARD);
        helper.assertTrue(RunManager.isInRun(id), "入場の準備が始まっていない");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    RunManager.Run run = RunManager.runOf(id);
                    helper.assertTrue(run != null && run.state == RunManager.State.ACTIVE, "まだ開始していない");
                })
                .thenExecuteAfter(20, () -> {
                    RunManager.Run run = RunManager.runOf(id);
                    helper.assertTrue(run != null && run.state == RunManager.State.ACTIVE, "すぐにクリア扱いになってしまった");
                })
                .thenWaitUntil(() -> {
                    RunManager.Run run = RunManager.runOf(id);
                    helper.assertTrue(RunManager.aliveMobs(run).size() == 3, "ハードでゾンビが3体（2×1.25）出ていない " + RunManager.describeMobs(run));
                })
                .thenExecute(() -> {
                    RunManager.Run run = RunManager.runOf(id);
                    helper.assertTrue(run.state == RunManager.State.ACTIVE, "倒す前にクリアになった");
                    helper.assertTrue(player.level().dimension() == PDDimensions.ARENA, "挑戦用の世界に移動していない");
                    helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "アドベンチャーになっていない");
                    BlockPos o = run.room.origin;
                    helper.assertTrue(arena.getBlockState(o).is(Blocks.STONE_BRICKS), "部屋が組み立てられていない");
                    helper.assertTrue(arena.getBlockState(o.offset(2, 1, 2)).is(Blocks.CHEST), "チェストが再現されていない");
                    helper.assertTrue(arena.getBlockState(o.offset(4, 1, 4)).isAir(), "敵ブロックが部屋に残っている");
                    helper.assertTrue(arena.getBlockState(o.offset(8, 1, 8)).isAir(), "スタートブロックが部屋に残っている");
                    List<net.minecraft.world.entity.Entity> zs = RunManager.aliveMobs(run);
                    for (net.minecraft.world.entity.Entity z : zs) {
                        float max = ((net.minecraft.world.entity.LivingEntity) z).getMaxHealth();
                        helper.assertTrue(Math.abs(max - 32f) < 0.01f, "ハードの体力が32（20×1.6）になっていない: " + max);
                    }
                    // 爆発してもブロックが壊れないこと
                    arena.explode(null, o.getX() + 8, o.getY() + 1, o.getZ() + 1, 4f, Level.ExplosionInteraction.TNT);
                    helper.assertTrue(arena.getBlockState(o.offset(8, 0, 0)).is(Blocks.STONE_BRICKS), "爆発で壁が壊れた");
                    for (net.minecraft.world.entity.Entity z : zs) {
                        z.kill();
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(RunManager.runOf(id) == null, "クリア後に戻っていない"))
                .thenExecute(() -> helper.assertTrue(player.level() == original, "元の世界に戻っていない"))
                .thenExecute(() -> helper.assertTrue(
                        player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == 3,
                        "報酬のダイヤ3個を受け取っていない"))
                .thenWaitUntil(() -> {
                    boolean allFree = RunManager.rooms().stream().allMatch(RunManager.Room::isFree);
                    helper.assertTrue(allFree, "部屋の片付けが終わっていない");
                })
                .thenExecute(() -> helper.assertTrue(arena.getBlockState(PDDimensions.roomOrigin(0)).isAir(),
                        "部屋がリセットされていない"))
                .thenExecute(() -> {
                    data.dungeons.remove(d.id);
                    player.discard();
                })
                .thenSucceed();
    }

    /** 間取りから部屋と通路が建つか */
    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void planBuild(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PDDimensions.ARENA = Level.NETHER;
        PDDimensions.WORKSHOP = Level.END;
        ServerLevel ws = server.getLevel(PDDimensions.WORKSHOP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // 間取り（E = 床の外）:  RR...RR   ← 2×2の部屋、3マスの通路、2×2の部屋
        //                       RR CCC RR      左上をスタートに
        BlockPos base = new BlockPos(5000, 64, 5000);
        net.minecraft.world.level.block.state.BlockState room =
                com.chigusa.partydungeon.registry.PDRegistry.PLAN_ROOM.get().defaultBlockState();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 2; z++) {
                boolean corridor = x >= 2 && x <= 4;
                if (corridor && z == 0) {
                    continue;
                }
                ws.setBlockAndUpdate(base.offset(x, 0, z), room);
            }
        }
        ws.setBlockAndUpdate(base, com.chigusa.partydungeon.registry.PDRegistry.PLAN_START.get().defaultBlockState());
        com.chigusa.partydungeon.workshop.PlanBuilder.Plan plan =
                com.chigusa.partydungeon.workshop.PlanBuilder.read(ws, base.offset(3, 0, 1));
        helper.assertTrue(plan != null && plan.w() == 7 && plan.h() == 2 && plan.cellCount() == 11,
                "間取りの読み取りがおかしい: " + (plan == null ? null : plan.w() + "x" + plan.h() + " " + plan.cellCount()));
        helper.assertTrue(plan.start() != null && plan.start()[0] == 0 && plan.start()[1] == 0, "スタートの位置がおかしい");
        com.chigusa.partydungeon.workshop.PlanBuilder.build(server, player, plan, "間取りテスト", 8, 3, 7,
                Blocks.STONE_BRICKS.defaultBlockState());
        DungeonDef d = PDData.get(server).findByName("間取りテスト");
        helper.assertTrue(d != null && d.size == 7 * 8 + 2, "ダンジョンが作られていない");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BlockJobs.isBusy(), "建築中"))
                .thenExecute(() -> {
                    BlockPos o = d.plotOrigin();
                    // 左の部屋（16×16 の床）の中ほど：床があって、上は空気
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 8, 0, 1 + 8)).is(Blocks.STONE_BRICKS), "部屋の床がない");
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 8, 3, 1 + 8)).isAir(), "部屋の中が空気でない");
                    // 通路（2行目の真ん中、幅3）：中央は空気、通路の横は壁
                    int cz = 1 + 8 + 3; // 2行目のマスの中央（幅3の通路は 2〜4 番目）
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 3 * 8 + 4, 2, cz)).isAir(), "通路が通っていない");
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 3 * 8 + 4, 2, cz - 2)).is(Blocks.STONE_BRICKS)
                            && ws.getBlockState(o.offset(1 + 3 * 8 + 4, 2, cz + 2)).is(Blocks.STONE_BRICKS),
                            "通路の横に壁がない");
                    // 1行目の通路の場所（何も描いていない）は、通路の壁の外なので空気
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 3 * 8 + 4, 2, 1 + 2)).isAir(), "描いていない所に何かある");
                    // スタートブロック
                    helper.assertTrue(ws.getBlockState(o.offset(d.start)).is(
                            com.chigusa.partydungeon.registry.PDRegistry.START_MARKER.get()), "スタートブロックがない");
                    PDData.get(server).dungeons.remove(d.id);
                    player.discard();
                })
                .thenSucceed();
    }

    /** ボス部屋（紫）が円形・高さ20で建ち、通路とつながるか */
    @GameTest(template = "empty", timeoutTicks = 20000)
    public static void planBoss(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        PDDimensions.ARENA = Level.NETHER;
        PDDimensions.WORKSHOP = Level.END;
        ServerLevel ws = server.getLevel(PDDimensions.WORKSHOP);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // 赤(スタート) 白 白 紫紫紫 ← 紫は 3×3
        BlockPos base = new BlockPos(7000, 64, 7000);
        ws.setBlockAndUpdate(base.offset(0, 0, 1), com.chigusa.partydungeon.registry.PDRegistry.PLAN_START.get().defaultBlockState());
        ws.setBlockAndUpdate(base.offset(1, 0, 1), com.chigusa.partydungeon.registry.PDRegistry.PLAN_ROOM.get().defaultBlockState());
        ws.setBlockAndUpdate(base.offset(2, 0, 1), com.chigusa.partydungeon.registry.PDRegistry.PLAN_ROOM.get().defaultBlockState());
        for (int x = 3; x < 6; x++) {
            for (int z = 0; z < 3; z++) {
                ws.setBlockAndUpdate(base.offset(x, 0, z), com.chigusa.partydungeon.registry.PDRegistry.PLAN_BOSS.get().defaultBlockState());
            }
        }
        com.chigusa.partydungeon.workshop.PlanBuilder.Plan plan =
                com.chigusa.partydungeon.workshop.PlanBuilder.read(ws, base.offset(4, 0, 1));
        helper.assertTrue(plan != null && plan.hasBoss() && plan.cellCount() == 12, "ボスの間取りが読めない");
        com.chigusa.partydungeon.workshop.PlanBuilder.build(server, player, plan, "ボステスト", 8, 3, 7,
                Blocks.STONE_BRICKS.defaultBlockState());
        DungeonDef d = PDData.get(server).findByName("ボステスト");
        helper.assertTrue(d != null && d.height == 20, "ボス部屋の高さが20になっていない");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!BlockJobs.isBusy(), "建築中"))
                .thenExecute(() -> {
                    BlockPos o = d.plotOrigin();
                    // ボス部屋の中心（マス 3〜5 → ブロック 24〜47、中心 36）
                    BlockPos c = o.offset(1 + 36, 0, 1 + 12);
                    helper.assertTrue(ws.getBlockState(c).is(Blocks.CHISELED_STONE_BRICKS), "ボス部屋の床がない");
                    helper.assertTrue(ws.getBlockState(c.above(15)).isAir(), "ボス部屋が高さ20になっていない");
                    helper.assertTrue(!ws.getBlockState(c.above(19)).isAir(), "ボス部屋の天井がない");
                    // 角（円の外）は床ではない
                    helper.assertTrue(!ws.getBlockState(o.offset(1 + 24, 0, 1)).is(Blocks.CHISELED_STONE_BRICKS),
                            "四角いまま（円になっていない）");
                    // 白の通路 → ボス部屋の入口がふさがっていない（境目のすぐ内側）
                    helper.assertTrue(ws.getBlockState(o.offset(1 + 24, 2, 1 + 8 + 3)).isAir(), "ボス部屋の入口がない");
                    PDData.get(server).dungeons.remove(d.id);
                    player.discard();
                })
                .thenSucceed();
    }

    private static List<Zombie> zombies(ServerLevel arena, BlockPos o) {
        return arena.getEntitiesOfClass(Zombie.class, new AABB(o.getX(), o.getY(), o.getZ(),
                o.getX() + 16, o.getY() + 8, o.getZ() + 16), z -> z.isAlive());
    }

    private PDGameTests() {
    }
}
