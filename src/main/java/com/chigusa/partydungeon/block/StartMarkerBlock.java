package com.chigusa.partydungeon.block;

import com.chigusa.partydungeon.util.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** スタート地点。このブロックの上にプレイヤーが現れる */
public class StartMarkerBlock extends MarkerBlock {
    public StartMarkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            Msg.info(sp, "スタート地点です。このブロックの上にプレイヤーが現れます（設定はありません）。");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
