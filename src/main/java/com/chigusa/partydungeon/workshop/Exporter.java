package com.chigusa.partydungeon.workshop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.template.DungeonTemplate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.loading.FMLPaths;

/**
 * ダンジョンを1つのファイル（.pdd）に書き出す／読み込む。
 * ファイルには、設定（敵・報酬・クリア条件・解放の設定など）、作業場の建物（目印ブロックつき）、
 * 公開中の設計図が入る。ランキングは入らない。
 *
 * ・書き出し先: サーバーのフォルダ/partydungeon/exports/
 * ・読み込み元: サーバーのフォルダ/partydungeon/import/（起動時に自動で読み込み、done/ に移す）
 */
public final class Exporter {
    private static final int FORMAT = 1;

    public static Path exportDir() {
        return FMLPaths.GAMEDIR.get().resolve("partydungeon").resolve("exports");
    }

    public static Path importDir() {
        return FMLPaths.GAMEDIR.get().resolve("partydungeon").resolve("import");
    }

    /** ファイル名に使えない文字を置き換える */
    private static String safe(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return s.isBlank() ? "dungeon" : s;
    }

    /** 書き出す。できたファイル（失敗したら null） */
    @Nullable
    public static Path export(MinecraftServer server, DungeonDef d) {
        ServerLevel ws = WorkshopManager.workshop(server);
        if (ws == null) {
            return null;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("format", FORMAT);
        t.putString("name", d.name);
        t.put("def", d.save());
        t.put("plot", DungeonTemplate.capture(ws, d.plotOrigin(), d.size, d.height, d.size, null, true).toTag());
        DungeonTemplate published = DungeonTemplate.load(server, d.id);
        if (published != null) {
            t.put("published", published.toTag());
        }
        try {
            Path dir = exportDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(safe(d.name) + ".pdd");
            NbtIo.writeCompressed(t, file);
            return file;
        } catch (IOException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 書き出せませんでした: {}", d.name, e);
            return null;
        }
    }

    /** 読み込み結果 */
    public record Result(@Nullable DungeonDef def, String message) {
    }

    /**
     * 読み込んで、新しい区画に建てる。
     * 元のIDが空いていればそのまま使う（jsn-Craft の依頼がIDでダンジョンを覚えているため）。
     * @param skipSameName 同じ名前のダンジョンがあれば読み込まない（自動読み込み用）
     */
    public static Result importFile(MinecraftServer server, Path file, UUID owner, boolean skipSameName) {
        CompoundTag t;
        try {
            t = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            return new Result(null, "ファイルを読めません: " + e.getMessage());
        }
        if (t.getInt("format") < 1 || !t.contains("def") || !t.contains("plot")) {
            return new Result(null, "Party Dungeon の書き出しファイルではありません。");
        }
        DungeonTemplate plot = DungeonTemplate.fromTag(t.getCompound("plot"));
        if (plot == null) {
            return new Result(null, "ファイルが壊れています。");
        }
        ServerLevel ws = WorkshopManager.workshop(server);
        if (ws == null) {
            return new Result(null, "作業場のディメンションが読み込めません。");
        }
        PDData data = PDData.get(server);
        CompoundTag defTag = t.getCompound("def").copy();
        String name = defTag.getString("name");
        if (data.findByName(name) != null && data.findByName(name).name.equals(name)) {
            if (skipSameName) {
                return new Result(null, "「" + name + "」はもうあるので読み込みませんでした。");
            }
            int n = 2;
            while (data.findByName(name + " (" + n + ")") != null) {
                n++;
            }
            name = name + " (" + n + ")";
        }
        String origId = defTag.getString("id");
        String id;
        if (!origId.isEmpty() && !data.dungeons.containsKey(origId)) {
            id = origId;
            if (origId.startsWith("d")) {
                try {
                    data.nextId = Math.max(data.nextId, Integer.parseInt(origId.substring(1)) + 1);
                } catch (NumberFormatException ignored) {
                    // 数字でないIDはそのまま
                }
            }
        } else {
            id = "d" + data.nextId++;
        }
        int size = defTag.getInt("size");
        defTag.putString("id", id);
        defTag.putString("name", name);
        defTag.putUUID("owner", owner);
        defTag.putInt("plot", data.allocatePlot(size));
        DungeonDef d = DungeonDef.load(defTag);

        String note = id.equals(origId) ? "" : "（IDが " + origId + " → " + id + " に変わりました。依頼の設定を確認してください）";
        if (t.contains("published")) {
            DungeonTemplate pub = DungeonTemplate.fromTag(t.getCompound("published"));
            try {
                if (pub == null) {
                    throw new IOException("壊れています");
                }
                pub.save(server, id);
            } catch (IOException e) {
                d.published = false;
                note += "（公開中の設計図を読めなかったので、非公開にしました）";
            }
        } else {
            d.published = false;
        }
        data.dungeons.put(id, d);
        data.setDirty();
        BlockJobs.add(BlockJobs.place(ws, d.plotOrigin(), plot, null));
        return new Result(d, "「" + name + "」を読み込みました" + (d.published ? "（公開中）" : "（非公開）") + note);
    }

    /** 読み込めるファイルの一覧（import フォルダと exports フォルダ） */
    public static List<Path> candidates() {
        List<Path> list = new ArrayList<>();
        for (Path dir : new Path[] {importDir(), exportDir()}) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(f -> f.getFileName().toString().endsWith(".pdd")).forEach(list::add);
            } catch (IOException ignored) {
                // 読めないフォルダは飛ばす
            }
        }
        return list;
    }

    /** 名前（拡張子なしでも可）でファイルを探す */
    @Nullable
    public static Path find(String name) {
        String want = name.endsWith(".pdd") ? name : name + ".pdd";
        for (Path f : candidates()) {
            if (f.getFileName().toString().equals(want) || f.getFileName().toString().equals(safe(name) + ".pdd")) {
                return f;
            }
        }
        return null;
    }

    /** 起動時：import フォルダのファイルを読み込み、done/ に移す */
    public static void autoImport(MinecraftServer server) {
        Path dir = importDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(f -> f.getFileName().toString().endsWith(".pdd")).forEach(files::add);
        } catch (IOException e) {
            return;
        }
        for (Path f : files) {
            Result r = importFile(server, f, new UUID(0, 0), true);
            PartyDungeon.LOGGER.info("[Party Dungeon] 自動読み込み {}: {}", f.getFileName(), r.message());
            try {
                Path done = dir.resolve("done");
                Files.createDirectories(done);
                Files.move(f, done.resolve(f.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                PartyDungeon.LOGGER.warn("[Party Dungeon] {} を done に移せませんでした", f.getFileName());
            }
        }
    }

    private Exporter() {
    }
}
