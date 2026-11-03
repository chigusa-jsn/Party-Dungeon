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
        d.start = new BlockPos(8, 1, 8);
        d.clear = ClearCondition.ALL;
        d.spawns.add(new SpawnPoint(new BlockPos(4, 1, 4), ResourceLocation.withDefaultNamespace("zombie"), 2, 0, false));
        data.dungeons.put(d.id, d);
        BlockJobs.runNow(BlockJobs.frame(ws, d.plotOrigin(), 16, 8, Blocks.STONE_BRICKS.defaultBlockState(), null));
        helper.assertTrue(ws.getBlockState(d.plotOrigin()).is(Blocks.STONE_BRICKS), "外枠の床ができていない");
        helper.assertTrue(ws.getBlockState(d.plotOrigin().offset(5, 3, 5)).isAir(), "外枠の中が空気になっていない");
        helper.assertTrue(ws.getBlockState(d.plotOrigin().offset(5, 7, 5)).is(Blocks.STONE_BRICKS), "天井ができていない");

        // 目印のブロック（チェスト）を置いて公開
        ws.setBlockAndUpdate(d.plotOrigin().offset(2, 1, 2), Blocks.CHEST.defaultBlockState());
        WorkshopManager.publish(server, player, d);
        helper.assertTrue(d.published, "公開できていない");

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

    private static List<Zombie> zombies(ServerLevel arena, BlockPos o) {
        return arena.getEntitiesOfClass(Zombie.class, new AABB(o.getX(), o.getY(), o.getZ(),
                o.getX() + 16, o.getY() + 8, o.getZ() + 16), z -> z.isAlive());
    }

    private PDGameTests() {
    }
}
