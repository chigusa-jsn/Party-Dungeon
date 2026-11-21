package com.chigusa.partydungeon.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * 上に立つと状態異常（や、いい効果）がかかる床。
 * 乗っているあいだ効果が続き、降りても少しのあいだ残る。
 * 挑戦中のコピーでも動くように、毎tickプレイヤーの足元を見て判定する。
 */
public class EffectFloorBlock extends Block {
    public final FloorKind kind;

    public EffectFloorBlock(FloorKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    /** プレイヤーの毎tick（サーバー側）。足元が状態異常の床なら効果をかける */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isSpectator() || p.isCreative() || !p.isAlive()) {
            return;
        }
        if (!p.onGround() || p.tickCount % 5 != 0) {
            return;
        }
        BlockState state = p.level().getBlockState(p.getOnPos());
        if (state.getBlock() instanceof EffectFloorBlock floor) {
            floor.apply(p);
        }
    }

    private void apply(Player p) {
        if (kind == FloorKind.FIRE) {
            if (!p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                p.igniteForSeconds(4);
                if (p.tickCount % 20 == 0) {
                    p.hurt(p.damageSources().hotFloor(), 1.0f);
                }
            }
            return;
        }
        MobEffectInstance now = p.getEffect(kind.effect);
        // すでに長くかかっていれば、かけ直さない（毒のダメージの間隔がリセットされないように）
        if (now != null && now.getAmplifier() >= kind.amplifier && now.getDuration() > kind.duration / 2) {
            return;
        }
        p.addEffect(new MobEffectInstance(kind.effect, kind.duration, kind.amplifier, false, true, true));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0 || !level.getBlockState(pos.above()).isAir()) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + 1.02;
        double z = pos.getZ() + random.nextDouble();
        if (kind == FloorKind.FIRE) {
            level.addParticle(random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, x, y, z, 0, 0.02, 0);
            return;
        }
        int c = kind.color;
        Vector3f rgb = new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
        level.addParticle(new DustParticleOptions(rgb, 1.0f), x, y, z, 0, 0.05, 0);
    }
}
