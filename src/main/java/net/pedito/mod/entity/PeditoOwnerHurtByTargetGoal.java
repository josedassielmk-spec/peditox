package net.pedito.mod.entity;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

public class PeditoOwnerHurtByTargetGoal extends TargetGoal {

  private final PeditoEntity pedito;
  private LivingEntity attacker;
  private int timestamp;

  public PeditoOwnerHurtByTargetGoal(PeditoEntity pedito) {
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
        this.attacker = owner.getLastHurtByMob();
        int i = owner.getLastHurtByMobTimestamp();
        return (
          i != this.timestamp &&
          this.canAttack(this.attacker, TargetingConditions.DEFAULT) &&
          this.pedito.wantsToAttack(this.attacker, owner)
        );
      }
    }
  }

  @Override
  public void start() {
    this.mob.setTarget(this.attacker);
    LivingEntity owner = this.pedito.getOwnerCustom();
    if (owner != null) {
      this.timestamp = owner.getLastHurtByMobTimestamp();
    }
    super.start();
  }
}
