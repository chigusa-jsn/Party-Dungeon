package com.chigusa.partydungeon.api;

import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * ほかのMOD（jsn-Craft・jsn-Phone）とのつなぎ。どれも、相手がいなくても動く（リフレクションで呼び合う）。
 */
public final class PDApi {
    /** 公開中のダンジョン [id, 名前] */
    public static List<String[]> dungeons(MinecraftServer server) {
        List<String[]> out = new ArrayList<>();
        for (DungeonDef d : PDData.get(server).dungeons.values()) if (d.published) out.add(new String[]{d.id, d.name});
        return out;
    }

    /** ダンジョンをその人に解放する（名前かID）。解放したダンジョンの名前、見つからなければ null */
    public static String unlock(ServerPlayer p, String nameOrId) {
        PDData data = PDData.get(p.server);
        DungeonDef d = data.findByName(nameOrId);
        if (d == null) return null;
        data.unlocked.computeIfAbsent(p.getUUID(), k -> new LinkedHashSet<>()).add(d.id);
        data.setDirty();
        return d.name;
    }

    public static boolean isUnlocked(ServerPlayer p, String nameOrId) {
        PDData data = PDData.get(p.server);
        DungeonDef d = data.findByName(nameOrId);
        return d != null && data.isUnlocked(p.getUUID(), d);
    }

    // ---------------- こちらから呼ぶ ----------------
    private static Method clearHook, hintHook, newsHook;
    private static boolean tried;

    private static synchronized void init() {
        if (tried) return;
        tried = true;
        if (ModList.get().isLoaded("jsn_craft")) {
            try {
                Class<?> c = Class.forName("com.chigusa.jsncraft.compat.DungeonBridge");
                clearHook = c.getMethod("onClear", ServerPlayer.class, String.class, String.class, String.class);
                hintHook = c.getMethod("unlockHint", String.class, String.class);
            } catch (Throwable t) {
                PartyDungeon.LOGGER.warn("[Party Dungeon] jsn-Craft とつながらなかった: {}", t.toString());
            }
        }
        if (ModList.get().isLoaded("jsn_phone")) {
            try {
                newsHook = Class.forName("com.chigusa.jsnphone.PhoneApi").getMethod("news", ServerPlayer.class, String.class, String.class);
            } catch (Throwable ignored) {
            }
        }
    }

    /** クリアしたとき（パーティの1人ずつ） */
    public static void cleared(ServerPlayer p, DungeonDef d, String difficultyId, String difficultyLabel, boolean announce) {
        init();
        try {
            if (clearHook != null) clearHook.invoke(null, p, d.id, d.name, difficultyId);
        } catch (Throwable t) {
            PartyDungeon.LOGGER.warn("[Party Dungeon] 依頼の更新に失敗: {}", t.toString());
        }
        try {
            if (announce && newsHook != null) newsHook.invoke(null, p, p.getName().getString() + " のパーティが「" + d.name + "」（" + difficultyLabel + "）をクリア！", "dungeon");
        } catch (Throwable ignored) {
        }
    }

    /** まだのダンジョンを、どの依頼で解放できるか（わからなければ空） */
    public static String hint(DungeonDef d) {
        init();
        try {
            if (hintHook != null) return (String) hintHook.invoke(null, d.id, d.name);
        } catch (Throwable ignored) {
        }
        return "";
    }

    private PDApi() {}
}
