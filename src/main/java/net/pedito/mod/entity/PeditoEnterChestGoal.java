package net.pedito.mod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

import java.util.EnumSet;

public class PeditoEnterChestGoal extends Goal {
    private final PeditoEntity pedito;
    private int delay;

    public PeditoEnterChestGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.pedito.getChestTarget() != null;
    }

    @Override
    public void start() {
        this.delay = 0;
    }

    @Override
    public void tick() {
        BlockPos target = this.pedito.getChestTarget();
        if (target == null) return;
        
        Vec3 targetCenter = Vec3.atCenterOf(target);
        
        if (this.pedito.distanceToSqr(targetCenter) > 1.5D) {
            this.pedito.getMoveControl().setWantedPosition(targetCenter.x, targetCenter.y, targetCenter.z, 2.5D);
            if (this.pedito.tickCount % 5 == 0 && this.pedito.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.pedito.getX(), this.pedito.getY() + 0.5, this.pedito.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            }
        } else {
            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, this.pedito.getX(), this.pedito.getY() + 0.5, this.pedito.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            }
            this.pedito.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.5F);
            this.pedito.discard(); // Enters chest and disappears
        }
    }

    @Override
    public void stop() {
        this.pedito.getNavigation().stop();
    }
}
