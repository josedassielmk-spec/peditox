package net.pedito.mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModBlockEntities;

import java.util.ArrayList;
import java.util.List;

public class PeditoChestBlockEntity extends BlockEntity {
    private final List<CompoundTag> storedEntities = new ArrayList<>();
    
    // Variables para la interpolación de animación
    private float openNess;
    private float oOpenNess;
    private int openCount;

    public PeditoChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEDITO_CHEST_BE, pos, state);
    }

    public boolean storePedito(PeditoEntity pedito) {
        if (level == null) return false;
        
        CompoundTag tag = new CompoundTag();
        if (pedito.saveAsPassenger(tag)) {
            storedEntities.add(tag);
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return true;
        }
        return false;
    }

    public boolean releaseLastEntity(Level level, BlockPos pos) {
        if (level == null || level.isClientSide() || storedEntities.isEmpty()) return false;
        
        CompoundTag tag = storedEntities.remove(storedEntities.size() - 1);
        Entity entity = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.TRIGGERED, e -> {
            e.setPos(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
            return e;
        });
        
        if (entity != null) {
            level.addFreshEntity(entity);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return true;
        }
        
        // Si falló, devolverlo a la lista
        storedEntities.add(tag);
        return false;
    }

    public int getStoredCount() {
        return storedEntities.size();
    }

    public List<CompoundTag> getStoredEntities() {
        return storedEntities;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (CompoundTag entityTag : storedEntities) {
            list.add(entityTag.copy());
        }
        tag.put("Entities", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedEntities.clear();
        if (tag.contains("Entities", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                storedEntities.add(list.getCompound(i).copy());
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    // Gestion de la animacion de apertura
    public static void ticker(Level level, BlockPos pos, BlockState state, PeditoChestBlockEntity blockEntity) {
        blockEntity.oOpenNess = blockEntity.openNess;
        if (blockEntity.openCount > 0 && blockEntity.openNess == 0.0F) {
            // Sonido de abrir (opcional)
        }
        if (blockEntity.openCount == 0 && blockEntity.openNess == 1.0F) {
            // Sonido de cerrar (opcional)
        }
        
        float target = blockEntity.openCount > 0 ? 1.0F : 0.0F;
        if (blockEntity.openNess < target) {
            blockEntity.openNess = Mth.clamp(blockEntity.openNess + 0.1F, 0.0F, 1.0F);
        } else if (blockEntity.openNess > target) {
            blockEntity.openNess = Mth.clamp(blockEntity.openNess - 0.1F, 0.0F, 1.0F);
        }
    }
    
    public void setOpen(boolean open) {
        this.openCount = open ? 1 : 0;
    }

    public float getOpenNess(float partialTicks) {
        return Mth.lerp(partialTicks, this.oOpenNess, this.openNess);
    }
}
