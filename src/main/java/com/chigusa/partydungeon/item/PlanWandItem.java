package com.chigusa.partydungeon.item;

import com.chigusa.partydungeon.net.PDNetwork;
import com.chigusa.partydungeon.util.Msg;
import com.chigusa.partydungeon.workshop.PlanBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** 間取りの杖：描いた間取りを右クリックすると、部屋を建てる画面が開く */
public class PlanWandItem extends Item {
    public PlanWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel level && ctx.getPlayer() instanceof ServerPlayer sp) {
            if (!sp.hasPermissions(2)) {
                return InteractionResult.FAIL;
            }
            PlanBuilder.Plan plan = PlanBuilder.read(level, ctx.getClickedPos());
            if (plan == null) {
                Msg.warn(sp, "間取りブロック（白か赤）を右クリックしてください。");
                return InteractionResult.FAIL;
            }
            CompoundTag t = new CompoundTag();
            t.putLong("pos", ctx.getClickedPos().asLong());
            t.putInt("w", plan.w());
            t.putInt("h", plan.h());
            t.putInt("cells", plan.cellCount());
            t.putBoolean("hasStart", plan.start() != null);
            t.putInt("maxSize", PlanBuilder.MAX_SIZE);
            PDNetwork.openScreen(sp, "plan", t);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }
}
