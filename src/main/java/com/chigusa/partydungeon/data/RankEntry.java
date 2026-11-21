package com.chigusa.partydungeon.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** ランキングの1行：クリアタイムと、挑んだメンバー（最大5人まで記録） */
public record RankEntry(long ticks, List<UUID> ids, List<String> names, long when) {
    public static final int KEEP = 5;
    public static final int MAX_MEMBERS = 5;

    /** 同じ顔ぶれか（並び順は関係なし） */
    public boolean sameTeam(List<UUID> other) {
        return ids.size() == other.size() && ids.containsAll(other);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("ticks", ticks);
        t.putLong("when", when);
        ListTag l = new ListTag();
        for (int i = 0; i < ids.size(); i++) {
            CompoundTag m = new CompoundTag();
            m.putUUID("id", ids.get(i));
            m.putString("name", i < names.size() ? names.get(i) : "?");
            l.add(m);
        }
        t.put("members", l);
        return t;
    }

    public static RankEntry load(CompoundTag t) {
        List<UUID> ids = new ArrayList<>();
        List<String> names = new ArrayList<>();
        ListTag l = t.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag m = l.getCompound(i);
            ids.add(m.getUUID("id"));
            names.add(m.getString("name"));
        }
        return new RankEntry(t.getLong("ticks"), ids, names, t.getLong("when"));
    }

}
