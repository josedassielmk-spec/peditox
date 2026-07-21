package net.pedito.mod.entity;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

public class PeditoAttackGoal extends Goal {

  private final PeditoEntity pedito;
  private int attackCooldown;
  private int orbitDirection = 1;

  public PeditoAttackGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    LivingEntity target = this.pedito.getTarget();
    return target != null && target.isAlive();
  }

  @Override
  public boolean canContinueToUse() {
    return this.pedito.getTarget() != null && this.pedito.getTarget().isAlive();
  }

  @Override
  public void start() {
    this.attackCooldown = 0;
    this.orbitDirection = this.pedito.getRandom().nextBoolean() ? 1 : -1;
  }

  @Override
  public void tick() {
    LivingEntity target = this.pedito.getTarget();
    if (target == null) return;

    if (this.pedito.getTarget() != target) {
      this.pedito.setTarget(target);
    }

    this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);

    List<PeditoEntity> allies = this.pedito
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        target.getBoundingBox().inflate(40.0D),
        e ->
          e.isAlive() &&
          (e.getTarget() == target ||
            (e.isTamedByOwner() &&
              e.getOwnerCustom() == this.pedito.getOwnerCustom()))
      );

    PeditoEntity.SquadRole myRole = this.pedito.getSquadRole();

    final boolean isNight = this.pedito.level().isDarkOutside();

    // Find peers in the same role to calculate formation geometry
    List<PeditoEntity> peers = allies
      .stream()
      .filter(e -> e.getSquadRole() == myRole)
      .sorted((p1, p2) -> {
        // Alpha is ALWAYS first in Vanguard
        if (myRole == PeditoEntity.SquadRole.VANGUARD) {
          if (
            p1.getVariant() == PeditoEntity.VARIANT_ALPHA &&
            p2.getVariant() != PeditoEntity.VARIANT_ALPHA
          ) return -1;
          if (
            p2.getVariant() == PeditoEntity.VARIANT_ALPHA &&
            p1.getVariant() != PeditoEntity.VARIANT_ALPHA
          ) return 1;
        }
        // Golden is ALWAYS first in Tactical
        if (myRole == PeditoEntity.SquadRole.TACTICAL) {
          if (
            p1.getVariant() == PeditoEntity.VARIANT_GOLDEN &&
            p2.getVariant() != PeditoEntity.VARIANT_GOLDEN
          ) return -1;
          if (
            p2.getVariant() == PeditoEntity.VARIANT_GOLDEN &&
            p1.getVariant() != PeditoEntity.VARIANT_GOLDEN
          ) return 1;
        }

        // Nocturnal adjustment:
        // At night, Nocturnals get a massive score boost (move to inner edge / front).
        // At day, they get a massive penalty (move to outer edge).
        long score1 = p1.getTier() * 1000L + (long) (p1.getHealth() * 10);
        long score2 = p2.getTier() * 1000L + (long) (p2.getHealth() * 10);

        if (p1.getVariant() == PeditoEntity.VARIANT_NIGHT) {
          score1 += isNight ? 1000000L : -1000000L;
        }
        if (p2.getVariant() == PeditoEntity.VARIANT_NIGHT) {
          score2 += isNight ? 1000000L : -1000000L;
        }

        if (score1 != score2) return Long.compare(score2, score1);
        return Integer.compare(p1.getId(), p2.getId());
      })
      .collect(Collectors.toList());

    int roleIndex = peers.indexOf(this.pedito);
    if (roleIndex == -1) roleIndex = 0;
    int roleTotal = Math.max(1, peers.size());
    int myIndex = allies.indexOf(this.pedito);

    double time = this.pedito.tickCount * 0.05;
    double targetX = target.getX();
    double targetY = target.getY() + target.getBbHeight() / 2.0;
    double targetZ = target.getZ();

    double wantedX = targetX;
    double wantedY = targetY;
    double wantedZ = targetZ;

    net.minecraft.world.entity.player.Player owner =
      this.pedito.getOwnerCustom();
    double baseAngle = 0;
    if (owner != null) {
      baseAngle = Math.atan2(
        owner.getZ() - target.getZ(),
        owner.getX() - target.getX()
      );
    }

    if (
      myRole == PeditoEntity.SquadRole.SOLO ||
      myRole == PeditoEntity.SquadRole.VANGUARD
    ) {
      // For Vanguard, roleIndex 0 (Alpha if present) is at angle 0 (relative to baseAngle, between target and owner)
      double radius = 2.5 + (roleIndex / (double) roleTotal) * 1.5; // Spread out radius slightly so inner/outer edges exist
      double spread = Math.PI; // Semicircle
      double fraction =
        roleTotal == 1 ? 0.5 : (double) roleIndex / (roleTotal - 1);
      // Alpha (index 0) gets fraction 0. We want alpha at exactly baseAngle.
      // Let's do alternating angles around the baseAngle
      double angleOffset =
        (roleIndex % 2 == 0 ? 1 : -1) *
        ((roleIndex + 1) / 2) *
        ((Math.PI * 2) / roleTotal);
      if (this.pedito.getVariant() == PeditoEntity.VARIANT_ALPHA) {
        angleOffset = 0; // Alpha directly in front
      }
      double angle = baseAngle + angleOffset + time * this.orbitDirection * 0.5;

      wantedX = targetX + Math.cos(angle) * radius;
      wantedZ = targetZ + Math.sin(angle) * radius;
      wantedY = targetY + 1.0 + Math.sin(time * 2.0 + myIndex) * 0.5;
    } else if (myRole == PeditoEntity.SquadRole.TACTICAL) {
      double radius = 6.0 + (roleIndex / (double) roleTotal) * 2.0;
      double angleOffset =
        (roleIndex % 2 == 0 ? 1 : -1) *
        ((roleIndex + 1) / 2) *
        ((Math.PI * 2) / roleTotal);
      if (this.pedito.getVariant() == PeditoEntity.VARIANT_GOLDEN) {
        angleOffset = Math.PI; // Golden at the back of the tactical ring (closest to center of mass of the horde, away from target)
      }
      double angle = baseAngle + angleOffset - time * this.orbitDirection * 0.5;
      wantedX = targetX + Math.cos(angle) * radius;
      wantedZ = targetZ + Math.sin(angle) * radius;
      wantedY = targetY + 3.0 + Math.sin(time * 2.0 + myIndex) * 1.0;
    } else if (myRole == PeditoEntity.SquadRole.ARTILLERY) {
      double radius = 14.0 + (roleIndex / (double) roleTotal) * 3.0;
      double angle =
        baseAngle +
        (Math.PI * 2 * roleIndex) / roleTotal +
        time * this.orbitDirection * 0.2;
      wantedX = targetX + Math.cos(angle) * radius;
      wantedZ = targetZ + Math.sin(angle) * radius;
      wantedY = targetY + 6.0 + Math.sin(time + myIndex) * 2.0;
    } else if (myRole == PeditoEntity.SquadRole.ROYAL_GUARD) {
      if (owner != null) {
        double radius = 2.0;
        double angle =
          baseAngle + (Math.PI * 2 * roleIndex) / roleTotal + time * 2.0;
        wantedX = owner.getX() + Math.cos(angle) * radius;
        wantedZ = owner.getZ() + Math.sin(angle) * radius;
        wantedY = owner.getY() + 1.0;
      }
    }

    double currentDistSq = this.pedito.distanceToSqr(wantedX, wantedY, wantedZ);
    if (currentDistSq > 1.0D) {
      this.pedito
        .getMoveControl()
        .setWantedPosition(wantedX, wantedY, wantedZ, 1.8D);
    } else {
      this.pedito.setDeltaMovement(
        this.pedito.getDeltaMovement().multiply(0.5, 0.5, 0.5)
      );
    }

    this.attackCooldown = Math.max(this.attackCooldown - 1, 0);
    double distToTargetSq = this.pedito.distanceToSqr(target);

    if (this.attackCooldown == 0 && this.pedito.hasLineOfSight(target)) {
      boolean canAttack = false;
      int nextCooldown = 20;

      if (
        (myRole == PeditoEntity.SquadRole.VANGUARD ||
          myRole == PeditoEntity.SquadRole.SOLO) &&
        distToTargetSq <= 16.0D
      ) {
        canAttack = true;
        nextCooldown = 15;
      } else if (
        myRole == PeditoEntity.SquadRole.TACTICAL && distToTargetSq <= 64.0D
      ) {
        canAttack = true;
        nextCooldown = 30;
      } else if (
        myRole == PeditoEntity.SquadRole.ARTILLERY && distToTargetSq <= 400.0D
      ) {
        canAttack = true;
        nextCooldown = 60;
      } else if (
        myRole == PeditoEntity.SquadRole.ROYAL_GUARD && distToTargetSq <= 36.0D
      ) {
        canAttack = true;
        nextCooldown = 10;
      }

      if (
        canAttack &&
        (this.pedito.getRandom().nextInt(Math.max(1, roleTotal)) == 0 ||
          roleTotal < 3)
      ) {
        this.performAttack(target, myRole);
        this.attackCooldown = nextCooldown;
      }
    }
  }

  private void performAttack(LivingEntity target, PeditoEntity.SquadRole role) {
    Level level = this.pedito.level();
    if (level instanceof ServerLevel serverLevel) {
      int tier = this.pedito.getTier();
      int color = 0x32CD32; // Default Green
      float damage = 1.0F;

      switch (tier) {
        case 1:
          color = 0xCC7722;
          damage = 1.5F;
          break; // Copper
        case 2:
          color = 0xAAAAAA;
          damage = 2.0F;
          break; // Iron
        case 3:
          color = 0xFFD700;
          damage = 2.5F;
          break; // Gold
        case 4:
          color = 0x00FFFF;
          damage = 4.0F;
          break; // Diamond
        case 5:
          color = 0x4B0082;
          damage = 6.0F;
          break; // Netherite
      }

      if (this.pedito.getVariant() == PeditoEntity.VARIANT_RAINBOW) {
        color = this.pedito.getRandom().nextInt(0xFFFFFF);
        damage += 1.0F;
      } else if (this.pedito.getVariant() == PeditoEntity.VARIANT_NIGHT) {
        color = 0x1A237E;
        damage += 1.0F;
      }

      if (role == PeditoEntity.SquadRole.ARTILLERY) {
        damage *= 2.0F;
      } else if (role == PeditoEntity.SquadRole.TACTICAL) {
        damage *= 1.2F;
      }
      if (this.pedito.hasNetheriteLeader()) {
        damage *= 1.15F; // +15% damage from Netherite Leader passive
      }

      if (this.pedito.canPlaySound(20)) {
        if (
          role == PeditoEntity.SquadRole.TACTICAL ||
          role == PeditoEntity.SquadRole.ARTILLERY
        ) {
          Vec3 start = this.pedito
            .position()
            .add(0, this.pedito.getBbHeight() / 2, 0);
          Vec3 end = target.position().add(0, target.getBbHeight() / 2, 0);
          Vec3 dir = end.subtract(start).normalize();
          double dist = start.distanceTo(end);
          for (double d = 0; d < dist; d += 0.5) {
            Vec3 p = start.add(dir.scale(d));
            serverLevel.sendParticles(
              new DustParticleOptions(color, 0.8F),
              p.x,
              p.y,
              p.z,
              1,
              0,
              0,
              0,
              0
            );
          }
          serverLevel.playSound(
            null,
            this.pedito.getX(),
            this.pedito.getY(),
            this.pedito.getZ(),
            ModSounds.PEDITO_LASER,
            SoundSource.NEUTRAL,
            0.8F,
            1.2F
          );
        } else {
          serverLevel.sendParticles(
            new DustParticleOptions(color, 1.5F),
            target.getX(),
            target.getY() + target.getBbHeight() / 2.0,
            target.getZ(),
            80,
            0.8,
            0.8,
            0.8,
            0.2
          );
          serverLevel.playSound(
            null,
            this.pedito.getX(),
            this.pedito.getY(),
            this.pedito.getZ(),
            ModSounds.PEDITO_FART,
            SoundSource.NEUTRAL,
            0.8F,
            1.2F
          );
        }

        this.pedito.playAttackVoice();
      }

      // Swarm Damage saturation formula: D_total = D_avg * (1 + ln(1 + beta * (N - 1))) * mu_tier
      // Scale individual hits by (1 + ln(1 + beta * (N - 1))) / N to cap cumulative DPS log-style
      List<PeditoEntity> swarmAllies = this.pedito
        .level()
        .getEntitiesOfClass(
          PeditoEntity.class,
          target.getBoundingBox().inflate(16.0D),
          e ->
            e.isAlive() &&
            (e.getTarget() == target ||
              (e.isTamedByOwner() &&
                e.getOwnerCustom() == this.pedito.getOwnerCustom()))
        );
      int N = Math.max(1, swarmAllies.size());
      double beta = 0.45;
      float swarmMultiplier = (float) ((1.0 + Math.log(1.0 + beta * (N - 1))) /
        N);
      damage *= swarmMultiplier;

      target.hurtServer(
        serverLevel,
        level.damageSources().mobAttack(this.pedito),
        damage
      );
    }
  }
}
