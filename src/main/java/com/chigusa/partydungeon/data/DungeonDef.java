package com.chigusa.partydungeon.data;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PDDimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1つのダンジョンの設定（建物そのものは DungeonTemplate に別ファイルで保存）。
 * 作業場の区画（plot）と1対1で対応する。
 */
public final class DungeonDef {
    public final String id;
    public String name;
    public final UUID owner;
    public final int plot;
    public final int size;
    public final int height;

    @Nullable
    public BlockPos start;
    @Nullable
    public BlockPos goal;
    public int goalRadius = 2;
    public ClearCondition clear = ClearCondition.ALL;
    public final List<SpawnPoint> spawns = new ArrayList<>();
    /** 難易度ごとの報酬 */
    public final EnumMap<Difficulty, List<Reward>> rewards = new EnumMap<>(Difficulty.class);
    /** 公開済みか（公開すると「遊ぶ」画面に出る） */
    public boolean published;
    /** 公開した回数（ファイル更新の目印） */
    public int revision;

    public DungeonDef(String id, String name, UUID owner, int plot, int size, int height) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.plot = plot;
        this.size = size;
        this.height = height;
    }

    public List<Reward> rewards(Difficulty diff) {
        return rewards.computeIfAbsent(diff, k -> new ArrayList<>());
    }

    /** 作業場での区画の角 */
    public BlockPos plotOrigin() {
        return PDDimensions.plotOrigin(plot);
    }

    /** 作業場の中の位置が、この区画に入っているか */
    public boolean containsWorld(BlockPos worldPos) {
        BlockPos o = plotOrigin();
        return worldPos.getX() >= o.getX() && worldPos.getX() < o.getX() + size
                && worldPos.getY() >= o.getY() && worldPos.getY() < o.getY() + height
                && worldPos.getZ() >= o.getZ() && worldPos.getZ() < o.getZ() + size;
    }

    public boolean containsRel(BlockPos rel) {
        return rel.getX() >= 0 && rel.getX() < size && rel.getY() >= 0 && rel.getY() < height
                && rel.getZ() >= 0 && rel.getZ() < size;
    }

    /** 公開前のチェック。問題があれば日本語の説明を返す。なければ null */
    @Nullable
    public String validate() {
        if (start == null) {
            return "スタート地点が決まっていません（/pd setstart）";
        }
        switch (clear) {
            case ALL -> {
                if (spawns.isEmpty()) {
                    return "クリア条件が「敵を全部倒す」ですが、敵が1体も登録されていません（/pd spawn add）";
                }
            }
            case BOSS -> {
                if (spawns.stream().noneMatch(SpawnPoint::boss)) {
                    return "クリア条件が「ボス」ですが、ボスが登録されていません（/pd spawn add <モブ> <数> <距離> true）";
                }
            }
            case GOAL -> {
                if (goal == null) {
                    return "クリア条件が「ゴール」ですが、ゴール地点が決まっていません（/pd setgoal）";
                }
            }
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("name", name);
        t.putUUID("owner", owner);
        t.putInt("plot", plot);
        t.putInt("size", size);
        t.putInt("height", height);
        if (start != null) {
            t.putIntArray("start", posToArray(start));
        }
        if (goal != null) {
            t.putIntArray("goal", posToArray(goal));
        }
        t.putInt("goalRadius", goalRadius);
        t.putString("clear", clear.id);
        ListTag list = new ListTag();
        for (SpawnPoint s : spawns) {
            list.add(s.save());
        }
        t.put("spawns", list);
        CompoundTag rw = new CompoundTag();
        rewards.forEach((diff, entries) -> {
            ListTag l = new ListTag();
            for (Reward r : entries) {
                l.add(r.save());
            }
            rw.put(diff.id, l);
        });
        t.put("rewards", rw);
        t.putBoolean("published", published);
        t.putInt("revision", revision);
        return t;
    }

    public static DungeonDef load(CompoundTag t) {
        DungeonDef d = new DungeonDef(t.getString("id"), t.getString("name"), t.getUUID("owner"),
                t.getInt("plot"), t.getInt("size"), t.getInt("height"));
        if (t.contains("start")) {
            d.start = arrayToPos(t.getIntArray("start"));
        }
        if (t.contains("goal")) {
            d.goal = arrayToPos(t.getIntArray("goal"));
        }
        d.goalRadius = t.getInt("goalRadius");
        d.clear = ClearCondition.byId(t.getString("clear"));
        ListTag list = t.getList("spawns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            d.spawns.add(SpawnPoint.load(list.getCompound(i)));
        }
        CompoundTag rw = t.getCompound("rewards");
        for (Difficulty diff : Difficulty.values()) {
            ListTag l = rw.getList(diff.id, Tag.TAG_COMPOUND);
            if (l.isEmpty()) {
                continue;
            }
            List<Reward> entries = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) {
                entries.add(Reward.load(l.getCompound(i)));
            }
            d.rewards.put(diff, entries);
        }
        d.published = t.getBoolean("published");
        d.revision = t.getInt("revision");
        return d;
    }

    /** 挑戦中に設定が書き換わっても影響しないよう、コピーを作る */
    public DungeonDef copy() {
        return load(save());
    }

    public static int[] posToArray(BlockPos p) {
        return new int[] {p.getX(), p.getY(), p.getZ()};
    }

    public static BlockPos arrayToPos(int[] a) {
        if (a.length < 3) {
            return BlockPos.ZERO;
        }
        return new BlockPos(a[0], a[1], a[2]);
    }
}
