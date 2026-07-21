package net.pedito.mod.entity;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

public class PeditoAlphaDefendVulnerableGoal extends TargetGoal {

  private final PeditoEntity pedito;
  private LivingEntity targetToAttack;

  public PeditoAlphaDefendVulnerableGoal(PeditoEntity pedito) {
    super(pedito, false);
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.TARGET));
  }

  @Override
  public boolean canUse() {
    if (
      !this.pedito.isTamedByOwner() ||
      (this.pedito.getVariant() != PeditoEntity.VARIANT_ALPHA &&
        this.pedito.getVariant() != PeditoEntity.VARIANT_GOLDEN)
    ) {
      return false;
    }

    Player owner = this.pedito.getOwnerCustom();
    if (owner == null) {
      return false;
    }

    // 1. Defend Owner if low health (< 50%) and has an active attacker
    if (owner.getHealth() < owner.getMaxHealth() * 0.5F) {
      LivingEntity ownerAttacker = owner.getLastHurtByMob();
      if (
        ownerAttacker != null &&
        ownerAttacker.isAlive() &&
        this.canAttack(ownerAttacker, TargetingConditions.DEFAULT)
      ) {
        this.targetToAttack = ownerAttacker;
        return true;
      }
    }

    // 2. Defend other vulnerable allies (Peditos of the same owner under 50% health being attacked)
    List<PeditoEntity> allies = this.pedito
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        this.pedito.getBoundingBox().inflate(24.0D),
        e ->
          e.isAlive() &&
          e.isTamedByOwner() &&
          e.getOwnerCustom() == owner &&
          e != this.pedito
      );

    for (PeditoEntity ally : allies) {
      if (ally.getHealth() < ally.getMaxHealth() * 0.5F) {
        LivingEntity allyAttacker = ally.getLastHurtByMob();
        if (
          allyAttacker != null &&
          allyAttacker.isAlive() &&
          this.canAttack(allyAttacker, TargetingConditions.DEFAULT)
        ) {
          this.targetToAttack = allyAttacker;
          return true;
        }
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
