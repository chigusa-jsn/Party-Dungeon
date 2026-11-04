package com.chigusa.partydungeon;

import com.chigusa.partydungeon.data.ClearCondition;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.data.SpawnPoint;
import com.chigusa.partydungeon.menu.Menus;
import com.chigusa.partydungeon.npc.NpcManager;
import com.chigusa.partydungeon.party.PartyManager;
import com.chigusa.partydungeon.run.RunManager;
import com.chigusa.partydungeon.util.Msg;
import com.chigusa.partydungeon.workshop.WorkshopManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** /pd コマンド一式 */
public final class PDCommands {

    @FunctionalInterface
    private interface PlayerAction {
        void run(MinecraftServer server, ServerPlayer player, CommandContext<CommandSourceStack> ctx)
                throws CommandSyntaxException;
    }

    private static int exec(CommandContext<CommandSourceStack> ctx, PlayerAction action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        action.run(ctx.getSource().getServer(), p, ctx);
        return 1;
    }

    /** 作業場の区画の中で使うコマンド */
    @FunctionalInterface
    private interface PlotAction {
        void run(MinecraftServer server, ServerPlayer player, DungeonDef def, CommandContext<CommandSourceStack> ctx)
                throws CommandSyntaxException;
    }

    private static int plot(CommandContext<CommandSourceStack> ctx, PlotAction action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        DungeonDef d = WorkshopManager.here(server, p);
        if (d != null) {
            action.run(server, p, d, ctx);
            PDData.get(server).setDirty();
        }
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext build) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pd");

        // ---------------------------------------------------------- みんなが使う
        root.then(Commands.literal("help").executes(ctx -> exec(ctx, (s, p, c) -> help(p, c.getSource().hasPermission(2)))));
        root.executes(ctx -> exec(ctx, (s, p, c) -> Menus.main(s, p)));
        root.then(Commands.literal("menu").executes(ctx -> exec(ctx, (s, p, c) -> Menus.main(s, p))));
        root.then(Commands.literal("lobby").executes(ctx -> exec(ctx, (s, p, c) -> Menus.lobby(s, p))));
        root.then(Commands.literal("join").then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> exec(ctx, (s, p, c) -> {
                    if (PartyManager.joinRecruit(s, p, EntityArgument.getPlayer(c, "player").getUUID())) {
                        Menus.lobby(s, p);
                    }
                }))));
        root.then(Commands.literal("npc").requires(src -> src.hasPermission(2))
                .executes(ctx -> exec(ctx, (s, p, c) -> NpcManager.spawn(p, "ダンジョン受付")))
                .then(Commands.literal("remove").executes(ctx -> exec(ctx, (s, p, c) -> NpcManager.removeNearest(p))))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> exec(ctx, (s, p, c) ->
                                NpcManager.spawn(p, StringArgumentType.getString(c, "name"))))));

        root.then(Commands.literal("play").executes(ctx -> exec(ctx, (s, p, c) -> {
            if (RunManager.isInRun(p.getUUID())) {
                Msg.warn(p, "挑戦中です。抜けるなら /pd exit");
                return;
            }
            Menus.dungeonList(s, p, 0);
        })));

        root.then(Commands.literal("exit").executes(ctx -> exec(ctx, (s, p, c) -> {
            if (RunManager.isInRun(p.getUUID())) {
                RunManager.leave(s, p);
            } else if (!WorkshopManager.exit(s, p)) {
                Msg.warn(p, "ダンジョンにも作業場にもいません。");
            }
        })));

        root.then(Commands.literal("party")
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> exec(ctx, (s, p, c) ->
                                PartyManager.invite(p, EntityArgument.getPlayer(c, "player"))))))
                .then(Commands.literal("accept").executes(ctx -> exec(ctx, (s, p, c) -> {
                    if (PartyManager.accept(s, p)) {
                        Menus.lobby(s, p);
                    }
                })))
                .then(Commands.literal("decline").executes(ctx -> exec(ctx, (s, p, c) -> PartyManager.decline(p))))
                .then(Commands.literal("leave").executes(ctx -> exec(ctx, (s, p, c) -> {
                    if (PartyManager.get(p.getUUID()) == null) {
                        Msg.warn(p, "パーティに入っていません。");
                        return;
                    }
                    PartyManager.leave(s, p.getUUID(), p.getName().getString());
                    Msg.info(p, "パーティを抜けました。");
                })))
                .then(Commands.literal("kick").then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> exec(ctx, (s, p, c) ->
                                PartyManager.kick(s, p, EntityArgument.getPlayer(c, "player").getUUID())))))
                .then(Commands.literal("list").executes(ctx -> exec(ctx, (s, p, c) -> PartyManager.list(s, p)))));

        // ---------------------------------------------------------- ダンジョンを作る人（OP）
        root.then(Commands.literal("workshop").requires(src -> src.hasPermission(2))
                .executes(ctx -> exec(ctx, (s, p, c) -> WorkshopManager.enter(s, p))));

        root.then(Commands.literal("create").requires(src -> src.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> create(ctx, 48, 24, null))
                        .then(Commands.argument("size", IntegerArgumentType.integer(16, 128))
                                .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "size"), 24, null))
                                .then(Commands.argument("height", IntegerArgumentType.integer(8, 96))
                                        .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "height"), null))
                                        .then(Commands.argument("block", BlockStateArgument.block(build))
                                                .executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "size"),
                                                        IntegerArgumentType.getInteger(ctx, "height"),
                                                        BlockStateArgument.getBlock(ctx, "block").getState())))))));

        root.then(Commands.literal("setstart").requires(src -> src.hasPermission(2))
                .executes(ctx -> plot(ctx, (s, p, d, c) -> {
                    d.start = WorkshopManager.relPos(d, p);
                    Msg.ok(p, "スタート地点をここにしました。（緑のキラキラが目印）");
                })));

        root.then(Commands.literal("setgoal").requires(src -> src.hasPermission(2))
                .executes(ctx -> plot(ctx, (s, p, d, c) -> setGoal(p, d, 2)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                        .executes(ctx -> plot(ctx, (s, p, d, c) ->
                                setGoal(p, d, IntegerArgumentType.getInteger(c, "radius"))))));

        LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear").requires(src -> src.hasPermission(2));
        for (ClearCondition cc : ClearCondition.values()) {
            clear.then(Commands.literal(cc.id).executes(ctx -> plot(ctx, (s, p, d, c) -> {
                d.clear = cc;
                Msg.ok(p, "クリア条件を「" + cc.label + "」にしました。");
            })));
        }
        root.then(clear);

        root.then(Commands.literal("info").requires(src -> src.hasPermission(2))
                .executes(ctx -> plot(ctx, (s, p, d, c) -> info(p, d))));

        root.then(Commands.literal("publish").requires(src -> src.hasPermission(2))
                .executes(ctx -> plot(ctx, (s, p, d, c) -> WorkshopManager.publish(s, p, d))));

        root.then(Commands.literal("unpublish").requires(src -> src.hasPermission(2))
                .executes(ctx -> plot(ctx, (s, p, d, c) -> {
                    d.published = false;
                    Msg.ok(p, "「" + d.name + "」を非公開にしました（作業場の建物と設定は残ります）。");
                })));

        root.then(Commands.literal("rename").requires(src -> src.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> plot(ctx, (s, p, d, c) -> {
                            String name = StringArgumentType.getString(c, "name");
                            if (PDData.get(s).findByName(name) != null) {
                                Msg.warn(p, "その名前はもう使われています。");
                                return;
                            }
                            d.name = name;
                            Msg.ok(p, "名前を「" + name + "」に変えました。");
                        }))));

        root.then(Commands.literal("list").requires(src -> src.hasPermission(2))
                .executes(ctx -> exec(ctx, (s, p, c) -> {
                    PDData data = PDData.get(s);
                    if (data.dungeons.isEmpty()) {
                        Msg.info(p, "まだダンジョンがありません。");
                    }
                    for (DungeonDef d : data.dungeons.values()) {
                        Msg.info(p, d.name + "（" + d.id + "）" + (d.published ? " 公開中 第" + d.revision + "版" : " 未公開"));
                    }
                })));

        root.then(Commands.literal("goto").requires(src -> src.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(ctx -> exec(ctx, (s, p, c) -> {
                            DungeonDef d = PDData.get(s).findByName(StringArgumentType.getString(c, "name"));
                            if (d == null) {
                                Msg.warn(p, "そのダンジョンはありません。/pd list で確認できます。");
                                return;
                            }
                            if (!WorkshopManager.isIn(p)) {
                                WorkshopManager.enter(s, p);
                            }
                            BlockPos o = d.plotOrigin();
                            p.teleportTo(WorkshopManager.workshop(s), o.getX() + d.size / 2.0, o.getY() + 1,
                                    o.getZ() + d.size / 2.0, p.getYRot(), 0);
                        }))));

        root.then(Commands.literal("rooms").requires(src -> src.hasPermission(2))
                .executes(ctx -> exec(ctx, (s, p, c) -> {
                    int max = PDConfig.ROOM_COUNT.get();
                    for (RunManager.Room r : RunManager.rooms()) {
                        if (r.index >= max) {
                            continue;
                        }
                        String st;
                        if (r.run != null) {
                            st = "「" + r.run.def.name + "」" + r.run.members.size() + "人 " + r.run.state;
                        } else if (r.cleaning) {
                            st = "片付け中";
                        } else {
                            st = "空き";
                        }
                        Msg.info(p, "部屋 " + (r.index + 1) + ": " + st);
                    }
                })));

        dispatcher.register(root);
    }

    private static int create(CommandContext<CommandSourceStack> ctx, int size, int height, BlockState block)
            throws CommandSyntaxException {
        return exec(ctx, (s, p, c) -> WorkshopManager.create(s, p, StringArgumentType.getString(c, "name"), size,
                height, block == null ? Blocks.STONE_BRICKS.defaultBlockState() : block));
    }

    private static void setGoal(ServerPlayer p, DungeonDef d, int radius) {
        d.goal = WorkshopManager.relPos(d, p);
        d.goalRadius = radius;
        Msg.ok(p, "ゴール地点をここにしました（半径 " + radius + "）。白い光が目印です。");
        if (d.clear != ClearCondition.GOAL) {
            Msg.info(p, "クリア条件をゴールにするなら /pd clear goal");
        }
    }

    private static int addSpawn(CommandContext<CommandSourceStack> ctx, int count, int radius, boolean boss)
            throws CommandSyntaxException {
        Holder.Reference<EntityType<?>> ref = ResourceArgument.getSummonableEntityType(ctx, "entity");
        ResourceLocation id = ref.key().location();
        return plot(ctx, (s, p, d, c) -> {
            BlockPos rel = WorkshopManager.relPos(d, p);
            d.spawns.add(new SpawnPoint(rel, id, count, radius, boss));
            String when = radius <= 0 ? "開始と同時" : "プレイヤーが " + radius + " ブロックまで近づいたら";
            Msg.ok(p, (boss ? "ボス " : "敵 ") + id + " ×" + count + " を登録しました（" + when + "出現）。"
                    + " 番号: " + d.spawns.size());
        });
    }

    private static void listSpawns(ServerPlayer p, DungeonDef d) {
        if (d.spawns.isEmpty()) {
            Msg.info(p, "敵はまだ登録されていません。");
            return;
        }
        for (int i = 0; i < d.spawns.size(); i++) {
            SpawnPoint s = d.spawns.get(i);
            Msg.info(p, (i + 1) + ". " + (s.boss() ? "[ボス] " : "") + s.entity() + " ×" + s.count()
                    + " 位置(" + s.rel().getX() + "," + s.rel().getY() + "," + s.rel().getZ() + ")"
                    + (s.triggerRadius() > 0 ? " 近づいたら(" + s.triggerRadius() + ")" : " 開始時"));
        }
    }

    private static void info(ServerPlayer p, DungeonDef d) {
        Msg.info(p, "「" + d.name + "」（" + d.id + "） " + d.size + "×" + d.size + " 高さ" + d.height
                + (d.published ? " 公開中 第" + d.revision + "版" : " 未公開"));
        Msg.info(p, "クリア条件: " + d.clear.label + " ／ 敵: " + d.spawns.size() + " か所");
        String problem = d.validate();
        if (problem != null) {
            Msg.warn(p, "公開前に: " + problem);
        } else {
            Msg.ok(p, "公開できる状態です（/pd publish）。");
        }
    }

    private static void help(ServerPlayer p, boolean op) {
        Msg.info(p, "―― 遊ぶ ――");
        Msg.info(p, "P キー ／ /pd ／ 受付の村人に話しかける ： メイン画面");
        Msg.info(p, "/pd play ： ダンジョンを選んで入場（パーティ全員で入ります）");
        Msg.info(p, "/pd exit ： ダンジョンから抜ける");
        Msg.info(p, "/pd party invite <名前> ／ accept ／ decline ／ leave ／ kick <名前> ／ list");
        if (!op) {
            return;
        }
        Msg.info(p, "―― 作る（OP） ――");
        Msg.info(p, "/pd workshop ： 作業場へ（クリエイティブ）。/pd exit で元の場所へ");
        Msg.info(p, "/pd create \"名前\" [広さ16-128] [高さ8-96] [壁のブロック] ： 外枠を自動建築");
        Msg.info(p, "/pd setstart ／ /pd setgoal [半径] ： 立っている場所をスタート／ゴールに");
        Msg.info(p, "敵・ゴール・スタートは、専用ブロックを置いて右クリックで設定します");
        Msg.info(p, "/pd clear boss|all|goal ： クリア条件");
        Msg.info(p, "/pd npc [名前] ： 受付の村人を置く ／ /pd npc remove ： 近くの受付を消す");
        Msg.info(p, "/pd info ／ publish ／ unpublish ／ rename ／ list ／ goto <名前> ／ rooms");
    }

    private PDCommands() {
    }
}
