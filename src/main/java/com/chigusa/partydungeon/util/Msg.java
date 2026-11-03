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

    public static String formatTicks(long ticks) {
        long sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
