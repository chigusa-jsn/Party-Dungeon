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

    /** 1tick に置き換えるブロックの数 */
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK = BUILDER
            .comment("部屋の組み立て・リセットで 1tick（1/20秒）に置き換えるブロック数。重ければ減らしてください。")
            .defineInRange("rooms.blocksPerTick", 20000, 500, 500000);

    /** クリア後、元の場所に戻るまでの秒数 */
    public static final ModConfigSpec.IntValue RETURN_DELAY_SECONDS = BUILDER
            .comment("クリアしてから元の場所に戻るまでの秒数。")
            .defineInRange("run.returnDelaySeconds", 10, 0, 300);

    /** ダンジョン内で死んでも持ち物を失わない */
    public static final ModConfigSpec.BooleanValue KEEP_INVENTORY = BUILDER
            .comment("ダンジョンの中で死んでも、持ち物と経験値を失わない（カオスは chaosLosesItems に従う）。")
            .define("run.keepInventory", true);

    /** カオスで死んだら全ロス */
    public static final ModConfigSpec.BooleanValue CHAOS_LOSES_ITEMS = BUILDER
            .comment("難易度カオスで死んだら、持ち物と経験値をすべて失う。")
            .define("run.chaosLosesItems", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private PDConfig() {
    }
}
