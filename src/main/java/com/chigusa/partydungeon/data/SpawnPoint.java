package com.chigusa.partydungeon.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 敵の出現ポイント。
 *
 * @param rel           ダンジョンの角からの相対位置
 * @param entity        出すモブのID（ほかのMODのモブも可）
 * @param count         一度に出す数
 * @param triggerRadius プレイヤーがこの距離まで近づいたら出す。0 なら開始と同時に出す
 * @param boss          ボス扱い（クリア条件「ボス」で使う）
 */
public record SpawnPoint(BlockPos rel, ResourceLocation entity, int count, int triggerRadius, boolean boss) {

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putIntArray("pos", DungeonDef.posToArray(rel));
        t.putString("entity", entity.toString());
        t.putInt("count", count);
        t.putInt("radius", triggerRadius);
        t.putBoolean("boss", boss);
        return t;
    }

    public static SpawnPoint load(CompoundTag t) {
        return new SpawnPoint(
                DungeonDef.arrayToPos(t.getIntArray("pos")),
                ResourceLocation.parse(t.getString("entity")),
                Math.max(1, t.getInt("count")),
                Math.max(0, t.getInt("radius")),
                t.getBoolean("boss"));
    }
}
