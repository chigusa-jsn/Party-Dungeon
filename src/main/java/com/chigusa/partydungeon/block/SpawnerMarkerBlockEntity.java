package com.chigusa.partydungeon.block;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.registry.PDRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 敵の出現ブロックの設定（どのモブを、何体、いつ出すか） */
public class SpawnerMarkerBlockEntity extends BlockEntity {
    @Nullable
    public ResourceLocation entity;
    public int count = 1;
    public int radius;
    public boolean boss;

    public SpawnerMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(PDRegistry.SPAWNER_MARKER_BE.get(), pos, state);
    }

    public CompoundTag saveData() {
        CompoundTag t = new CompoundTag();
        if (entity != null) {
            t.putString("entity", entity.toString());
        }
        t.putInt("count", count);
        t.putInt("radius", radius);
        t.putBoolean("boss", boss);
        return t;
    }

    public void loadData(CompoundTag t) {
        entity = t.contains("entity") ? ResourceLocation.tryParse(t.getString("entity")) : null;
        count = Math.max(1, Math.min(64, t.contains("count") ? t.getInt("count") : 1));
        radius = Math.max(0, Math.min(128, t.getInt("radius")));
        boss = t.getBoolean("boss");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.merge(saveData());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadData(tag);
    }
}
