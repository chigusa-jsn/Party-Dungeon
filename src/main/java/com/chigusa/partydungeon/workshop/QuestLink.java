package com.chigusa.partydungeon.workshop;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PartyDungeon;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

/**
 * jsn-Craft の依頼を、まとめて書き出し／読み込みに入れるための橋渡し。
 * jsn-Craft が入っていないときは何もしない（リフレクションで呼ぶ）。
 */
final class QuestLink {
    private static final String CLASS = "com.chigusa.jsncraft.compat.QuestBundle";

    static boolean available() {
        return ModList.get().isLoaded("jsn_craft") && m("export", MinecraftServer.class, Map.class) != null;
    }

    @Nullable
    private static Method m(String name, Class<?>... args) {
        try {
            return Class.forName(CLASS).getMethod(name, args);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** 依頼を全部（ダンジョンIDは uid に置き換えて） */
    @Nullable
    static CompoundTag export(MinecraftServer server, Map<String, String> dungeonIdToUid) {
        try {
            Method m = m("export", MinecraftServer.class, Map.class);
            return m == null ? null : (CompoundTag) m.invoke(null, server, dungeonIdToUid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 依頼を書き出せませんでした", e);
            return null;
        }
    }

    /** 1つずつ「新しい／同じ／更新／こちらで変更あり」 */
    static ListTag preview(MinecraftServer server, CompoundTag part, Map<String, String> dungeonIdToUid) {
        try {
            Method m = m("preview", MinecraftServer.class, CompoundTag.class, Map.class);
            return m == null ? new ListTag() : (ListTag) m.invoke(null, server, part, dungeonIdToUid);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 依頼を確認できませんでした", e);
            return new ListTag();
        }
    }

    /** 選んだ依頼を入れる。結果のメッセージ */
    static String apply(MinecraftServer server, CompoundTag part, List<String> uids, Map<String, String> dungeonUidToRef) {
        try {
            Method m = m("apply", MinecraftServer.class, CompoundTag.class, List.class, Map.class);
            return m == null ? "jsn-Craft が入っていないので、依頼は読み込めませんでした。"
                    : (String) m.invoke(null, server, part, uids, dungeonUidToRef);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 依頼を読み込めませんでした", e);
            return "依頼の読み込みでエラーが起きました（ログを確認してください）。";
        }
    }

    private QuestLink() {
    }
}
