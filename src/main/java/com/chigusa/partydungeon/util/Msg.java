package com.chigusa.partydungeon.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** チャットに出すメッセージの共通部品。 */
public final class Msg {
    private Msg() {
    }

    private static MutableComponent prefix() {
        return Component.literal("[PD] ").withStyle(ChatFormatting.GOLD);
    }

    public static Component info(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE));
    }

    public static Component ok(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    public static Component warn(String text) {
        return prefix().append(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    public static void info(ServerPlayer p, String text) {
        p.sendSystemMessage(info(text));
    }

    public static void ok(ServerPlayer p, String text) {
        p.sendSystemMessage(ok(text));
    }

    public static void warn(ServerPlayer p, String text) {
        p.sendSystemMessage(warn(text));
    }

    /** ランキング用：分:秒.百分の一秒 */
    public static String formatTime(long ticks) {
        long cs = ticks * 5;
        long sec = cs / 100;
        return String.format("%d:%02d.%02d", sec / 60, sec % 60, cs % 100);
    }

    /** 残り時間（ミリ秒）を「7分12秒」の形に */
    public static String formatWait(long ms) {
        long sec = (ms + 999) / 1000;
        return sec >= 60 ? (sec / 60) + "分" + (sec % 60) + "秒" : sec + "秒";
    }

    public static String formatTicks(long ticks) {
        long sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
