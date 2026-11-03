package com.chigusa.partydungeon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * サーバー設定（world/serverconfig/partydungeon-server.toml に作られる）。
 */
public final class PDConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 同時に使える挑戦用の部屋の数（全ダンジョン共用） */
    public static final ModConfigSpec.IntValue ROOM_COUNT = BUILDER
            .comment("同時に使える挑戦用の部屋の数（全ダンジョン共用）。サーバーが重ければ減らしてください。")
            .defineInRange("rooms.count", 5, 1, 64);

    /** 1つのダンジョンが同時に使える部屋の上限（0 = 上限なし） */
    public static final ModConfigSpec.IntValue ROOMS_PER_DUNGEON = BUILDER
            .comment("1つのダンジョンが同時に使える部屋の上限。0 で上限なし。")
            .defineInRange("rooms.maxPerDungeon", 0, 0, 64);

    /** パーティの最大人数 */
    public static final ModConfigSpec.IntValue MAX_PARTY_SIZE = BUILDER
            .comment("パーティの最大人数。")
            .defineInRange("party.maxSize", 5, 1, 20);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PDConfig() {
    }
}
