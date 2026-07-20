package net.pedito.mod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class PeditoWanderNearOwnerGoal extends Goal {
    private final PeditoEntity pedito;
    private double targetX;
    private double targetY;
    private double targetZ;

    public PeditoWanderNearOwnerGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.pedito.isWhistleActive()) return false;
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        if (this.pedito.isOwnerInDanger()) return false;
        
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        
        if (this.pedito.distanceToSqr(owner) > 1600.0D) return false;
        
        if (this.pedito.getRandom().nextInt(200) != 0) return false;

        Vec3 randomDir = new Vec3((this.pedito.getRandom().nextDouble() - 0.5) * 20.0, 
                                  (this.pedito.getRandom().nextDouble() - 0.5) * 4.0, 
                                  (this.pedito.getRandom().nextDouble() - 0.5) * 20.0);
                                  
        Vec3 dest = owner.position().add(randomDir);
        
        double distToOwnerSq = dest.distanceToSqr(owner.position());
        if (distToOwnerSq < 9.0D || distToOwnerSq > 100.0D) return false; // between 3 and 10 blocks

        BlockPos pos = BlockPos.containing(dest.x, dest.y, dest.z);
        if (this.pedito.isSolidBlock(pos)) return false;

        this.targetX = dest.x;
        this.targetY = dest.y;
        this.targetZ = dest.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.pedito.isWhistleActive()) return false;
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        if (this.pedito.isOwnerInDanger()) return false;
        
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        
        return this.pedito.distanceToSqr(this.targetX, this.targetY, this.targetZ) > 1.0D && this.pedito.distanceToSqr(owner) <= 1600.0D;
    }

    @Override
    public void start() {
        this.pedito.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, 0.8D);
    }
}
