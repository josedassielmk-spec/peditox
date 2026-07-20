package net.pedito.mod.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class PeditoHoverOwnerGoal extends Goal {
    private final PeditoEntity pedito;
    private double hoverX;
    private double hoverY;
    private double hoverZ;

    public PeditoHoverOwnerGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        
        double distSq = this.pedito.distanceToSqr(owner);
        return this.pedito.isOwnerInDanger() || distSq > 64.0D; // Follow owner when further than 8 blocks
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;
        
        if (this.pedito.isOwnerInDanger()) return true;
        
        return this.pedito.distanceToSqr(owner) > 25.0D; // Keep approaching until 5 blocks
    }

    @Override
    public void start() {
    }

    @Override
    public void tick() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return;

        if (!this.pedito.isOwnerInDanger()) {
            // Just move towards player until 5 blocks away
            this.pedito.getLookControl().setLookAt(owner, 10.0F, 10.0F);
            this.pedito.getMoveControl().setWantedPosition(owner.getX(), owner.getY() + owner.getBbHeight() / 2.0, owner.getZ(), 1.5D);
            return;
        }

        // Combat hover (layered dome behind and around the player, keeping line of sight clear)
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                owner.getBoundingBox().inflate(64.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        
        int total = allies.size();
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;

        double time = this.pedito.tickCount * 0.05;
        
        int layer = 0;
        int peditosInCurrentLayer = 5;
        int peditosBeforeCurrentLayer = 0;
        
        while (myIndex >= peditosBeforeCurrentLayer + peditosInCurrentLayer) {
            peditosBeforeCurrentLayer += peditosInCurrentLayer;
            layer++;
            peditosInCurrentLayer += 3; // 5, 8, 11, etc.
        }
        
        int indexInLayer = myIndex - peditosBeforeCurrentLayer;
        int peditosInThisLayer = Math.min(peditosInCurrentLayer, total - peditosBeforeCurrentLayer);
        
        double radius = 2.5 + layer * 1.5;
        
        // Base angle is directly behind the player to leave the front clear
        double baseAngle = Math.toRadians(owner.getYRot()); 
        double arcSpan = Math.toRadians(240); // 240 degrees (leaves 120 deg front clear)
        
        double fraction = (peditosInThisLayer <= 1) ? 0.5 : (double) indexInLayer / (peditosInThisLayer - 1);
        double currentAngle = baseAngle - (arcSpan / 2.0) + (fraction * arcSpan);
        
        // Add slow rotation to the dome for a dynamic shield feel
        currentAngle += Math.sin(time * 0.5 + layer) * 0.1; 
        
        double offsetX = Math.sin(currentAngle) * radius;
        double offsetZ = -Math.cos(currentAngle) * radius;
        
        // Height increases with layers to form a dome shape, plus some sinusoidal hover
        double baseHeight = 1.0 + layer * 0.8;
        // Make the ones in the middle of the arc slightly higher to curve the dome over the player
        double heightArc = Math.sin(fraction * Math.PI); 
        double offsetY = baseHeight + (heightArc * 1.5) + Math.sin(time * 1.5 + myIndex) * 0.4;

        this.hoverX = owner.getX() + offsetX;
        this.hoverY = owner.getY() + offsetY;
        this.hoverZ = owner.getZ() + offsetZ;

        this.pedito.getLookControl().setLookAt(owner, 10.0F, 10.0F);

        net.minecraft.core.BlockPos targetPos = net.minecraft.core.BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
        int adjustmentCount = 0;
        while (this.pedito.isSolidBlock(targetPos) && adjustmentCount < 5) {
            this.hoverY += 1.0;
            targetPos = net.minecraft.core.BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
            adjustmentCount++;
        }

        if (this.pedito.distanceToSqr(this.hoverX, this.hoverY, this.hoverZ) > 0.25) {
            this.pedito.getMoveControl().setWantedPosition(this.hoverX, this.hoverY, this.hoverZ, 1.4D);
        }
    }
}