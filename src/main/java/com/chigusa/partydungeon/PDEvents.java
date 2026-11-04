package com.chigusa.partydungeon;

import com.chigusa.partydungeon.party.PartyManager;
import com.chigusa.partydungeon.run.RunManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** ゲーム中のイベントをまとめて受け取る */
public final class PDEvents {

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((RegisterCommandsEvent e) -> PDCommands.register(e.getDispatcher(), e.getBuildContext()));
        bus.addListener((ServerStartedEvent e) -> {
            RunManager.onServerStarted(e.getServer());
            if (Boolean.getBoolean("partydungeon.selftest")) {
                boolean ok = e.getServer().getLevel(PDDimensions.WORKSHOP) != null
                        && e.getServer().getLevel(PDDimensions.ARENA) != null;
                PartyDungeon.LOGGER.info(ok ? "PD-SELFTEST-OK" : "PD-SELFTEST-FAIL");
                e.getServer().halt(false);
            }
        });
        bus.addListener((ServerStoppingEvent e) -> {
            RunManager.reset();
            PartyManager.reset();
        });
        bus.addListener((ServerTickEvent.Post e) -> {
            RunManager.tick(e.getServer());
        });

        // ---- ブロックの保護（挑戦用の世界では壊せない・置けない・爆発で壊れない）
        bus.addListener((BlockEvent.BreakEvent e) -> {
            if (isArena(e.getLevel()) && !isAdmin(e.getPlayer())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (isArena(e.getLevel()) && !(e.getEntity() instanceof Player p && isAdmin(p))) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ExplosionEvent.Detonate e) -> {
            if (isArena(e.getLevel())) {
                // プレイヤーへのダメージはそのまま、ブロックだけ守る
                e.getAffectedBlocks().clear();
            }
        });

        // ウィザーやエンダードラゴンがブロックを壊すのも止める
        bus.addListener((LivingDestroyBlockEvent e) -> {
            if (isArena(e.getEntity().level())) {
                e.setCanceled(true);
            }
        });
        // エンダーマンがブロックを持ち去る等も止める
        bus.addListener((EntityMobGriefingEvent e) -> {
            if (e.getEntity() != null && isArena(e.getEntity().level())) {
                e.setCanGrief(false);
            }
        });

        // ---- 死亡と復活
        bus.addListener(EventPriority.LOWEST, (LivingDeathEvent e) -> {
            if (e.isCanceled()) {
                return;
            }
            if (e.getEntity() instanceof ServerPlayer p) {
                RunManager.onPlayerDeath(p);
            } else {
                RunManager.onMobDeath(e.getEntity());
            }
        });
        bus.addListener((LivingExperienceDropEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && RunManager.keepsExperience(p.getUUID())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((PlayerEvent.Clone e) -> {
            if (e.isWasDeath() && e.getEntity() instanceof ServerPlayer p) {
                RunManager.onPlayerClone(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                RunManager.onRespawn(p.server, p);
            }
        });

        // ---- 画面まわり
        bus.addListener(com.chigusa.partydungeon.menu.ChatInput::onChat);
        bus.addListener(com.chigusa.partydungeon.npc.NpcManager::onInteract);
        bus.addListener(com.chigusa.partydungeon.npc.NpcManager::onInteractSpecific);

        // ---- ログイン・ログアウト
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                RunManager.onLogin(p.server, p);
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                RunManager.onLogout(p.server, p);
                com.chigusa.partydungeon.menu.ChatInput.forget(p.getUUID());
                PartyManager.leave(p.server, p.getUUID(), p.getName().getString());
            }
        });
    }

    private static boolean isArena(LevelAccessor level) {
        return level instanceof Level l && l.dimension() == PDDimensions.ARENA;
    }

    /** OP のクリエイティブだけは例外（部屋の修理用） */
    private static boolean isAdmin(Player p) {
        return p != null && p.isCreative() && p.hasPermissions(2);
    }

    private PDEvents() {
    }
}
