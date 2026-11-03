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
    public int nextPlot;
    public int nextId = 1;

    public static PDData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PDData::new, PDData::load, null), NAME);
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
        data.nextPlot = tag.getInt("nextPlot");
        data.nextId = Math.max(1, tag.getInt("nextId"));
        return data;
    }
}
