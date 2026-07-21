package net.pedito.mod.entity;

import java.util.EnumSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

public class PeditoDanceGoal extends Goal {

  private final PeditoEntity pedito;
  private int danceTimer;
  private boolean isJukeboxDancing; // If dancing due to a music disc

  public PeditoDanceGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    if (this.pedito.isWhistleActive()) return false;
    if (
      this.pedito.isSittingCustom() || this.pedito.getTarget() != null
    ) return false;
    if (
      this.pedito.isTamedByOwner() && this.pedito.isOwnerInDanger()
    ) return false;

    int chance = this.pedito.isBaby() ? 300 : 800;
    return (
      this.pedito.isJukeboxDancing() ||
      this.pedito.getRandom().nextInt(chance) == 0
    );
  }

  @Override
  public void start() {
    this.isJukeboxDancing = this.pedito.isJukeboxDancing();
    this.danceTimer = this.isJukeboxDancing ? 600 : 100; // 30 secs or 5 secs
  }

  @Override
  public boolean canContinueToUse() {
    if (this.pedito.isWhistleActive()) return false;
    if (
      this.pedito.isSittingCustom() || this.pedito.getTarget() != null
    ) return false;
    if (
      this.pedito.isTamedByOwner() && this.pedito.isOwnerInDanger()
    ) return false;

    if (this.isJukeboxDancing) return this.pedito.isJukeboxDancing();
    return this.danceTimer > 0;
  }

  @Override
  public void tick() {
    this.danceTimer--;

    // Small spins
    this.pedito.setYRot(this.pedito.getYRot() + 20.0F);
    this.pedito.setYHeadRot(this.pedito.getYHeadRot() + 20.0F);

    // Small jumps
    if (this.pedito.onGround() && this.pedito.getRandom().nextInt(5) == 0) {
      this.pedito.getJumpControl().jump();
    }

    if (
      this.danceTimer % 10 == 0 &&
      this.pedito.level() instanceof ServerLevel serverLevel
    ) {
      serverLevel.sendParticles(
        ParticleTypes.NOTE,
        this.pedito.getX(),
        this.pedito.getY() + 1.2,
        this.pedito.getZ(),
        1,
        0.2,
        0.2,
        0.2,
        1.0
      );
    }
  }
}
