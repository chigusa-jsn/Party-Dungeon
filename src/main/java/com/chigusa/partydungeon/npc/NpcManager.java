package com.chigusa.partydungeon.npc;

import com.chigusa.partydungeon.menu.Menus;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 「ダンジョン受付」の村人。話しかけるとメイン画面が開く。
 * 普通の村人に目印（タグ）をつけて、動かない・倒れない・取引しないようにしている。
 */
public final class NpcManager {
    public static final String TAG = "partydungeon_npc";

    public static void spawn(ServerPlayer p, String name) {
        ServerLevel level = p.serverLevel();
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) {
            Msg.warn(p, "受付を置けませんでした。");
            return;
        }
        v.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0);
        v.setYHeadRot(p.getYRot());
        v.setYBodyRot(p.getYRot());
        v.setNoAi(true);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistenceRequired();
        v.setCustomName(Component.literal(name).withStyle(ChatFormatting.GOLD));
        v.setCustomNameVisible(true);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.CARTOGRAPHER).setLevel(5));
        v.addTag(TAG);
        level.addFreshEntity(v);
        Msg.ok(p, "「" + name + "」を置きました。話しかけるとメイン画面が開きます。消すときは近くで /pd npc remove");
    }

    public static void removeNearest(ServerPlayer p) {
        Entity nearest = null;
        double best = 8 * 8;
        for (Entity e : p.serverLevel().getEntities(p, p.getBoundingBox().inflate(8), e -> e.getTags().contains(TAG))) {
            double d = e.distanceToSqr(p);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            Msg.warn(p, "近く（8ブロック以内）に受付がいません。");
            return;
        }
        nearest.discard();
        Msg.ok(p, "受付を消しました。");
    }

    /** 受付に話しかけたらメイン画面を開く（村人の取引画面は出さない） */
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!e.getTarget().getTags().contains(TAG)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() == InteractionHand.MAIN_HAND && e.getEntity() instanceof ServerPlayer sp) {
            Menus.main(sp.server, sp);
        }
    }

    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        if (e.getTarget().getTags().contains(TAG)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private NpcManager() {
    }
}
