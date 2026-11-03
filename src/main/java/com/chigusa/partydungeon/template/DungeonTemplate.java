package com.chigusa.partydungeon.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.PartyDungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;

/**
 * ダンジョンの建物の「設計図」。
 * 作業場から読み取ってファイルに保存し、挑戦のたびに部屋へ組み立てる。
 * （バニラのストラクチャーと違い、少しずつ組み立てられるよう自前の形式にしている）
 */
public final class DungeonTemplate {
    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    /** 使われているブロックの一覧 */
    public final List<BlockState> palette;
    /** 各位置のブロック（palette の番号）。順番は x → z → y */
    public final int[] data;
    /** チェストなど中身を持つブロック：data の番号 → 中身 */
    public final Map<Integer, CompoundTag> blockEntities;

    private DungeonTemplate(int sizeX, int sizeY, int sizeZ, List<BlockState> palette, int[] data,
            Map<Integer, CompoundTag> blockEntities) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.palette = palette;
        this.data = data;
        this.blockEntities = blockEntities;
    }

    public int volume() {
        return sizeX * sizeY * sizeZ;
    }

    /** data の番号 → 角からの相対位置 */
    public BlockPos relPos(int index) {
        int x = index % sizeX;
        int rest = index / sizeX;
        int z = rest % sizeZ;
        int y = rest / sizeZ;
        return new BlockPos(x, y, z);
    }

    /** 作業場の範囲を読み取って設計図を作る */
    public static DungeonTemplate capture(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> lookup = new HashMap<>();
        palette.add(Blocks.AIR.defaultBlockState());
        lookup.put(Blocks.AIR.defaultBlockState(), 0);

        int[] data = new int[sizeX * sizeY * sizeZ];
        Map<Integer, CompoundTag> bes = new HashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    m.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState state = level.getBlockState(m);
                    Integer idx = lookup.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(state);
                        lookup.put(state, idx);
                    }
                    data[i] = idx;
                    if (state.hasBlockEntity()) {
                        BlockEntity be = level.getBlockEntity(m);
                        if (be != null) {
                            bes.put(i, be.saveWithoutMetadata(level.registryAccess()));
                        }
                    }
                    i++;
                }
            }
        }
        return new DungeonTemplate(sizeX, sizeY, sizeZ, palette, data, bes);
    }

    // ------------------------------------------------------------------ ファイル

    public static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("partydungeon").resolve("dungeons");
    }

    public static Path file(MinecraftServer server, String id) {
        return dir(server).resolve(id + ".nbt");
    }

    public void save(MinecraftServer server, String id) throws IOException {
        CompoundTag t = new CompoundTag();
        t.putInt("version", 1);
        t.putInt("sx", sizeX);
        t.putInt("sy", sizeY);
        t.putInt("sz", sizeZ);
        ListTag pal = new ListTag();
        for (BlockState s : palette) {
            pal.add(NbtUtils.writeBlockState(s));
        }
        t.put("palette", pal);
        t.putIntArray("data", data);
        ListTag be = new ListTag();
        blockEntities.forEach((idx, tag) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("i", idx);
            e.put("nbt", tag);
            be.add(e);
        });
        t.put("blockEntities", be);

        Path path = file(server, id);
        Files.createDirectories(path.getParent());
        NbtIo.writeCompressed(t, path);
    }

    @Nullable
    public static DungeonTemplate load(MinecraftServer server, String id) {
        Path path = file(server, id);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            CompoundTag t = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            int sx = t.getInt("sx");
            int sy = t.getInt("sy");
            int sz = t.getInt("sz");
            ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
            List<BlockState> palette = new ArrayList<>(pal.size());
            for (int i = 0; i < pal.size(); i++) {
                palette.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i)));
            }
            int[] data = t.getIntArray("data");
            if (data.length != sx * sy * sz) {
                PartyDungeon.LOGGER.error("[Party Dungeon] 設計図が壊れています: {}", id);
                return null;
            }
            Map<Integer, CompoundTag> bes = new HashMap<>();
            ListTag be = t.getList("blockEntities", Tag.TAG_COMPOUND);
            for (int i = 0; i < be.size(); i++) {
                CompoundTag e = be.getCompound(i);
                bes.put(e.getInt("i"), e.getCompound("nbt"));
            }
            return new DungeonTemplate(sx, sy, sz, palette, data, bes);
        } catch (IOException e) {
            PartyDungeon.LOGGER.error("[Party Dungeon] 設計図を読み込めません: {}", id, e);
            return null;
        }
    }
}
