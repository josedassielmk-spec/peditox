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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModBlockEntities;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.util.ProblemReporter;

public class PeditoChestBlockEntity extends BlockEntity {

    private final List<CompoundTag> storedEntities = new ArrayList<>();
    
    private float openNess;
    private float oOpenNess;
    private boolean open;

    public PeditoChestBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.PEDITO_CHEST_BE, blockPos, blockState);
    }

    public boolean storePedito(PeditoEntity pedito) {
        if (level == null) return false;
        
        CompoundTag tag = new CompoundTag();
        try (var problems = new ProblemReporter.ScopedCollector(pedito.problemPath(), Entity.LOGGER)) {
            var output = TagValueOutput.createWithContext(problems, level.registryAccess());
            pedito.saveWithoutId(output);
            tag = output.buildResult();
            
            storedEntities.add(tag);
            setChanged();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean releaseLastEntity(Level level, BlockPos pos) {
        if (storedEntities.isEmpty()) {
            return false;
        }
        CompoundTag tag = storedEntities.remove(storedEntities.size() - 1);
        Entity entity = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.TRIGGERED, e -> {
            e.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0);
            return e;
        });
        
        if (entity != null) {
            level.addFreshEntity(entity);
            setChanged();
            return true;
        }
        return false;
    }

    public void releaseEntities(Level level, BlockPos pos) {
        while (!storedEntities.isEmpty()) {
            releaseLastEntity(level, pos);
        }
    }

    public int getStoredCount() {
        return storedEntities.size();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    }

    public float getOpenNess(float tickDelta) {
        return Mth.lerp(tickDelta, this.oOpenNess, this.openNess);
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, PeditoChestBlockEntity blockEntity) {
        blockEntity.oOpenNess = blockEntity.openNess;
        if (blockEntity.open) {
            blockEntity.openNess += 0.1f;
            if (blockEntity.openNess >= 1.0f) {
                blockEntity.openNess = 1.0f;
                blockEntity.open = false;
            }
        } else {
            blockEntity.openNess -= 0.1f;
            if (blockEntity.openNess <= 0.0f) {
                blockEntity.openNess = 0.0f;
            }
        }
    }
}
