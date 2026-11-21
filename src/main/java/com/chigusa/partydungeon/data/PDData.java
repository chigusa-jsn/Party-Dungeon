package com.chigusa.partydungeon.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * ワールドに保存されるデータ（world/data/partydungeon.dat）。
 * ・ダンジョンの設定一覧
 * ・作業場やダンジョンに入る前の場所（サーバー再起動後に戻すため）
 * ・ブロックが残っている部屋（再起動後に片付けるため）
 */
public final class PDData extends SavedData {
    private static final String NAME = "partydungeon";

    public final Map<String, DungeonDef> dungeons = new LinkedHashMap<>();
    /** ダンジョンに入る前の場所 */
    public final Map<UUID, ReturnInfo> returns = new HashMap<>();
    /** 作業場に入る前の場所（テストプレイ中も保持するため別に持つ） */
    public final Map<UUID, ReturnInfo> workshopReturns = new HashMap<>();
    /** 部屋番号 → 片付けが必要な範囲（size, height） */
    public final Map<Integer, int[]> dirtyRooms = new HashMap<>();
    /** プレイヤーごとの、解放したダンジョン（ID） */
    public final Map<UUID, java.util.Set<String>> unlocked = new HashMap<>();
    /** ランキング：ダンジョンID → 難易度ID → 上位（速い順） */
    public final Map<String, Map<String, java.util.List<RankEntry>>> rankings = new HashMap<>();
    /** 再挑戦の待ち時間：プレイヤー → ダンジョンID → 挑めるようになる時刻（ミリ秒） */
    public final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    public int nextPlot;
    public int nextId = 1;

    public static PDData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PDData::new, PDData::load, null), NAME);
    }

    /** 新しい区画の番号を確保する。大きいダンジョンは隣の区画もまとめて使う */
    public int allocatePlot(int size) {
        int plot = nextPlot;
        int slots = Math.max(1, (size + 16 + com.chigusa.partydungeon.PDDimensions.PLOT_SPACING - 1)
                / com.chigusa.partydungeon.PDDimensions.PLOT_SPACING);
        nextPlot += slots;
        setDirty();
        return plot;
    }

    @Nullable
    public DungeonDef findByPlotPos(BlockPos worldPos) {
        for (DungeonDef d : dungeons.values()) {
            if (d.containsWorld(worldPos)) {
                return d;
            }
        }
        return null;
    }

    @Nullable
    /** そのダンジョンに挑めるか（解放がいらないか、解放ずみ） */
    public boolean isUnlocked(UUID player, DungeonDef d) {
        return !d.locked || unlocked.getOrDefault(player, java.util.Set.of()).contains(d.id);
    }

    public DungeonDef findByName(String nameOrId) {
        DungeonDef byId = dungeons.get(nameOrId);
        if (byId != null) {
            return byId;
        }
        for (DungeonDef d : dungeons.values()) {
            if (d.name.equals(nameOrId)) {
                return d;
            }
        }
        return null;
    }

    public java.util.List<RankEntry> ranking(String dungeonId, String diffId) {
        return rankings.getOrDefault(dungeonId, Map.of()).getOrDefault(diffId, java.util.List.of());
    }

    /** クリアの記録を入れる。上位に入れば順位（1〜）、入らなければ 0 */
    public int record(String dungeonId, String diffId, RankEntry e) {
        java.util.List<RankEntry> list = new java.util.ArrayList<>(ranking(dungeonId, diffId));
        for (RankEntry old : list) {
            if (old.sameTeam(e.ids())) {
                if (old.ticks() <= e.ticks()) {
                    return 0;
                }
                list.remove(old);
                break;
            }
        }
        list.add(e);
        list.sort(java.util.Comparator.comparingLong(RankEntry::ticks).thenComparingLong(RankEntry::when));
        while (list.size() > RankEntry.KEEP) {
            list.remove(list.size() - 1);
        }
        rankings.computeIfAbsent(dungeonId, k -> new HashMap<>()).put(diffId, list);
        setDirty();
        int rank = list.indexOf(e);
        return rank < 0 ? 0 : rank + 1;
    }

    /** あと何ミリ秒待つ必要があるか（0 = すぐ挑める） */
    public long cooldownLeft(UUID player, String dungeonId) {
        Long until = cooldowns.getOrDefault(player, Map.of()).get(dungeonId);
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(UUID player, String dungeonId, long until) {
        cooldowns.computeIfAbsent(player, k -> new HashMap<>()).put(dungeonId, until);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (DungeonDef d : dungeons.values()) {
            list.add(d.save());
        }
        tag.put("dungeons", list);

        CompoundTag ret = new CompoundTag();
        returns.forEach((id, info) -> ret.put(id.toString(), info.save()));
        tag.put("returns", ret);
        CompoundTag wret = new CompoundTag();
        workshopReturns.forEach((id, info) -> wret.put(id.toString(), info.save()));
        tag.put("workshopReturns", wret);

        CompoundTag dirty = new CompoundTag();
        dirtyRooms.forEach((room, sz) -> dirty.putIntArray(Integer.toString(room), sz));
        tag.put("dirtyRooms", dirty);

        CompoundTag un = new CompoundTag();
        unlocked.forEach((id, set) -> {
            ListTag l = new ListTag();
            for (String s : set) l.add(net.minecraft.nbt.StringTag.valueOf(s));
            un.put(id.toString(), l);
        });
        tag.put("unlocked", un);
        CompoundTag rk = new CompoundTag();
        rankings.forEach((did, byDiff) -> {
            CompoundTag dt = new CompoundTag();
            byDiff.forEach((diff, entries) -> {
                ListTag l = new ListTag();
                for (RankEntry e : entries) l.add(e.save());
                dt.put(diff, l);
            });
            rk.put(did, dt);
        });
        tag.put("rankings", rk);
        CompoundTag cd = new CompoundTag();
        long now = System.currentTimeMillis();
        cooldowns.forEach((pid, m) -> {
            CompoundTag pt = new CompoundTag();
            m.forEach((did, until) -> {
                if (until > now) pt.putLong(did, until);
            });
            if (!pt.isEmpty()) cd.put(pid.toString(), pt);
        });
        tag.put("cooldowns", cd);
        tag.putInt("nextPlot", nextPlot);
        tag.putInt("nextId", nextId);
        return tag;
    }

    private static PDData load(CompoundTag tag, HolderLookup.Provider registries) {
        PDData data = new PDData();
        ListTag list = tag.getList("dungeons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            DungeonDef d = DungeonDef.load(list.getCompound(i));
            data.dungeons.put(d.id, d);
        }
        CompoundTag ret = tag.getCompound("returns");
        for (String key : ret.getAllKeys()) {
            try {
                data.returns.put(UUID.fromString(key), ReturnInfo.load(ret.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // 壊れたデータは読み飛ばす
            }
        }
        CompoundTag wret = tag.getCompound("workshopReturns");
        for (String key : wret.getAllKeys()) {
            try {
                data.workshopReturns.put(UUID.fromString(key), ReturnInfo.load(wret.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // 読み飛ばす
            }
        }
        CompoundTag dirty = tag.getCompound("dirtyRooms");
        for (String key : dirty.getAllKeys()) {
            try {
                data.dirtyRooms.put(Integer.parseInt(key), dirty.getIntArray(key));
            } catch (NumberFormatException ignored) {
                // 読み飛ばす
            }
        }
        CompoundTag un = tag.getCompound("unlocked");
        for (String key : un.getAllKeys()) {
            try {
                ListTag l = un.getList(key, Tag.TAG_STRING);
                java.util.Set<String> set = new java.util.LinkedHashSet<>();
                for (int i = 0; i < l.size(); i++) set.add(l.getString(i));
                data.unlocked.put(UUID.fromString(key), set);
            } catch (IllegalArgumentException ignored) {
                // 読み飛ばす
            }
        }
        CompoundTag rk = tag.getCompound("rankings");
        for (String did : rk.getAllKeys()) {
            CompoundTag dt = rk.getCompound(did);
            Map<String, java.util.List<RankEntry>> byDiff = new HashMap<>();
            for (String diff : dt.getAllKeys()) {
                ListTag l = dt.getList(diff, Tag.TAG_COMPOUND);
                java.util.List<RankEntry> entries = new java.util.ArrayList<>();
                for (int i = 0; i < l.size(); i++) entries.add(RankEntry.load(l.getCompound(i)));
                byDiff.put(diff, entries);
            }
            data.rankings.put(did, byDiff);
        }
        CompoundTag cd = tag.getCompound("cooldowns");
        for (String key : cd.getAllKeys()) {
            try {
                CompoundTag pt = cd.getCompound(key);
                Map<String, Long> m = new HashMap<>();
                for (String did : pt.getAllKeys()) m.put(did, pt.getLong(did));
                data.cooldowns.put(UUID.fromString(key), m);
            } catch (IllegalArgumentException ignored) {
                // 読み飛ばす
            }
        }
        data.nextPlot = tag.getInt("nextPlot");
        data.nextId = Math.max(1, tag.getInt("nextId"));
        return data;
    }
}
