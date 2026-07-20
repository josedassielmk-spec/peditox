package net.pedito.mod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import java.util.EnumSet;
import java.util.List;

public class PeditoFollowAlphaTargetGoal extends TargetGoal {
    private final PeditoEntity pedito;
    private LivingEntity targetToAttack;

    public PeditoFollowAlphaTargetGoal(PeditoEntity pedito) {
        super(pedito, false);
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!this.pedito.isTamedByOwner() || this.pedito.getVariant() == PeditoEntity.VARIANT_ALPHA) {
            return false;
        }

        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) {
            return false;
        }

        // Find the Alpha of the owner's swarm
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
            PeditoEntity.class,
            this.pedito.getBoundingBox().inflate(32.0D),
            e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner && e.getVariant() == PeditoEntity.VARIANT_ALPHA
        );

        if (!allies.isEmpty()) {
            PeditoEntity alpha = allies.get(0);
            LivingEntity alphaTarget = alpha.getTarget();
            if (alphaTarget != null && alphaTarget.isAlive() && this.canAttack(alphaTarget, TargetingConditions.DEFAULT)) {
                this.targetToAttack = alphaTarget;
                return true;
            }
        }

        return false;
    }

    @Override
    public void start() {
        this.mob.setTarget(this.targetToAttack);
        super.start();
    }
}
