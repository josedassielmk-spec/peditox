package net.pedito.mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModBlockEntities;

import java.util.ArrayList;
import java.util.List;

public class PeditoChestBlockEntity extends BlockEntity {
    private final List<CompoundTag> storedEntities = new ArrayList<>();

    public PeditoChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEDITO_CHEST_BE, pos, state);
    }

    public boolean storePedito(PeditoEntity pedito) {
        if (level == null) return false;
        
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        pedito.save(output);
        CompoundTag tag = output.buildResult();
        
        storedEntities.add(tag);
        setChanged();
        if (!level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public void releaseEntities(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        
        for (CompoundTag tag : storedEntities) {
            ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
            Entity entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.SPAWN_ITEM_USE, EntityProcessor.NOP);
            if (entity != null) {
                entity.setPos(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
                level.addFreshEntity(entity);
            }
        }
        storedEntities.clear();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void saveToItem(ItemStack stack, RegistryAccess registryAccess) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag entityTag : storedEntities) {
            list.add(entityTag.copy());
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("Entities", list);
        
        stack.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA, 
                net.minecraft.world.item.component.TypedEntityData.of(ModBlockEntities.PEDITO_CHEST_BE, wrapper));
    }

    public int getStoredCount() {
        return storedEntities.size();
    }

    @Override
    protected void saveAdditional(ValueOutput nbt) {
        super.saveAdditional(nbt);
        ListTag list = new ListTag();
        for (CompoundTag tag : storedEntities) {
            list.add(tag.copy());
        }
        CompoundTag wrapper = new CompoundTag();
        wrapper.put("Entities", list);
        nbt.store("StoredData", CompoundTag.CODEC, wrapper);
    }

    @Override
    protected void loadAdditional(ValueInput nbt) {
        super.loadAdditional(nbt);
        storedEntities.clear();
        nbt.read("StoredData", CompoundTag.CODEC).ifPresent(wrapper -> {
            if (wrapper.contains("Entities", 9)) { // 9 is ListTag
                ListTag list = wrapper.getList("Entities", 10); // 10 is CompoundTag
                for (int i = 0; i < list.size(); i++) {
                    storedEntities.add(list.getCompound(i).copy());
                }
            }
        });
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
