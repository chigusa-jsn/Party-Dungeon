package com.chigusa.partydungeon.workshop;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.job.BlockJobs;
import com.chigusa.partydungeon.net.PDNetwork;
import com.chigusa.partydungeon.template.DungeonTemplate;
import com.chigusa.partydungeon.util.Canon;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * まとめて書き出し／読み込み（ダンジョン全部 ＋ jsn-Craft の依頼）。
 *
 * ・書き出し：サーバーでファイルの中身を作って、押した人のパソコンへ送る（パソコンの partydungeon/exports/ に保存）
 * ・読み込み：パソコンのファイルをサーバーへ送る → 1つずつ「新しい／更新／こちらで変更あり」を見せる → 選んだ物だけ入れる
 *
 * ダンジョンと依頼には uid（どこでも同じ印）を付けておき、2回目からは同じ物を上書きする。
 * 最後に書き出し／読み込みしたときの指紋（syncHash）とくらべて、こちらで変更したかどうかを見分ける。
 */
public final class Bundle {
    public static final int FORMAT = 2;
    /** サーバー → パソコンの1かたまり */
    public static final int DOWN_CHUNK = 512 * 1024;
    /** パソコン → サーバーの1かたまり（サーバーへの通信は 32KB まで） */
    public static final int UP_CHUNK = 30000;
    /** 受け取るファイルの上限 */
    public static final int MAX_BYTES = 64 * 1024 * 1024;

    private static final class Upload {
        int id;
        int total;
        int next;
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    }

    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    /** 確認画面で選んでもらうまで置いておく */
    private static final Map<UUID, CompoundTag> PENDING = new HashMap<>();

    // ------------------------------------------------------------------ 中身

    /** ダンジョン1つ分（どこでも同じ形）。ID・持ち主・区画など、その場所だけの物は入れない */
    static CompoundTag entry(MinecraftServer server, ServerLevel ws, DungeonDef d) {
        CompoundTag def = d.save();
        for (String k : new String[] {"id", "owner", "plot", "uid", "sync", "revision"}) {
            def.remove(k);
        }
        CompoundTag plot = DungeonTemplate.capture(ws, d.plotOrigin(), d.size, d.height, d.size, null, true).toTag();
        DungeonTemplate pub = d.published ? DungeonTemplate.load(server, d.id) : null;
        CompoundTag e = new CompoundTag();
        e.putString("uid", d.uid);
        e.putString("name", d.name);
        e.put("def", def);
        e.put("plot", plot);
        CompoundTag pubTag = pub == null ? null : pub.toTag();
        if (pubTag != null) {
            e.put("published", pubTag);
        }
        e.putString("hash", Canon.hash(def, plot, pubTag));
        return e;
    }

    private static Map<String, String> idToUid(PDData data) {
        Map<String, String> m = new HashMap<>();
        for (DungeonDef d : data.dungeons.values()) {
            if (d.ensureUid()) {
                data.setDirty();
            }
            m.put(d.id, d.uid);
        }
        return m;
    }

    // ------------------------------------------------------------------ 書き出し

    public static void export(MinecraftServer server, ServerPlayer p) {
        ServerLevel ws = WorkshopManager.workshop(server);
        if (ws == null) {
            Msg.warn(p, "作業場が読み込めません。");
            return;
        }
        PDData data = PDData.get(server);
        Map<String, String> ids = idToUid(data);
        CompoundTag b = new CompoundTag();
        b.putInt("format", FORMAT);
        b.putLong("created", System.currentTimeMillis());
        b.putString("source", server.isDedicatedServer() ? "サーバー" : "1人用「" + server.getWorldData().getLevelName() + "」");
        ListTag list = new ListTag();
        for (DungeonDef d : data.dungeons.values()) {
            CompoundTag e = entry(server, ws, d);
            d.syncHash = e.getString("hash");
            list.add(e);
        }
        data.setDirty();
        b.put("dungeons", list);
        int quests = 0;
        if (QuestLink.available()) {
            CompoundTag q = QuestLink.export(server, ids);
            if (q != null) {
                b.put("quests", q);
                quests = q.getList("quests", Tag.TAG_COMPOUND).size();
            }
        }
        CompoundTag sum = new CompoundTag();
        sum.putInt("dungeons", list.size());
        sum.putInt("quests", quests);
        b.put("summary", sum);

        byte[] bytes;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(b, out);
            bytes = out.toByteArray();
        } catch (IOException e) {
            Msg.warn(p, "書き出せませんでした。");
            PartyDungeon.LOGGER.error("[Party Dungeon] まとめて書き出しに失敗", e);
            return;
        }
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date())
                + (server.isDedicatedServer() ? "_server" : "_single") + ".pdb";
        int total = (bytes.length + DOWN_CHUNK - 1) / DOWN_CHUNK;
        int id = (int) (System.nanoTime() & 0x7fffffff);
        for (int i = 0; i < total; i++) {
            int from = i * DOWN_CHUNK;
            byte[] part = java.util.Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + DOWN_CHUNK));
            PacketDistributor.sendToPlayer(p, new PDNetwork.BundleDown(id, i, total, name, part));
        }
        Msg.ok(p, "ダンジョン " + list.size() + " こ・依頼 " + quests + " こを書き出しています…（"
                + (bytes.length / 1024) + " KB）");
    }

    // ------------------------------------------------------------------ 読み込み

    /** パソコンから届いた1かたまり */
    public static void receive(ServerPlayer p, int id, int index, int total, byte[] part) {
        if (!p.hasPermissions(2)) {
            return;
        }
        if (total <= 0 || (long) total * UP_CHUNK > MAX_BYTES) {
            Msg.warn(p, "ファイルが大きすぎます（64MB まで）。");
            return;
        }
        Upload u = UPLOADS.get(p.getUUID());
        if (index == 0 || u == null || u.id != id) {
            u = new Upload();
            u.id = id;
            u.total = total;
            UPLOADS.put(p.getUUID(), u);
        }
        if (index != u.next) {
            UPLOADS.remove(p.getUUID());
            Msg.warn(p, "ファイルの送信がとちゅうで途切れました。もう一度やってみてください。");
            return;
        }
        u.buf.writeBytes(part);
        u.next++;
        if (u.next < u.total) {
            return;
        }
        UPLOADS.remove(p.getUUID());
        CompoundTag b;
        try {
            b = NbtIo.readCompressed(new ByteArrayInputStream(u.buf.toByteArray()), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            Msg.warn(p, "ファイルを読めませんでした（壊れているかもしれません）。");
            return;
        }
        if (b.getInt("format") != FORMAT || !b.contains("dungeons")) {
            Msg.warn(p, "Party Dungeon のまとめて書き出しファイルではありません。");
            return;
        }
        PENDING.put(p.getUUID(), b);
        preview(p.server, p, b);
    }

    /** 1つずつの状態を調べて、確認画面を開く */
    private static void preview(MinecraftServer server, ServerPlayer p, CompoundTag b) {
        ServerLevel ws = WorkshopManager.workshop(server);
        if (ws == null) {
            Msg.warn(p, "作業場が読み込めません。");
            return;
        }
        PDData data = PDData.get(server);
        Map<String, String> ids = idToUid(data);
        ListTag rows = new ListTag();
        ListTag list = b.getList("dungeons", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            DungeonDef local = findByUid(data, e.getString("uid"));
            String status;
            if (local == null) {
                status = "new";
            } else {
                String now = entry(server, ws, local).getString("hash");
                if (now.equals(e.getString("hash"))) {
                    status = "same";
                } else if (now.equals(local.syncHash)) {
                    status = "update";
                } else {
                    status = "changed";
                }
            }
            CompoundTag r = new CompoundTag();
            r.putString("uid", e.getString("uid"));
            r.putString("name", e.getString("name"));
            r.putString("status", status);
            rows.add(r);
        }
        CompoundTag out = new CompoundTag();
        out.put("dungeons", rows);
        out.put("quests", b.contains("quests") && QuestLink.available()
                ? QuestLink.preview(server, b.getCompound("quests"), ids) : new ListTag());
        out.putBoolean("questsMissing", b.contains("quests") && !QuestLink.available());
        out.putString("source", b.getString("source"));
        out.putLong("created", b.getLong("created"));
        PDNetwork.openScreen(p, "bundle_preview", out);
    }

    @Nullable
    private static DungeonDef findByUid(PDData data, String uid) {
        if (uid.isEmpty()) {
            return null;
        }
        for (DungeonDef d : data.dungeons.values()) {
            if (uid.equals(d.uid)) {
                return d;
            }
        }
        return null;
    }

    /** 確認画面で「読み込む」 */
    public static void apply(MinecraftServer server, ServerPlayer p, CompoundTag choice) {
        CompoundTag b = PENDING.remove(p.getUUID());
        if (b == null) {
            Msg.warn(p, "読み込むファイルがありません。もう一度選んでください。");
            return;
        }
        ServerLevel ws = WorkshopManager.workshop(server);
        if (ws == null) {
            return;
        }
        Set<String> pickD = strings(choice.getList("dungeons", Tag.TAG_STRING));
        List<String> pickQ = new ArrayList<>(strings(choice.getList("quests", Tag.TAG_STRING)));
        PDData data = PDData.get(server);
        int added = 0;
        int updated = 0;
        ListTag list = b.getList("dungeons", Tag.TAG_COMPOUND);
        Map<String, String> bundleNames = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            bundleNames.put(e.getString("uid"), e.getString("name"));
            if (!pickD.contains(e.getString("uid"))) {
                continue;
            }
            if (applyDungeon(server, ws, data, p.getUUID(), e)) {
                added++;
            } else {
                updated++;
            }
        }
        data.setDirty();
        StringBuilder msg = new StringBuilder("ダンジョン：追加 " + added + "・更新 " + updated);
        if (b.contains("quests") && !pickQ.isEmpty()) {
            // 依頼の中のダンジョンの印 → こちらのダンジョンID（なければ名前）
            Map<String, String> ref = new HashMap<>(bundleNames);
            for (DungeonDef d : data.dungeons.values()) {
                if (!d.uid.isEmpty()) {
                    ref.put(d.uid, d.id);
                }
            }
            msg.append(" ／ ").append(QuestLink.apply(server, b.getCompound("quests"), pickQ, ref));
        }
        Msg.ok(p, "読み込みました！ " + msg);
        if (added + updated > 0) {
            Msg.info(p, "作業場に建物を建てています。少し待つと終わります。");
        }
    }

    private static Set<String> strings(ListTag l) {
        Set<String> s = new HashSet<>();
        for (Tag t : l) {
            s.add(((StringTag) t).getAsString());
        }
        return s;
    }

    /** 1つ入れる。新しく追加したら true、上書きなら false */
    private static boolean applyDungeon(MinecraftServer server, ServerLevel ws, PDData data, UUID importer, CompoundTag e) {
        String uid = e.getString("uid");
        DungeonDef local = findByUid(data, uid);
        CompoundTag defTag = e.getCompound("def").copy();
        int size = defTag.getInt("size");
        int height = defTag.getInt("height");
        DungeonTemplate plot = DungeonTemplate.fromTag(e.getCompound("plot"));
        if (plot == null) {
            return false;
        }
        String id;
        int plotIndex;
        UUID owner;
        int revision = 0;
        if (local == null) {
            do {
                id = "d" + data.nextId++;
            } while (data.dungeons.containsKey(id));
            plotIndex = data.allocatePlot(size);
            owner = importer;
        } else {
            id = local.id;
            owner = local.owner;
            revision = local.revision + 1;
            if (local.size == size && local.height == height) {
                plotIndex = local.plot;
            } else {
                // 大きさが変わったら、新しい区画へ。前の区画は空にする
                plotIndex = data.allocatePlot(size);
                net.minecraft.core.BlockPos o = local.plotOrigin();
                BlockJobs.add(BlockJobs.custom(ws, o, local.size, local.height, local.size,
                        (x, y, z) -> Blocks.AIR.defaultBlockState(), null));
            }
        }
        // 名前がほかのダンジョンとかぶったら (2) をつける
        String name = e.getString("name");
        String base = name;
        int n = 2;
        while (nameTaken(data, name, id)) {
            name = base + " (" + n++ + ")";
        }
        defTag.putString("id", id);
        defTag.putString("name", name);
        defTag.putUUID("owner", owner);
        defTag.putInt("plot", plotIndex);
        DungeonDef d = DungeonDef.load(defTag);
        d.uid = uid;
        d.revision = revision;
        d.syncHash = e.getString("hash");
        if (e.contains("published")) {
            DungeonTemplate pub = DungeonTemplate.fromTag(e.getCompound("published"));
            try {
                if (pub == null) {
                    throw new IOException("壊れています");
                }
                pub.save(server, id);
            } catch (IOException ex) {
                d.published = false;
            }
        } else {
            d.published = false;
        }
        data.dungeons.put(id, d);
        final String fid = id;
        BlockJobs.add(BlockJobs.place(ws, d.plotOrigin(), plot, () -> {
            // 建て終わったら、こちらの形での指紋を覚えておく（次に読み込むとき「変更なし」と分かるように）
            DungeonDef now = PDData.get(server).dungeons.get(fid);
            if (now != null && now.uid.equals(uid)) {
                now.syncHash = entry(server, ws, now).getString("hash");
                PDData.get(server).setDirty();
            }
        }));
        return local == null;
    }

    private static boolean nameTaken(PDData data, String name, String exceptId) {
        for (DungeonDef d : data.dungeons.values()) {
            if (!d.id.equals(exceptId) && d.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void onLogout(UUID id) {
        UPLOADS.remove(id);
        PENDING.remove(id);
    }

    private Bundle() {
    }
}
