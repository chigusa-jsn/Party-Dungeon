package com.chigusa.partydungeon.block;

import com.chigusa.partydungeon.data.Difficulty;
import com.chigusa.partydungeon.data.DungeonDef;
import com.chigusa.partydungeon.data.PDData;
import com.chigusa.partydungeon.menu.RewardMenu;
import com.chigusa.partydungeon.util.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 報酬チェスト。右クリックで、このダンジョンの報酬画面（置いた場所に意味はない） */
public class RewardMarkerBlock extends MarkerBlock {
    public RewardMarkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && sp.hasPermissions(2)) {
            DungeonDef d = PDData.get(sp.server).findByPlotPos(pos);
            if (d == null) {
                Msg.warn(sp, "ダンジョンの区画の中に置いてください。");
            } else {
                RewardMenu.open(sp.server, sp, d.id, Difficulty.NORMAL);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
