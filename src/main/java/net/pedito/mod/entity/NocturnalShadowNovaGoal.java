package net.pedito.mod.entity;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

public class NocturnalShadowNovaGoal extends Goal {

  private final PeditoEntity pedito;
  private int cooldown = 0;
  private int castTime = 0;

  public NocturnalShadowNovaGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    if (this.pedito.getVariant() != PeditoEntity.VARIANT_NIGHT) return false;
    if (this.pedito.isBaby()) return false;

    if (this.cooldown > 0) {
      this.cooldown--;
      return false;
    }

    LivingEntity target = this.pedito.getTarget();
    if (target == null || !target.isAlive()) return false;

    // Check distance to target
    double distSq = this.pedito.distanceToSqr(target);
    if (distSq > 144.0D) return false; // 12 blocks max to initiate

    // 10% chance per tick to trigger once target is nearby and off cooldown
    return this.pedito.getRandom().nextInt(10) == 0;
  }

  @Override
  public void start() {
    this.castTime = 30; // 1.5 seconds cast
    this.pedito.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 0.8F);
    this.pedito.setSpinningTicks(30);
    this.pedito.getNavigation().stop();
  }

  @Override
  public void tick() {
    LivingEntity target = this.pedito.getTarget();
    if (target != null) {
      this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    if (this.castTime > 0) {
      this.castTime--;

      if (this.pedito.level() instanceof ServerLevel serverLevel) {
        // Draw dark swirling energy particles
        double radius = 1.2;
        double angle = this.castTime * 0.4;
        double x = this.pedito.getX() + Math.cos(angle) * radius;
        double z = this.pedito.getZ() + Math.sin(angle) * radius;
        serverLevel.sendParticles(
          new DustParticleOptions(0x1A237E, 1.2F), // Deep Night Indigo
          x,
          this.pedito.getY() + 0.5,
          z,
          2,
          0.1,
          0.1,
          0.1,
          0
        );
        serverLevel.sendParticles(
          ParticleTypes.PORTAL,
          this.pedito.getX(),
          this.pedito.getY() + 0.3,
          this.pedito.getZ(),
          2,
          0.3,
          0.3,
          0.3,
          0.02
        );
      }

      if (this.castTime <= 0) {
        this.executeShadowNova();
        this.cooldown = 240; // 12 seconds cooldown
      }
    }
  }

  private void executeShadowNova() {
    if (!(this.pedito.level() instanceof ServerLevel serverLevel)) return;

    // Visual explosion of shadow-gas
    serverLevel.sendParticles(
      new DustParticleOptions(0x1A237E, 2.0F),
      this.pedito.getX(),
      this.pedito.getY() + 0.5,
      this.pedito.getZ(),
      120,
      2.5,
      1.0,
      2.5,
      0.1
    );
    serverLevel.sendParticles(
      ParticleTypes.SQUID_INK,
      this.pedito.getX(),
      this.pedito.getY() + 0.5,
      this.pedito.getZ(),
      80,
      2.0,
      1.0,
      2.0,
      0.05
    );
    serverLevel.sendParticles(
      ParticleTypes.WITCH,
      this.pedito.getX(),
      this.pedito.getY() + 0.5,
      this.pedito.getZ(),
      40,
      1.5,
      0.8,
      1.5,
      0.02
    );

    this.pedito.playSound(SoundEvents.WITHER_SHOOT, 1.0F, 0.7F);
    this.pedito.playSound(ModSounds.PEDITO_FART, 1.2F, 0.8F);

    double range = 6.0D;
    AABB area = this.pedito.getBoundingBox().inflate(range);
    List<LivingEntity> entities = this.pedito.level().getEntitiesOfClass(LivingEntity.class, area);

    // Dynamic base damage scaling with tier
    float baseDamage = 12.0F + (this.pedito.getTier() * 2.5F);

    // 50% damage boost during night-time/dark outside
    if (this.pedito.level().isDarkOutside()) {
      baseDamage *= 1.5F;
    }

    for (LivingEntity e : entities) {
      if (e != this.pedito && e.isAlive() && this.pedito.wantsToAttack(e, this.pedito.getOwnerCustom())) {
        // Deal shadow damage
        e.hurt(this.pedito.damageSources().indirectMagic(this.pedito, this.pedito), baseDamage);

        // Apply dark curses (Wither & Blindness)
        e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1)); // Wither II for 5s
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0)); // Blindness for 3s

        // Push back
        Vec3 knockback = e.position().subtract(this.pedito.position()).normalize().scale(1.2);
        e.push(knockback.x, 0.4, knockback.z);
      }
    }
  }

  @Override
  public boolean canContinueToUse() {
    return this.castTime > 0;
  }
}
