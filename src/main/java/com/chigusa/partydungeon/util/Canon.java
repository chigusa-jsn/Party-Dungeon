package com.chigusa.partydungeon.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * NBT の中身から「指紋」を作る（キーの順番に左右されない）。
 * 同じ中身なら、どのパソコン・サーバーでも同じ指紋になる。
 * 書き出し・読み込みで「変わったかどうか」を見分けるのに使う。
 */
public final class Canon {
    public static String hash(@Nullable Tag... tags) {
        StringBuilder sb = new StringBuilder();
        for (Tag t : tags) {
            write(sb, t);
            sb.append('|');
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", d[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static void write(StringBuilder sb, @Nullable Tag t) {
        if (t == null) {
            sb.append("null");
        } else if (t instanceof CompoundTag c) {
            List<String> keys = new ArrayList<>(c.getAllKeys());
            Collections.sort(keys);
            sb.append('{');
            for (String k : keys) {
                sb.append(k).append(':');
                write(sb, c.get(k));
                sb.append(',');
            }
            sb.append('}');
        } else if (t instanceof ListTag l) {
            sb.append('[');
            for (Tag e : l) {
                write(sb, e);
                sb.append(',');
            }
            sb.append(']');
        } else {
            sb.append(t);
        }
    }

    private Canon() {
    }
}
