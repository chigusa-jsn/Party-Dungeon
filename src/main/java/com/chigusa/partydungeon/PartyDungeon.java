package com.chigusa.partydungeon;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Party Dungeon のメインクラス。
 * MOD が読み込まれたときに最初に呼ばれ、設定や各機能を登録する。
 */
@Mod(PartyDungeon.MOD_ID)
public final class PartyDungeon {
    public static final String MOD_ID = "partydungeon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PartyDungeon(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, PDConfig.SPEC);
        LOGGER.info("[Party Dungeon] 読み込みました");
    }
}
