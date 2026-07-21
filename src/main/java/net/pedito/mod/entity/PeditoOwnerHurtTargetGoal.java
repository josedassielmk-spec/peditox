package net.pedito.mod.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public class PeditoOwnerHurtTargetGoal extends TargetGoal {

  private final PeditoEntity pedito;
  private LivingEntity ownerLastHurt;
  private int timestamp;

  public PeditoOwnerHurtTargetGoal(PeditoEntity pedito) {
    super(pedito, false);
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.TARGET));
  }

  @Override
  public boolean canUse() {
    if (!this.pedito.isTamedByOwner()) {
      return false;
    } else {
      LivingEntity owner = this.pedito.getOwnerCustom();
      if (owner == null) {
        return false;
      } else {
        this.ownerLastHurt = owner.getLastHurtMob();
        int i = owner.getLastHurtMobTimestamp();
        return (
          i != this.timestamp &&
          this.canAttack(this.ownerLastHurt, TargetingConditions.DEFAULT) &&
          this.pedito.wantsToAttack(this.ownerLastHurt, owner)
        );
      }
    }
  }

  @Override
  public void start() {
    this.mob.setTarget(this.ownerLastHurt);
    LivingEntity owner = this.pedito.getOwnerCustom();
    if (owner != null) {
      this.timestamp = owner.getLastHurtMobTimestamp();
    }
    super.start();
  }
}
