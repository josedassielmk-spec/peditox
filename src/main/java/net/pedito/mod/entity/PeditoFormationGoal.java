package net.pedito.mod.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class PeditoFormationGoal extends Goal {
    private final PeditoEntity pedito;
    private double hoverX;
    private double hoverY;
    private double hoverZ;

    public PeditoFormationGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        if (this.pedito.isOwnerInDanger()) return false;

        // Peditos currently holding a front-guard slot are handled entirely by
        // PeditoFrontGuardGoal; this goal must not also try to place them.
        if (PeditoFrontGuardGoal.isFrontGuard(this.pedito)) return false;

        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;

        return this.pedito.distanceToSqr(owner) <= 225.0D;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.isSittingCustom()) return false;
        if (this.pedito.isOwnerInDanger()) return false;

        if (PeditoFrontGuardGoal.isFrontGuard(this.pedito)) return false;

        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return false;

        return this.pedito.distanceToSqr(owner) <= 225.0D;
    }

    @Override
    public void start() {
    }

    @Override
    public void tick() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) return;

        // Front-guard peditos are excluded here too: they occupy their own
        // fixed flank slots and must not be counted when spacing out the
        // rear semicircle, or the arc math would leave a gap where they
        // "should" be.
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                owner.getBoundingBox().inflate(64.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
                        && !PeditoFrontGuardGoal.isFrontGuard(e)
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));

        int total = allies.size();
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;

        double time = this.pedito.tickCount * 0.05;
        boolean isPlayerMoving = owner.getDeltaMovement().lengthSqr() > 0.01;

        // Base angle is opposite to the direction the player is looking
        double baseAngle = Math.toRadians(owner.getYRot()); // Actually behind the player 

        if (isPlayerMoving) {
            double spread = 1.5;
            double row = Math.floor(myIndex / 2.0) + 1;
            double side = (myIndex % 2 == 0) ? 1 : -1;

            if (myIndex == 0) { row = 0; side = 0; }
            double offsetX = Math.sin(baseAngle) * (row * 1.5) + Math.cos(baseAngle) * (side * spread * row);
            double offsetZ = -Math.cos(baseAngle) * (row * 1.5) + Math.sin(baseAngle) * (side * spread * row);

            double offsetY = 1.5 + Math.sin(time + myIndex) * 0.3;

            this.hoverX = owner.getX() + offsetX;
            this.hoverY = owner.getY() + offsetY;
            this.hoverZ = owner.getZ() + offsetZ;
        } else {
            // Semicircle behind the player
            double arcSpan = Math.PI; // 180 degrees
            if (total <= 6) {
                double fraction = (total == 1) ? 0.5 : (double) myIndex / (total - 1);
                // Center around baseAngle
                double currentAngle = baseAngle - (arcSpan / 2.0) + (fraction * arcSpan);

                // Add a little breathing movement
                currentAngle += Math.sin(time * 0.5) * 0.1;

                double radius = 3.5;
                double offsetX = Math.sin(currentAngle) * radius;
                double offsetZ = -Math.cos(currentAngle) * radius;
                double offsetY = 1.0 + Math.sin(time * 1.5 + myIndex) * 0.4;

                this.hoverX = owner.getX() + offsetX;
                this.hoverY = owner.getY() + offsetY;
                this.hoverZ = owner.getZ() + offsetZ;
            } else {
                // Multiple semicircles behind the player
                int ringIndex = myIndex % 3;
                double radius = 3.5 + (ringIndex * 1.5);
                int countInRing = (int) Math.ceil(total / 3.0);
                int posInRing = myIndex / 3;

                double fraction = (countInRing == 1) ? 0.5 : (double) posInRing / (countInRing - 1);
                double currentAngle = baseAngle - (arcSpan / 2.0) + (fraction * arcSpan);

                currentAngle += Math.sin(time * 0.5 + ringIndex) * 0.1;

                double offsetX = Math.sin(currentAngle) * radius;
                double offsetZ = -Math.cos(currentAngle) * radius;
                double offsetY = 1.0 + ringIndex * 0.8 + Math.sin(time + myIndex) * 0.3;

                this.hoverX = owner.getX() + offsetX;
                this.hoverY = owner.getY() + offsetY;
                this.hoverZ = owner.getZ() + offsetZ;
            }
        }

        this.pedito.getLookControl().setLookAt(owner, 10.0F, 10.0F);

        net.minecraft.core.BlockPos targetPos = net.minecraft.core.BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
        int adjustmentCount = 0;
        while (this.pedito.isSolidBlock(targetPos) && adjustmentCount < 5) {
            this.hoverY += 1.0;
            targetPos = net.minecraft.core.BlockPos.containing(this.hoverX, this.hoverY, this.hoverZ);
            adjustmentCount++;
        }

        if (this.pedito.distanceToSqr(this.hoverX, this.hoverY, this.hoverZ) > 0.25) {
            this.pedito.getMoveControl().setWantedPosition(this.hoverX, this.hoverY, this.hoverZ, 1.0D);
        }
    }
}
