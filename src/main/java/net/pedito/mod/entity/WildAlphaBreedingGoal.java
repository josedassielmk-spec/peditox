package net.pedito.mod.entity;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.pedito.mod.Pedito;
import net.pedito.mod.registry.ModSounds;

/**
 * Inteligencia Artificial autónoma para el Pedito Alpha Salvaje.
 * Le permite buscar un Pedito verde adulto salvaje cercano, acoplarse con él,
 * y procrear nuevos integrantes para generar o expandir la manada.
 */
public class WildAlphaBreedingGoal extends Goal {

  private final PeditoEntity alpha;
  private PeditoEntity partner;
  private int breedingTimer;

  public WildAlphaBreedingGoal(PeditoEntity alpha) {
    this.alpha = alpha;
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
  }

  @Override
  public boolean canUse() {
    if (this.alpha.isTamedByOwner()) return false;
    if (this.alpha.isBaby()) return false;
    if (
      this.alpha.getVariant() != PeditoEntity.VARIANT_ALPHA &&
      this.alpha.getVariant() != PeditoEntity.VARIANT_GOLDEN
    ) return false;
    if (this.alpha.getTarget() != null) return false;
    if (this.alpha.wildAlphaBreedCooldown > 0) return false;

    // Frecuencia de chequeo aleatoria para optimizar rendimiento
    if (this.alpha.getRandom().nextInt(150) != 0) return false;

    List<PeditoEntity> targets = this.alpha
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        this.alpha.getBoundingBox().inflate(16.0D),
        e ->
          e.isAlive() &&
          !e.isBaby() &&
          !e.isTamedByOwner() &&
          e.getVariant() == PeditoEntity.VARIANT_NORMAL &&
          e != this.alpha
      );

    if (targets.isEmpty()) return false;

    this.partner = targets.get(this.alpha.getRandom().nextInt(targets.size()));
    return true;
  }

  @Override
  public boolean canContinueToUse() {
    if (
      this.alpha.isTamedByOwner() || this.alpha.getTarget() != null
    ) return false;
    if (
      this.partner == null ||
      !this.partner.isAlive() ||
      this.partner.isTamedByOwner()
    ) return false;
    return (
      this.breedingTimer > 0 && this.alpha.distanceToSqr(this.partner) < 256.0D
    );
  }

  @Override
  public void start() {
    this.breedingTimer = 160; // 8 segundos de acoplamiento
  }

  @Override
  public void stop() {
    this.partner = null;
  }

  @Override
  public void tick() {
    this.breedingTimer--;

    this.alpha.getLookControl().setLookAt(this.partner, 10.0F, 10.0F);
    this.partner.getLookControl().setLookAt(this.alpha, 10.0F, 10.0F);

    double distSqr = this.alpha.distanceToSqr(this.partner);
    if (distSqr > 2.25D) {
      // Aproximadamente 1.5 bloques
      this.alpha.getNavigation().moveTo(this.partner, 1.2D);
      this.partner.getNavigation().moveTo(this.alpha, 1.0D);
    } else {
      this.alpha.getNavigation().stop();
      this.partner.getNavigation().stop();
    }

    if (
      distSqr <= 3.0D && this.alpha.level() instanceof ServerLevel serverLevel
    ) {
      // Efecto visual romántico y alegre
      if (this.breedingTimer % 10 == 0) {
        serverLevel.sendParticles(
          ParticleTypes.HEART,
          this.alpha.getX(),
          this.alpha.getY() + 0.6,
          this.alpha.getZ(),
          1,
          0.2,
          0.2,
          0.2,
          0.0
        );
        serverLevel.sendParticles(
          ParticleTypes.HAPPY_VILLAGER,
          this.partner.getX(),
          this.partner.getY() + 0.5,
          this.partner.getZ(),
          1,
          0.2,
          0.2,
          0.2,
          0.0
        );
      }
      // Efectos de voz sincronizados
      if (
        this.breedingTimer % 40 == 0 &&
        this.alpha.getRandom().nextInt(2) == 0 &&
        this.alpha.canPlaySound(40)
      ) {
        this.alpha.playSound(ModSounds.PEDITO_VOICE_PEDI, 0.8F, 0.5F); // Alpha: grave
        this.partner.playSound(ModSounds.PEDITO_VOICE_PUPULLITO, 0.8F, 1.2F); // Normal: alegre/agudo
      }
    }

    if (this.breedingTimer == 0) {
      spawnNewGroup();
    }
  }

  private void spawnNewGroup() {
    if (!(this.alpha.level() instanceof ServerLevel serverLevel)) return;

    // Generar entre 1 y 3 nuevos integrantes
    int spawnCount = 1 + this.alpha.getRandom().nextInt(3);
    for (int i = 0; i < spawnCount; i++) {
      PeditoEntity offspring = new PeditoEntity(
        Pedito.PEDITO_ENTITY,
        serverLevel
      );
      double rx =
        this.alpha.getX() + (this.alpha.getRandom().nextDouble() - 0.5) * 2.0;
      double ry =
        this.alpha.getY() + (this.alpha.getRandom().nextDouble() - 0.5) * 0.5;
      double rz =
        this.alpha.getZ() + (this.alpha.getRandom().nextDouble() - 0.5) * 2.0;
      offspring.setPos(rx, ry, rz);
      offspring.setYRot(this.alpha.getYRot());
      offspring.setXRot(this.alpha.getXRot());

      // 40% de ser bebé, 60% de ser adulto normal (o raro nocturno si es de noche/suerte)
      boolean isBaby = this.alpha.getRandom().nextInt(10) < 4;
      offspring.setBaby(isBaby);

      if (!isBaby) {
        int roll = this.alpha.getRandom().nextInt(100);
        if (roll < 80) {
          offspring.setVariant(PeditoEntity.VARIANT_NORMAL);
        } else {
          offspring.setVariant(PeditoEntity.VARIANT_NIGHT);
        }
      } else {
        offspring.setVariant(PeditoEntity.VARIANT_NORMAL);
      }

      offspring.setTier(0);
      serverLevel.addFreshEntity(offspring);
    }

    // Efectos finales de la reproducción de la manada
    serverLevel.playSound(
      null,
      this.alpha.getX(),
      this.alpha.getY(),
      this.alpha.getZ(),
      ModSounds.PEDITO_FART_SPAWN,
      SoundSource.NEUTRAL,
      1.5F,
      1.1F
    );
    serverLevel.sendParticles(
      ParticleTypes.CLOUD,
      this.alpha.getX(),
      this.alpha.getY() + 0.5,
      this.alpha.getZ(),
      15,
      0.5,
      0.3,
      0.5,
      0.05
    );

    // Cooldowns de reproducción
    this.alpha.wildAlphaBreedCooldown = 12000; // 10 minutos
    this.partner.wildAlphaBreedCooldown = 6000; // 5 minutos
  }
}
