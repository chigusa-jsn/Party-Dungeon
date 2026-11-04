package com.chigusa.partydungeon.block;

import javax.annotation.Nullable;

import com.chigusa.partydungeon.net.PDNetwork;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** 敵の出現ブロック。右クリックで設定画面 */
public class SpawnerMarkerBlock extends BaseEntityBlock {
    public static final MapCodec<SpawnerMarkerBlock> CODEC = simpleCodec(SpawnerMarkerBlock::new);

    public SpawnerMarkerBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerMarkerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && sp.hasPermissions(2)
                && level.getBlockEntity(pos) instanceof SpawnerMarkerBlockEntity be) {
            CompoundTag t = be.saveData();
            t.putLong("pos", pos.asLong());
            PDNetwork.openScreen(sp, "spawner", t);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
