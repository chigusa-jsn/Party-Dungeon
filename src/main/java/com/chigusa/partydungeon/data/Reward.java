package com.chigusa.partydungeon.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * 報酬1つぶん。
 *
 * @param item   アイテム（エンチャントや名前なども含めてそのまま保存）
 * @param chance 出る確率（1〜100 %）
 */
public record Reward(CompoundTag item, int chance) {

    public static Reward of(ItemStack stack, int chance, HolderLookup.Provider registries) {
        return new Reward((CompoundTag) stack.save(registries), chance);
    }

    public ItemStack stack(HolderLookup.Provider registries) {
        return ItemStack.parseOptional(registries, item);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("item", item.copy());
        t.putInt("chance", chance);
        return t;
    }

    public static Reward load(CompoundTag t) {
        return new Reward(t.getCompound("item"), Math.max(1, Math.min(100, t.getInt("chance"))));
    }
}
