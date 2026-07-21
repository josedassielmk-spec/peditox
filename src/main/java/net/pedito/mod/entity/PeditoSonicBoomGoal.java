package net.pedito.mod.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

public class PeditoSonicBoomGoal extends Goal {

  private final PeditoEntity pedito;
  private long nextAttackTick;
  private int attackTimer;

  public PeditoSonicBoomGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    PeditoEntity.SquadRole role = this.pedito.getSquadRole();
    if (
      role != PeditoEntity.SquadRole.TACTICAL &&
      role != PeditoEntity.SquadRole.SOLO
    ) {
      return false;
    }

    if (this.pedito.tickCount < this.nextAttackTick) return false;
    LivingEntity target = this.pedito.getTarget();
    if (
      target == null || !target.isAlive() || !this.pedito.isTamedByOwner()
    ) return false;
    if (this.pedito.getAllyCount() < 4) return false;
    return this.pedito.getRandom().nextInt(50) == 0;
  }

  @Override
  public void start() {
    this.attackTimer = 60; // 3 seconds total (1.5 positioning, 1.5 cast)
    this.pedito.playAttackVoice(0.5F); // Deep voice charge, now louder and animated
  }

  @Override
  public void tick() {
    LivingEntity target = this.pedito.getTarget();
    if (target != null) {
      this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);
    } else {
      this.attackTimer = 0;
      return;
    }

    List<PeditoEntity> allies = this.pedito
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        target.getBoundingBox().inflate(16.0D),
        e -> e.isAlive() && e.getTarget() == target
      );
    allies.sort(
      Comparator.comparingInt(net.minecraft.world.entity.Entity::getId)
    );
    int total = allies.size();
    int myIndex = allies.indexOf(this.pedito);
    if (myIndex == -1) myIndex = 0;

    // Positioning Phase
    if (this.attackTimer > 30) {
      Vec3 dirToTarget = target
        .position()
        .subtract(this.pedito.position())
        .normalize();

      // Sonic Boom — aproximación frontal
      // encarado 180° opuesto a la mirada, cono 45°, 1.8 bloques

      // Spread along a small arc if multiple
      double angleSpread = Math.toRadians(45) / Math.max(1, total - 1);
      double angleOffset = (myIndex - (total - 1) / 2.0) * angleSpread;

      // Calculate direction from target with angle offset
      double targetX =
        dirToTarget.x * Math.cos(angleOffset) -
        dirToTarget.z * Math.sin(angleOffset);
      double targetZ =
        dirToTarget.x * Math.sin(angleOffset) +
        dirToTarget.z * Math.cos(angleOffset);
      Vec3 spreadDir = new Vec3(targetX, dirToTarget.y, targetZ).normalize();

      Vec3 optimalPos = target
        .position()
        .subtract(spreadDir.scale(1.8))
        .add(0, 0.5, 0);

      this.pedito
        .getMoveControl()
        .setWantedPosition(optimalPos.x, optimalPos.y, optimalPos.z, 1.8D);

      // Face away from target (180 opposite to look)
      // The look control usually faces the target automatically.
      // We can override yRot in the actual firing phase, or here if we want them to turn around while getting ready.

      this.attackTimer--;
      return;
    } else {
      this.pedito.getNavigation().stop();
    }

    this.attackTimer--;

    if (this.pedito.level() instanceof ServerLevel serverLevel) {
      if (this.attackTimer > 0) {
        // Suck in particles
        Vec3 peditoPos = this.pedito
          .position()
          .add(0, this.pedito.getBbHeight() / 2.0, 0);
        Vec3 offset = new Vec3(
          (this.pedito.getRandom().nextDouble() - 0.5) * 4.0,
          (this.pedito.getRandom().nextDouble() - 0.5) * 4.0,
          (this.pedito.getRandom().nextDouble() - 0.5) * 4.0
        );
        Vec3 spawnPos = peditoPos.add(offset);
        Vec3 velocity = peditoPos.subtract(spawnPos).scale(0.1);

        serverLevel.sendParticles(
          ParticleTypes.CLOUD,
          spawnPos.x,
          spawnPos.y,
          spawnPos.z,
          1,
          velocity.x,
          velocity.y,
          velocity.z,
          0.0
        );
      } else if (this.attackTimer == 0) {
        // Fire custom Fart Wave (instead of Warden Sonic Boom)
        this.pedito.playSound(ModSounds.PEDITO_SONIC_BOOM, 0.7F, 1.0F);

        Vec3 startPos = this.pedito
          .position()
          .add(0, this.pedito.getBbHeight() / 2.0, 0);
        Vec3 targetPos = target
          .position()
          .add(0, target.getBbHeight() / 2.0, 0);
        Vec3 dir = targetPos.subtract(startPos).normalize();

        for (int i = 0; i < 20; i += 2) {
          Vec3 particlePos = startPos.add(dir.scale(i));
          // Create a ring perpendicular to the direction
          for (int j = 0; j < 8; j += 2) {
            double angle = j * (Math.PI / 4);
            // Approximate a cross vector
            Vec3 up = new Vec3(0, 1, 0);
            if (Math.abs(dir.y) > 0.9) up = new Vec3(1, 0, 0);
            Vec3 right = dir.cross(up).normalize();
            Vec3 actualUp = right.cross(dir).normalize();

            double radius = 0.5 + i * 0.1; // Wave gets wider
            Vec3 ringPos = particlePos
              .add(right.scale(Math.cos(angle) * radius))
              .add(actualUp.scale(Math.sin(angle) * radius));
            serverLevel.sendParticles(
              ParticleTypes.CAMPFIRE_COSY_SMOKE,
              ringPos.x,
              ringPos.y,
              ringPos.z,
              1,
              0,
              0,
              0,
              0
            );
            serverLevel.sendParticles(
              ParticleTypes.CLOUD,
              ringPos.x,
              ringPos.y,
              ringPos.z,
              1,
              0,
              0,
              0,
              0
            );
          }
        }

        // Only leader deals damage to prevent insta-kill overlap, or reduce damage if multiple hit
        if (myIndex == 0 || this.pedito.getRandom().nextBoolean()) {
          target.hurtServer(
            serverLevel,
            this.pedito.level().damageSources().mobAttack(this.pedito),
            8.0F
          );
          target.setDeltaMovement(
            target.getDeltaMovement().add(dir.scale(1.5).add(0, 0.5, 0))
          ); // Blast into air
        }

        this.nextAttackTick =
          this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 123 : 140); // 7 seconds cooldown
      }
    }
  }

  @Override
  public boolean canContinueToUse() {
    return this.attackTimer > 0;
  }
}
