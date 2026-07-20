package net.pedito.mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.pedito.mod.registry.ModBlockEntities;

public class PeditoChestBlockEntity extends BlockEntity {
    public PeditoChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEDITO_CHEST_BE, pos, state);
    }
}
