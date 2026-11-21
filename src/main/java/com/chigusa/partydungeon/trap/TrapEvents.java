package com.chigusa.partydungeon.trap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.chigusa.partydungeon.registry.PDRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 床の罠をプレイヤーの毎tickで判定する（挑戦用のコピーでも確実に動くように）。
 * ・状態異常の床：効果をかける
 * ・崩れる床：乗ったら崩れ始める
 * ・落下リターンブロック：落ちてきたら、最後に安全に立っていた場所へ戻す
 */
public final class TrapEvents {
    private record Safe(ResourceKey<Level> dim, Vec3 pos) {
    }

    /** 最後に安全に立っていた場所 */
    private static final Map<UUID, Safe> SAFE = new HashMap<>();
    /** 戻すときに受けるダメージ */
    private static final float RETURN_DAMAGE = 2.0f;

    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isSpectator() || !p.isAlive()) {
            return;
        }
        ServerLevel level = p.serverLevel();
        if (checkReturn(p, level)) {
            return;
        }
        if (!p.onGround()) {
            return;
        }
        BlockPos on = p.getOnPos();
        BlockState state = level.getBlockState(on);
        if (state.getBlock() instanceof CrumbleFloorBlock crumble) {
            if (!p.isCreative()) {
                crumble.trigger(level, on, state);
            }
            return;
        }
        if (state.getBlock() instanceof EffectFloorBlock floor) {
            if (!p.isCreative() && p.tickCount % 5 == 0) {
                floor.apply(p);
            }
            return;
        }
        if (!state.isAir() && state.getBlock() != PDRegistry.FALL_RETURN.get()) {
            SAFE.put(p.getUUID(), new Safe(level.dimension(), Vec3.atBottomCenterOf(on.above())));
        }
    }

    /** 落ちてきて、足元（3マス下まで）に落下リターンブロックがあれば戻す */
    private static boolean checkReturn(ServerPlayer p, ServerLevel level) {
        if (p.getDeltaMovement().y > 0.01 && !p.onGround()) {
            return false;
        }
        if (p.getAbilities().flying) {
            return false;
        }
        BlockPos feet = p.blockPosition();
        boolean hit = false;
        for (int dy = 0; dy <= 3; dy++) {
            BlockState s = level.getBlockState(feet.below(dy));
            if (s.getBlock() == PDRegistry.FALL_RETURN.get()) {
                hit = true;
                break;
            }
            if (dy > 0 && !s.isAir()) {
                // 間にほかのブロックがある（ふつうの床の上にいる）
                break;
            }
        }
        if (!hit) {
            return false;
        }
        Safe safe = SAFE.get(p.getUUID());
        if (safe == null || safe.dim() != level.dimension() || safe.pos().distanceToSqr(p.position()) > 96 * 96) {
            return false;
        }
        p.teleportTo(safe.pos().x, safe.pos().y, safe.pos().z);
        p.setDeltaMovement(Vec3.ZERO);
        p.resetFallDistance();
        p.hurtMarked = true;
        if (!p.isCreative()) {
            p.hurt(p.damageSources().fall(), RETURN_DAMAGE);
        }
        level.playSound(null, p.blockPosition(), SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.0f);
        p.displayClientMessage(Component.literal("落ちてしまった…！ 元の場所に戻りました"), true);
        return true;
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SAFE.remove(e.getEntity().getUUID());
    }

    private TrapEvents() {
    }
}
