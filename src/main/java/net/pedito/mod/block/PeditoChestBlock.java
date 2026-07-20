package net.pedito.mod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.pedito.mod.block.entity.PeditoChestBlockEntity;
import net.pedito.mod.item.PeditoStaffItem;
import net.pedito.mod.entity.PeditoEntity;
import java.util.List;

public class PeditoChestBlock extends BaseEntityBlock {
    public static final MapCodec<PeditoChestBlock> CODEC = simpleCodec(PeditoChestBlock::new);

    public PeditoChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<PeditoChestBlock> codec() {
        return CODEC;
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
                AABB searchAABB = new AABB(pos).inflate(128.0D);
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, searchAABB, 
                    (pedito) -> pedito.getOwnerCustom() == player
                );
                
                for (PeditoEntity pedito : peditos) {
                    pedito.discard(); // Simplified capture
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
