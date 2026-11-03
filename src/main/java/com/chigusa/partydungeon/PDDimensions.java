package com.chigusa.partydungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** このMODが使う2つのディメンション。中身は data/partydungeon/dimension/*.json で定義。 */
public final class PDDimensions {
    // 自動テストのときだけ別の世界に差し替えるため final にしていない

    /** ダンジョンを作るためのクリエイティブ作業場 */
    public static ResourceKey<Level> WORKSHOP = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PartyDungeon.MOD_ID, "workshop"));
    /** 挑戦用の部屋が並ぶ場所 */
    public static ResourceKey<Level> ARENA = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(PartyDungeon.MOD_ID, "arena"));

    /** 部屋・作業区画の床の高さ */
    public static final int BASE_Y = 64;
    /** 作業場の区画どうしの間隔 */
    public static final int PLOT_SPACING = 256;
    /** 挑戦用の部屋どうしの間隔 */
    public static final int ROOM_SPACING = 1024;

    public static BlockPos plotOrigin(int plot) {
        return new BlockPos(plot * PLOT_SPACING, BASE_Y, 0);
    }

    public static BlockPos roomOrigin(int room) {
        return new BlockPos(room * ROOM_SPACING, BASE_Y, 0);
    }

    private PDDimensions() {
    }
}
