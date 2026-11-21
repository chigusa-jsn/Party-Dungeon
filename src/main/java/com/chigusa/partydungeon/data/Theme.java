package com.chigusa.partydungeon.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ダンジョンの「フィールド」＝使うブロックの組み合わせ。
 * 床・壁・天井・明かり・ボス部屋の床を、テーマごとに決めておく。
 * 壁や床は数種類を混ぜて、のっぺりしないようにする。
 */
public final class Theme {
    public enum Part { FLOOR, WALL, CEILING, LIGHT, BOSS_FLOOR }

    public final String id;
    public final String label;
    private final Block[] floor;
    private final Block[] wall;
    private final Block[] ceiling;
    private final Block light;
    private final Block bossFloor;

    private Theme(String id, String label, Block[] floor, Block[] wall, Block[] ceiling, Block light, Block bossFloor) {
        this.id = id;
        this.label = label;
        this.floor = floor;
        this.wall = wall;
        this.ceiling = ceiling;
        this.light = light;
        this.bossFloor = bossFloor;
    }

    private static Block[] b(Block... blocks) {
        return blocks;
    }

    /** 選べるテーマ（順番は画面のボタンで切り替わる順） */
    public static final List<Theme> ALL = new ArrayList<>();
    private static final Map<String, Theme> BY_ID = new HashMap<>();

    private static Theme add(String id, String label, Block[] floor, Block[] wall, Block[] ceiling, Block light,
            Block boss) {
        Theme t = new Theme(id, label, floor, wall, ceiling, light, boss);
        ALL.add(t);
        BY_ID.put(id, t);
        return t;
    }

    public static final Theme STONE = add("stone", "石レンガの迷宮",
            b(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
            b(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS),
            b(Blocks.STONE_BRICKS), Blocks.SEA_LANTERN, Blocks.CHISELED_STONE_BRICKS);

    static {
        add("cave", "洞窟",
                b(Blocks.COBBLESTONE, Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF),
                b(Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.COAL_ORE),
                b(Blocks.STONE, Blocks.STONE, Blocks.ANDESITE), Blocks.GLOWSTONE, Blocks.POLISHED_ANDESITE);
        add("forest", "森の遺跡",
                b(Blocks.MOSS_BLOCK, Blocks.MOSS_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.ROOTED_DIRT),
                b(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS, Blocks.OAK_LOG),
                b(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.MOSS_BLOCK), Blocks.SHROOMLIGHT,
                Blocks.STRIPPED_OAK_WOOD);
        add("desert", "砂漠の神殿",
                b(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE),
                b(Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SANDSTONE, Blocks.CHISELED_SANDSTONE),
                b(Blocks.SMOOTH_SANDSTONE), Blocks.OCHRE_FROGLIGHT, Blocks.CHISELED_RED_SANDSTONE);
        add("ice", "氷の城",
                b(Blocks.PACKED_ICE, Blocks.PACKED_ICE, Blocks.SNOW_BLOCK),
                b(Blocks.PACKED_ICE, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.BLUE_ICE),
                b(Blocks.PACKED_ICE, Blocks.SNOW_BLOCK), Blocks.SEA_LANTERN, Blocks.BLUE_ICE);
        add("ocean", "海底神殿",
                b(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE),
                b(Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE),
                b(Blocks.DARK_PRISMARINE), Blocks.SEA_LANTERN, Blocks.DARK_PRISMARINE);
        add("deep", "地底の古代都市",
                b(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES),
                b(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.POLISHED_DEEPSLATE),
                b(Blocks.DEEPSLATE_TILES, Blocks.SCULK), Blocks.VERDANT_FROGLIGHT, Blocks.REINFORCED_DEEPSLATE);
        add("nether", "ネザー要塞",
                b(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS),
                b(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS, Blocks.CRACKED_NETHER_BRICKS),
                b(Blocks.NETHER_BRICKS), Blocks.SHROOMLIGHT, Blocks.CHISELED_NETHER_BRICKS);
        add("end", "エンドの城",
                b(Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICKS, Blocks.END_STONE),
                b(Blocks.PURPUR_BLOCK, Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR),
                b(Blocks.PURPUR_BLOCK), Blocks.PEARLESCENT_FROGLIGHT, Blocks.OBSIDIAN);
        add("blackstone", "闇の砦",
                b(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
                b(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE),
                b(Blocks.POLISHED_BLACKSTONE), Blocks.SHROOMLIGHT, Blocks.CHISELED_POLISHED_BLACKSTONE);
        add("mansion", "木の屋敷",
                b(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS),
                b(Blocks.BIRCH_PLANKS, Blocks.BIRCH_PLANKS, Blocks.STRIPPED_SPRUCE_WOOD),
                b(Blocks.DARK_OAK_PLANKS), Blocks.GLOWSTONE, Blocks.RED_WOOL);
        add("quartz", "白い宮殿",
                b(Blocks.SMOOTH_QUARTZ, Blocks.QUARTZ_BRICKS),
                b(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_BRICKS, Blocks.QUARTZ_PILLAR),
                b(Blocks.SMOOTH_QUARTZ), Blocks.SEA_LANTERN, Blocks.GOLD_BLOCK);
    }

    public static Theme byId(@Nullable String id) {
        Theme t = id == null ? null : BY_ID.get(id);
        return t == null ? STONE : t;
    }

    /** 次のテーマ（画面のボタン用） */
    public static Theme next(String id) {
        int i = ALL.indexOf(byId(id));
        return ALL.get((i + 1) % ALL.size());
    }

    /** /pd create で壁のブロックを指定されたとき用：全部その1種類 */
    public static Theme single(BlockState wall) {
        Block w = wall.getBlock();
        return new Theme("custom", "指定ブロック", b(w), b(w), b(w), Blocks.SEA_LANTERN, w);
    }

    /** 位置で決まる「ばらつき」。同じ場所なら毎回同じブロックになる */
    private static Block pick(Block[] list, int x, int y, int z) {
        if (list.length == 1) {
            return list[0];
        }
        long h = x * 73428767L ^ y * 912931L ^ z * 438289L;
        h = (h ^ (h >>> 13)) * 0x5DEECE66DL;
        return list[(int) Math.floorMod(h >>> 17, (long) list.length)];
    }

    public BlockState state(Part part, int x, int y, int z) {
        Block b = switch (part) {
            case FLOOR -> pick(floor, x, y, z);
            case WALL -> pick(wall, x, y, z);
            case CEILING -> pick(ceiling, x, y, z);
            case LIGHT -> light;
            case BOSS_FLOOR -> bossFloor;
        };
        return b.defaultBlockState();
    }

    // ---------------------------------------------------------------- 建てたあとの着せ替え

    private static Set<Block> lights;
    private static Set<Block> bosses;
    private static Set<Block> bodies;

    private static void index() {
        if (lights != null) {
            return;
        }
        lights = new HashSet<>();
        bosses = new HashSet<>();
        bodies = new HashSet<>();
        for (Theme t : ALL) {
            lights.add(t.light);
            bosses.add(t.bossFloor);
            bodies.addAll(List.of(t.floor));
            bodies.addAll(List.of(t.wall));
            bodies.addAll(List.of(t.ceiling));
        }
    }

    /**
     * いま置いてあるブロックを、このテーマの同じ役のブロックに置き換えたもの。
     * テーマのブロックでなければ（自分で置いた飾りなど）そのまま返す。
     * 床・壁・天井は、上下が空いているかどうかで見分ける。
     */
    public BlockState restyle(BlockGetter level, BlockPos pos, BlockState now, int x, int y, int z, int relY) {
        index();
        Block b = now.getBlock();
        if (lights.contains(b)) {
            return light.defaultBlockState();
        }
        boolean body = bodies.contains(b);
        if (relY == 0 && bosses.contains(b)) {
            // 一番下の段にあるボス部屋の床
            return bossFloor.defaultBlockState();
        }
        if (!body) {
            return now;
        }
        boolean openAbove = !level.getBlockState(pos.above()).isCollisionShapeFullBlock(level, pos.above());
        boolean openBelow = relY > 0 && !level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below());
        Part part = openBelow ? Part.CEILING : openAbove ? Part.FLOOR : Part.WALL;
        return state(part, x, y, z);
    }
}
