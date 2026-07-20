package net.pedito.mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.pedito.mod.block.entity.PeditoChestBlockEntity;
import net.pedito.mod.item.PeditoStaffItem;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModSounds;
import net.minecraft.sounds.SoundSource;

import java.util.List;

public class PeditoChestBlock extends BaseEntityBlock {
    public static final MapCodec<PeditoChestBlock> CODEC = simpleCodec(PeditoChestBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PeditoChestBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<PeditoChestBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PeditoChestBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().getItem() instanceof PeditoStaffItem) {
            if (!level.isClientSide()) {
                level.playSound(null, pos, ModSounds.PEDITO_STAFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                
                AABB searchAABB = new AABB(pos).inflate(128.0D);
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, searchAABB, 
                    (pedito) -> pedito.getOwnerCustom() == player
                );
                
                for (PeditoEntity pedito : peditos) {
                    pedito.setChestTarget(pos);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
