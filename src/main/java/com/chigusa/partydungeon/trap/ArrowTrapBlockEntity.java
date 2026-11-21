package com.chigusa.partydungeon.trap;

import java.util.List;

import com.chigusa.partydungeon.registry.PDRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 矢の発射台の設定と、撃つタイミング */
public class ArrowTrapBlockEntity extends BlockEntity {
    private static final int[] INTERVALS = {10, 20, 40, 60, 100};
    /** プレイヤーを探す距離 */
    private static final int RANGE = 16;

    public int interval = 40;
    /** true: 人がいなくても撃ち続ける / false: 前に人が来たときだけ */
    public boolean always;
    private int cooldown;

    public ArrowTrapBlockEntity(BlockPos pos, BlockState state) {
        super(PDRegistry.ARROW_TRAP_BE.get(), pos, state);
    }

    public void nextInterval() {
        int i = 0;
        while (i < INTERVALS.length && INTERVALS[i] != interval) {
            i++;
        }
        interval = INTERVALS[(i + 1) % INTERVALS.length];
    }

    public String describe() {
        return (interval / 20.0) + " 秒おき・" + (always ? "いつでも撃つ" : "前に人が来たら撃つ")
                + "（右クリック: 間隔 / スニーク＋右クリック: 撃ち方）";
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ArrowTrapBlockEntity be) {
        if (be.cooldown > 0) {
            be.cooldown--;
            return;
        }
        Direction dir = state.getValue(ArrowTrapBlock.FACING);
        if (!be.always && !be.seesPlayer((ServerLevel) level, pos, dir)) {
            be.cooldown = 5;
            return;
        }
        be.cooldown = be.interval;
        be.shoot((ServerLevel) level, pos, dir);
    }

    private Vec3 muzzle(BlockPos pos, Direction dir) {
        return Vec3.atCenterOf(pos).add(dir.getStepX() * 0.7, dir.getStepY() * 0.7 - (dir.getAxis().isVertical() ? 0 : 0.1),
                dir.getStepZ() * 0.7);
    }

    /** 前方（幅3マス）に、壁にさえぎられずに見えるプレイヤーがいるか */
    private boolean seesPlayer(ServerLevel level, BlockPos pos, Direction dir) {
        BlockPos a = pos.relative(dir);
        BlockPos b = pos.relative(dir, RANGE);
        AABB box = new AABB(Vec3.atLowerCornerOf(a), Vec3.atLowerCornerOf(b).add(1, 1, 1));
        box = switch (dir.getAxis()) {
            case X -> box.inflate(0, 1, 1);
            case Y -> box.inflate(1, 0, 1);
            case Z -> box.inflate(1, 1, 0);
        };
        List<Player> players = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        Vec3 from = muzzle(pos, dir);
        for (Player p : players) {
            HitResult hit = level.clip(new ClipContext(from, p.getEyePosition(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }

    private void shoot(ServerLevel level, BlockPos pos, Direction dir) {
        Vec3 m = muzzle(pos, dir);
        Arrow arrow = new Arrow(level, m.x, m.y, m.z, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.shoot(dir.getStepX(), dir.getStepY() + (dir.getAxis().isVertical() ? 0 : 0.05), dir.getStepZ(), 1.6f, 1.0f);
        level.addFreshEntity(arrow);
        level.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider reg) {
        super.saveAdditional(t, reg);
        t.putInt("interval", interval);
        t.putBoolean("always", always);
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider reg) {
        super.loadAdditional(t, reg);
        if (t.contains("interval")) {
            interval = Math.max(5, t.getInt("interval"));
        }
        always = t.getBoolean("always");
    }
}
