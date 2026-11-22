package com.chigusa.partydungeon.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.chigusa.partydungeon.net.PDNetwork;
import com.chigusa.partydungeon.workshop.Bundle;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/** まとめて書き出し／読み込みの、パソコン側（ファイルの保存・一覧・送信） */
public final class BundleClient {
    private static int downId = -1;
    private static int downNext;
    private static ByteArrayOutputStream downBuf;

    /** パソコンのマイクラのフォルダ/partydungeon/exports */
    public static Path dir() {
        return FMLPaths.GAMEDIR.get().resolve("partydungeon").resolve("exports");
    }

    private static void say(String s, ChatFormatting c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[Party Dungeon] " + s).withStyle(c), false);
        }
    }

    /** サーバーから書き出しファイルが届いた */
    public static void receive(PDNetwork.BundleDown p) {
        if (p.index() == 0 || p.id() != downId) {
            downId = p.id();
            downNext = 0;
            downBuf = new ByteArrayOutputStream();
        }
        if (p.index() != downNext) {
            downId = -1;
            say("書き出しファイルの受け取りがとちゅうで途切れました。", ChatFormatting.RED);
            return;
        }
        downBuf.writeBytes(p.data());
        downNext++;
        if (downNext < p.total()) {
            return;
        }
        downId = -1;
        try {
            Files.createDirectories(dir());
            Path f = dir().resolve(p.name().replaceAll("[\\\\/:*?\"<>|]", "_"));
            Files.write(f, downBuf.toByteArray());
            say("書き出しました！ パソコンの " + FMLPaths.GAMEDIR.get().relativize(f) + " に保存しました。", ChatFormatting.GREEN);
            say("別のワールドやサーバーで、製作メニューの「読み込み」から選べます。", ChatFormatting.GRAY);
        } catch (IOException e) {
            say("ファイルを保存できませんでした: " + e.getMessage(), ChatFormatting.RED);
        }
        downBuf = null;
    }

    /** 一覧の1行 */
    public record FileInfo(Path path, String source, long created, int dungeons, int quests, long bytes) {
    }

    /** パソコンにある書き出しファイル（新しい順） */
    public static List<FileInfo> list() {
        List<FileInfo> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) {
            return out;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir())) {
            s.filter(f -> f.getFileName().toString().endsWith(".pdb")).forEach(files::add);
        } catch (IOException e) {
            return out;
        }
        for (Path f : files) {
            try {
                CompoundTag t = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap());
                CompoundTag sum = t.getCompound("summary");
                out.add(new FileInfo(f, t.getString("source"), t.getLong("created"), sum.getInt("dungeons"),
                        sum.getInt("quests"), Files.size(f)));
            } catch (IOException | RuntimeException e) {
                // 読めないファイルは出さない
            }
        }
        out.sort(Comparator.comparingLong(FileInfo::created).reversed());
        return out;
    }

    /** ファイルをサーバーへ送る（サーバーで確認画面が開く） */
    public static void upload(Path f) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f);
        } catch (IOException e) {
            say("ファイルを読めませんでした: " + e.getMessage(), ChatFormatting.RED);
            return;
        }
        if (bytes.length > Bundle.MAX_BYTES) {
            say("ファイルが大きすぎます（64MB まで）。", ChatFormatting.RED);
            return;
        }
        int total = Math.max(1, (bytes.length + Bundle.UP_CHUNK - 1) / Bundle.UP_CHUNK);
        int id = (int) (System.nanoTime() & 0x7fffffff);
        for (int i = 0; i < total; i++) {
            int from = i * Bundle.UP_CHUNK;
            byte[] part = java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + Bundle.UP_CHUNK));
            PacketDistributor.sendToServer(new PDNetwork.BundleUp(id, i, total, part));
        }
        say("サーバーへ送っています…（" + (bytes.length / 1024) + " KB）", ChatFormatting.GRAY);
    }

    private BundleClient() {
    }
}
