package com.chigusa.partydungeon.registry;

import com.chigusa.partydungeon.PartyDungeon;
import com.chigusa.partydungeon.block.GoalMarkerBlock;
import com.chigusa.partydungeon.block.GoalMarkerBlockEntity;
import com.chigusa.partydungeon.block.MarkerBlock;
import com.chigusa.partydungeon.block.RewardMarkerBlock;
import com.chigusa.partydungeon.block.SpawnerMarkerBlock;
import com.chigusa.partydungeon.block.SpawnerMarkerBlockEntity;
import com.chigusa.partydungeon.block.StartMarkerBlock;
import com.chigusa.partydungeon.item.PlanWandItem;
import com.chigusa.partydungeon.trap.ArrowTrapBlock;
import com.chigusa.partydungeon.trap.ArrowTrapBlockEntity;
import com.chigusa.partydungeon.trap.EffectFloorBlock;
import com.chigusa.partydungeon.trap.FloorKind;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** このMODで追加するブロック・アイテム・クリエイティブタブ */
public final class PDRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(PartyDungeon.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PartyDungeon.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, PartyDungeon.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PartyDungeon.MOD_ID);

    private static BlockBehaviour.Properties props() {
        return BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.METAL).noLootTable();
    }

    // ---- ダンジョンに置く目印（挑戦のときは見えなくなる）
    public static final DeferredBlock<StartMarkerBlock> START_MARKER =
            BLOCKS.register("start_marker", () -> new StartMarkerBlock(props()));
    public static final DeferredBlock<SpawnerMarkerBlock> SPAWNER_MARKER =
            BLOCKS.register("spawner_marker", () -> new SpawnerMarkerBlock(props().noOcclusion()));
    public static final DeferredBlock<GoalMarkerBlock> GOAL_MARKER =
            BLOCKS.register("goal_marker", () -> new GoalMarkerBlock(props()));
    public static final DeferredBlock<RewardMarkerBlock> REWARD_MARKER =
            BLOCKS.register("reward_marker", () -> new RewardMarkerBlock(props()));

    // ---- 間取りを描くブロック
    public static final DeferredBlock<Block> PLAN_ROOM =
            BLOCKS.register("plan_room", () -> new MarkerBlock(props().sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> PLAN_CORRIDOR =
            BLOCKS.register("plan_corridor", () -> new MarkerBlock(props().sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> PLAN_START =
            BLOCKS.register("plan_start", () -> new MarkerBlock(props().sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> PLAN_BOSS =
            BLOCKS.register("plan_boss", () -> new MarkerBlock(props().sound(SoundType.WOOL)));

    public static final DeferredItem<BlockItem> START_MARKER_ITEM = ITEMS.registerSimpleBlockItem(START_MARKER);
    public static final DeferredItem<BlockItem> SPAWNER_MARKER_ITEM = ITEMS.registerSimpleBlockItem(SPAWNER_MARKER);
    public static final DeferredItem<BlockItem> GOAL_MARKER_ITEM = ITEMS.registerSimpleBlockItem(GOAL_MARKER);
    public static final DeferredItem<BlockItem> REWARD_MARKER_ITEM = ITEMS.registerSimpleBlockItem(REWARD_MARKER);
    public static final DeferredItem<BlockItem> PLAN_ROOM_ITEM = ITEMS.registerSimpleBlockItem(PLAN_ROOM);
    public static final DeferredItem<BlockItem> PLAN_CORRIDOR_ITEM = ITEMS.registerSimpleBlockItem(PLAN_CORRIDOR);
    public static final DeferredItem<BlockItem> PLAN_START_ITEM = ITEMS.registerSimpleBlockItem(PLAN_START);
    public static final DeferredItem<BlockItem> PLAN_BOSS_ITEM = ITEMS.registerSimpleBlockItem(PLAN_BOSS);
    public static final DeferredItem<Item> PLAN_WAND =
            ITEMS.register("plan_wand", () -> new PlanWandItem(new Item.Properties().stacksTo(1)));

    // ---- 罠
    public static final DeferredBlock<ArrowTrapBlock> ARROW_TRAP = BLOCKS.register("arrow_trap",
            () -> new ArrowTrapBlock(BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.STONE).noLootTable()));
    public static final DeferredItem<BlockItem> ARROW_TRAP_ITEM = ITEMS.registerSimpleBlockItem(ARROW_TRAP);
    public static final DeferredBlock<com.chigusa.partydungeon.trap.CrumbleFloorBlock> CRUMBLE_FLOOR =
            BLOCKS.register("crumble_floor", () -> new com.chigusa.partydungeon.trap.CrumbleFloorBlock(
                    BlockBehaviour.Properties.of().strength(1.0f, 6f).sound(SoundType.STONE).noLootTable().noOcclusion()));
    public static final DeferredItem<BlockItem> CRUMBLE_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(CRUMBLE_FLOOR);
    public static final DeferredBlock<Block> FALL_RETURN = BLOCKS.register("fall_return",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.AMETHYST).noLootTable()
                    .lightLevel(s -> 7)));
    public static final DeferredItem<BlockItem> FALL_RETURN_ITEM = ITEMS.registerSimpleBlockItem(FALL_RETURN);
    public static final Map<FloorKind, DeferredBlock<EffectFloorBlock>> FLOORS = new EnumMap<>(FloorKind.class);
    public static final Map<FloorKind, DeferredItem<BlockItem>> FLOOR_ITEMS = new EnumMap<>(FloorKind.class);

    static {
        for (FloorKind k : FloorKind.values()) {
            DeferredBlock<EffectFloorBlock> b = BLOCKS.register(k.id, () -> new EffectFloorBlock(k,
                    BlockBehaviour.Properties.of().strength(1.5f, 6f).sound(SoundType.STONE).noLootTable()
                            .lightLevel(s -> k.light)));
            FLOORS.put(k, b);
            FLOOR_ITEMS.put(k, ITEMS.registerSimpleBlockItem(b));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArrowTrapBlockEntity>> ARROW_TRAP_BE =
            BLOCK_ENTITIES.register("arrow_trap",
                    () -> BlockEntityType.Builder.of(ArrowTrapBlockEntity::new, ARROW_TRAP.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerMarkerBlockEntity>> SPAWNER_MARKER_BE =
            BLOCK_ENTITIES.register("spawner_marker",
                    () -> BlockEntityType.Builder.of(SpawnerMarkerBlockEntity::new, SPAWNER_MARKER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GoalMarkerBlockEntity>> GOAL_MARKER_BE =
            BLOCK_ENTITIES.register("goal_marker",
                    () -> BlockEntityType.Builder.of(GoalMarkerBlockEntity::new, GOAL_MARKER.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.partydungeon"))
                    .icon(() -> new ItemStack(SPAWNER_MARKER_ITEM.get()))
                    .displayItems((params, out) -> {
                        out.accept(PLAN_WAND.get());
                        out.accept(PLAN_ROOM_ITEM.get());
                        out.accept(PLAN_CORRIDOR_ITEM.get());
                        out.accept(PLAN_START_ITEM.get());
                        out.accept(PLAN_BOSS_ITEM.get());
                        out.accept(START_MARKER_ITEM.get());
                        out.accept(SPAWNER_MARKER_ITEM.get());
                        out.accept(GOAL_MARKER_ITEM.get());
                        out.accept(REWARD_MARKER_ITEM.get());
                        out.accept(ARROW_TRAP_ITEM.get());
                        out.accept(CRUMBLE_FLOOR_ITEM.get());
                        out.accept(FALL_RETURN_ITEM.get());
                        FLOOR_ITEMS.values().forEach(i -> out.accept(i.get()));
                    })
                    .build());

    /** 目印ブロック（ダンジョンに組み立てるときは空気にする）か */
    public static boolean isMarker(Block b) {
        return b == START_MARKER.get() || b == SPAWNER_MARKER.get() || b == GOAL_MARKER.get()
                || b == REWARD_MARKER.get() || b == PLAN_ROOM.get() || b == PLAN_CORRIDOR.get() || b == PLAN_START.get() || b == PLAN_BOSS.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
    }

    private PDRegistry() {
    }
}
