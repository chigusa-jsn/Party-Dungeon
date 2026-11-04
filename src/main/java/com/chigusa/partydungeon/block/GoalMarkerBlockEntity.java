package com.chigusa.partydungeon.block;

import com.chigusa.partydungeon.registry.PDRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** ゴールの範囲 */
public class GoalMarkerBlockEntity extends BlockEntity {
    public int radius = 2;

    public GoalMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(PDRegistry.GOAL_MARKER_BE.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("radius", radius);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        radius = tag.contains("radius") ? Math.max(1, Math.min(32, tag.getInt("radius"))) : 2;
    }
}
