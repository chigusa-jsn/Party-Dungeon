package com.chigusa.partydungeon.trap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 崩れる床。乗ると少しして消え、しばらくすると元に戻る。
 * 0 = ふつう / 1 = ひび（もうすぐ消える）/ 2 = 消えている
 */
public class CrumbleFloorBlock extends Block {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    /** 乗ってから消えるまで */
    private static final int CRACK_TICKS = 12;
    /** 消えてから戻るまで */
    private static final int BACK_TICKS = 60;

    public CrumbleFloorBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(STAGE);
    }

    /** プレイヤーが乗った（TrapEvents から） */
    void trigger(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_CLIENTS);
            level.scheduleTick(pos, this, CRACK_TICKS);
            level.playSound(null, pos, SoundEvents.GRAVEL_HIT, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int stage = state.getValue(STAGE);
        if (stage == 1) {
            level.setBlock(pos, state.setValue(STAGE, 2), Block.UPDATE_CLIENTS);
            level.levelEvent(2001, pos, Block.getId(Blocks.SANDSTONE.defaultBlockState()));
            level.scheduleTick(pos, this, BACK_TICKS);
        } else if (stage == 2) {
            level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }

    /** 消えたまま写された（挑戦用のコピーなど）ときも、ちゃんと戻るように */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (!level.isClientSide && state.getValue(STAGE) != 0 && !old.is(this)) {
            level.scheduleTick(pos, this, BACK_TICKS);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(STAGE) == 2 ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(STAGE) == 2 ? Shapes.empty() : Shapes.block();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == 1) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SANDSTONE.defaultBlockState()),
                        pos.getX() + random.nextDouble(), pos.getY() - 0.05, pos.getZ() + random.nextDouble(), 0, 0, 0);
            }
        }
    }
}
