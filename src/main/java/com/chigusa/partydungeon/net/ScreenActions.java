package com.chigusa.partydungeon.net;

import java.nio.file.Files;

import com.chigusa.partydungeon.block.GoalMarkerBlockEntity;
import com.chigusa.partydungeon.block.SpawnerMarkerBlockEntity;
import com.chigusa.partydungeon.data.ClearCondition;
import com.chigusa.partydungeon.data.Difficulty;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.menu.RewardMenu;
import com.chigusa.partydungeon.run.RunManager;
import com.chigusa.partydungeon.template.DungeonTemplate;
import com.chigusa.partydungeon.util.Msg;
import com.chigusa.partydungeon.workshop.PlanBuilder;
import com.chigusa.partydungeon.workshop.WorkshopManager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** 専用画面のボタンが押されたときのサーバー側の処理 */
public final class ScreenActions {

    public static void handle(ServerPlayer p, String screen, String action, CompoundTag data) {
        if (!p.hasPermissions(2)) {
            return;
        }
        MinecraftServer server = p.server;
        switch (screen) {
            case "spawner" -> saveSpawner(p, data);
            case "goal" -> saveGoal(p, data);
            case "plan" -> buildPlan(p, data);
            case "manager" -> manager(server, p, action, data);
            default -> {
            }
        }
    }

    private static boolean nearby(ServerPlayer p, BlockPos pos) {
        return p.level().isLoaded(pos) && p.blockPosition().distSqr(pos) < 64 * 64;
    }

    private static void saveSpawner(ServerPlayer p, CompoundTag data) {
        BlockPos pos = BlockPos.of(data.getLong("pos"));
        if (!nearby(p, pos) || !(p.level().getBlockEntity(pos) instanceof SpawnerMarkerBlockEntity be)) {
            return;
        }
        be.loadData(data);
        be.setChanged();
        Msg.ok(p, be.entity == null ? "保存しました（モブが選ばれていません）。"
                : "保存しました: " + be.entity + " ×" + be.count + (be.boss ? "（ボス）" : ""));
    }

    private static void saveGoal(ServerPlayer p, CompoundTag data) {
        BlockPos pos = BlockPos.of(data.getLong("pos"));
        if (!nearby(p, pos) || !(p.level().getBlockEntity(pos) instanceof GoalMarkerBlockEntity be)) {
            return;
        }
        be.radius = Math.max(1, Math.min(32, data.getInt("radius")));
        be.setChanged();
        Msg.ok(p, "ゴールの範囲を " + be.radius + " にしました。");
    }

    private static void buildPlan(ServerPlayer p, CompoundTag data) {
        BlockPos pos = BlockPos.of(data.getLong("pos"));
        if (!nearby(p, pos)) {
            return;
        }
        PlanBuilder.Plan plan = PlanBuilder.read(p.serverLevel(), pos);
        if (plan == null) {
            Msg.warn(p, "間取りが見つかりません。");
            return;
        }
        PlanBuilder.build(p.server, p, plan, data.getString("name").trim(), Math.max(3, data.getInt("scale")),
                Math.max(1, data.getInt("corridor")), data.getInt("height"), Blocks.STONE_BRICKS.defaultBlockState());
    }

    // ---------------------------------------------------------------- 製作メニュー

    /** 製作メニューを（最新の内容で）開く */
    public static void openManager(MinecraftServer server, ServerPlayer p, String selected) {
        PDData data = PDData.get(server);
        CompoundTag t = new CompoundTag();
        ListTag list = new ListTag();
        for (DungeonDef d : data.dungeons.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("id", d.id);
            e.putString("name", d.name);
            e.putBoolean("published", d.published);
            e.putInt("revision", d.revision);
            e.putString("clear", d.clear.label);
            e.putInt("size", d.size);
            e.putInt("height", d.height);
            e.putInt("enemies", d.spawns.size());
            int rewards = 0;
            for (Difficulty diff : Difficulty.values()) {
                rewards += d.rewards(diff).size();
            }
            e.putInt("rewards", rewards);
            list.add(e);
        }
        t.put("dungeons", list);
        t.putBoolean("inWorkshop", WorkshopManager.isIn(p));
        DungeonDef here = WorkshopManager.isIn(p) ? data.findByPlotPos(p.blockPosition()) : null;
        t.putString("selected", !selected.isEmpty() ? selected : here != null ? here.id : "");
        PDNetwork.openScreen(p, "manager", t);
    }

    private static void manager(MinecraftServer server, ServerPlayer p, String action, CompoundTag data) {
        PDData pd = PDData.get(server);
        String id = data.getString("id");
        DungeonDef d = pd.dungeons.get(id);
        switch (action) {
            case "refresh" -> openManager(server, p, id);
            case "enter" -> {
                WorkshopManager.enter(server, p);
                openManager(server, p, id);
            }
            case "exit" -> WorkshopManager.exit(server, p);
            case "lobby" -> {
                WorkshopManager.enterLobby(server, p);
            }
            case "create" -> {
                p.closeContainer();
                WorkshopManager.create(server, p, data.getString("name").trim(),
                        Math.max(16, Math.min(PlanBuilder.MAX_SIZE, data.getInt("size"))),
                        Math.max(8, Math.min(96, data.getInt("height"))), Blocks.STONE_BRICKS.defaultBlockState());
            }
            default -> {
                if (d == null) {
                    return;
                }
                dungeonAction(server, p, pd, d, action, data);
            }
        }
    }

    private static void dungeonAction(MinecraftServer server, ServerPlayer p, PDData pd, DungeonDef d, String action,
            CompoundTag data) {
        switch (action) {
            case "goto" -> {
                if (!WorkshopManager.isIn(p)) {
                    WorkshopManager.enter(server, p);
                }
                BlockPos s = d.plotOrigin().offset(d.start == null ? new BlockPos(d.size / 2, 1, d.size / 2) : d.start);
                p.teleportTo(WorkshopManager.workshop(server), s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5,
                        p.getYRot(), 0);
            }
            case "publish" -> {
                WorkshopManager.publish(server, p, d);
                openManager(server, p, d.id);
            }
            case "testplay" -> {
                WorkshopManager.publish(server, p, d);
                if (d.published && d.validate() == null) {
                    Difficulty diff = Difficulty.byId(data.getString("difficulty"));
                    RunManager.start(server, p, d, diff);
                }
            }
            case "unpublish" -> {
                d.published = false;
                pd.setDirty();
                Msg.ok(p, "「" + d.name + "」を非公開にしました。");
                openManager(server, p, d.id);
            }
            case "clear" -> {
                d.clear = switch (d.clear) {
                    case ALL -> ClearCondition.BOSS;
                    case BOSS -> ClearCondition.GOAL;
                    case GOAL -> ClearCondition.ALL;
                };
                pd.setDirty();
                openManager(server, p, d.id);
            }
            case "rename" -> {
                String name = data.getString("name").trim();
                if (name.isEmpty() || (pd.findByName(name) != null && pd.findByName(name) != d)) {
                    Msg.warn(p, "その名前は使えません（空か、もう使われています）。");
                } else {
                    d.name = name;
                    pd.setDirty();
                    Msg.ok(p, "名前を「" + name + "」にしました。");
                }
                openManager(server, p, d.id);
            }
            case "rewards" -> RewardMenu.open(server, p, d.id, Difficulty.NORMAL);
            case "delete" -> {
                if (RunManager.activeCount(d.id) > 0) {
                    Msg.warn(p, "挑戦中のパーティがいるので、今は消せません。");
                    return;
                }
                pd.dungeons.remove(d.id);
                pd.setDirty();
                try {
                    Files.deleteIfExists(DungeonTemplate.file(server, d.id));
                } catch (Exception ignored) {
                    // 消せなくても問題ない
                }
                ServerLevel ws = WorkshopManager.workshop(server);
                if (ws != null) {
                    BlockJobs.add(BlockJobs.clear(ws, d.plotOrigin(), d.size, d.height, d.size, null));
                }
                Msg.ok(p, "「" + d.name + "」を削除しました。");
                openManager(server, p, "");
            }
            default -> {
            }
        }
    }

    private ScreenActions() {
    }
}
