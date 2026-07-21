package net.pedito.mod.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.pedito.mod.registry.ModSounds;

/**
 * AI Goal: Concentración del Alfa (Alpha Concentration Healing Pulse)
 * El Alpha se queda quieto durante 5 segundos (100 ticks) curando a todos los
 * peditos del mismo dueño en un rango de 40 bloques, exponiéndose a recibir daño.
 */
public class PeditoAlphaConcentrationGoal extends Goal {

  private final PeditoEntity pedito;
  private int channelTicks;
  private static final Map<UUID, Long> alphaCooldowns =
    new ConcurrentHashMap<>();

  public PeditoAlphaConcentrationGoal(PeditoEntity pedito) {
    this.pedito = pedito;
    // Se establecen flags para interrumpir movimiento, mirar otros objetivos, saltar o atacar
    this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
  }

  @Override
  public boolean canUse() {
    // Solo la variante Alpha domesticada puede usar esta habilidad
    if (
      !this.pedito.isTamedByOwner() ||
      this.pedito.getVariant() != PeditoEntity.VARIANT_ALPHA
    ) {
      return false;
    }

    Player owner = this.pedito.getOwnerCustom();
    if (owner == null || !owner.isAlive()) {
      return false;
    }

    // Comprobación de Cooldown (40 segundos = 800 ticks)
    Long cd = alphaCooldowns.get(this.pedito.getUUID());
    if (cd != null && this.pedito.tickCount < cd) {
      return false;
    }

    // Condición: El dueño tiene menos del 60% de salud, o al menos 2 aliados en 16 bloques tienen menos del 70% de salud
    boolean ownerNeedsHealing = owner.getHealth() < owner.getMaxHealth() * 0.6F;

    List<PeditoEntity> allies = this.pedito
      .level()
      .getEntitiesOfClass(
        PeditoEntity.class,
        this.pedito.getBoundingBox().inflate(16.0D),
        e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
      );

    long lowHealthAllies = allies
      .stream()
      .filter(e -> e.getHealth() < e.getMaxHealth() * 0.7F)
      .count();

    return ownerNeedsHealing || lowHealthAllies >= 2;
  }

  @Override
  public boolean canContinueToUse() {
    return this.channelTicks < 100 && this.pedito.isAlive();
  }

  @Override
  public void start() {
    this.channelTicks = 0;
    this.pedito.getNavigation().stop();
    this.pedito.setTarget(null); // Detener cualquier agresión

    // Registrar Cooldown (40 segundos)
    Player owner = this.pedito.getOwnerCustom();
    if (owner != null) {
      alphaCooldowns.put(
        this.pedito.getUUID(),
        (long) (this.pedito.tickCount + 800)
      );
    }

    // Sonido inicial de concentración profunda (pitch grave de 0.5F)
    this.pedito
      .level()
      .playSound(
        null,
        this.pedito.getX(),
        this.pedito.getY(),
        this.pedito.getZ(),
        ModSounds.PEDITO_VOICE_SWAN,
        net.minecraft.sounds.SoundSource.NEUTRAL,
        1.5F,
        0.5F
      );
  }

  @Override
  public void tick() {
    // Forzar al Alpha a quedarse inmóvil y cancelar la navegación
    this.pedito.getNavigation().stop();
    this.pedito
      .getMoveControl()
      .setWantedPosition(
        this.pedito.getX(),
        this.pedito.getY(),
        this.pedito.getZ(),
        0.0D
      );

    // Activar boca sorprendida/hablando en cada tick de concentración
    this.pedito.triggerTalkAnimation();

    this.channelTicks++;

    // Emitir un pulso curativo cada 20 ticks (1.0 segundo)
    if (this.channelTicks % 20 == 0) {
      Player owner = this.pedito.getOwnerCustom();
      if (owner != null) {
        // VFX: Ondas de partículas en el Alpha (Círculo expansivo)
        if (this.pedito.level() instanceof ServerLevel serverLevel) {
          for (int angleDeg = 0; angleDeg < 360; angleDeg += 15) {
            double rad = Math.toRadians(angleDeg);
            double vx = Math.cos(rad) * 0.4D;
            double vz = Math.sin(rad) * 0.4D;
            // Partículas de happy villager y polvo dorado (0xFFD700)
            serverLevel.sendParticles(
              ParticleTypes.HAPPY_VILLAGER,
              this.pedito.getX(),
              this.pedito.getY() + 0.5,
              this.pedito.getZ(),
              1,
              vx,
              0.0,
              vz,
              0.2
            );
            serverLevel.sendParticles(
              new DustParticleOptions(0xFFD700, 1.2F),
              this.pedito.getX(),
              this.pedito.getY() + 0.5,
              this.pedito.getZ(),
              1,
              vx,
              0.0,
              vz,
              0.2
            );
          }
        }

        // Sonido de pulso (Spray con tono grave de 0.7F)
        this.pedito
          .level()
          .playSound(
            null,
            this.pedito.getX(),
            this.pedito.getY(),
            this.pedito.getZ(),
            ModSounds.PEDITO_SPRAY,
            net.minecraft.sounds.SoundSource.NEUTRAL,
            1.0F,
            0.7F
          );

        // Sanación al Dueño en un rango de 40 bloques
        if (owner.isAlive() && owner.distanceToSqr(this.pedito) <= 1600.0D) {
          owner.heal(2.0F); // Cura 2 HP (1 corazón)
          if (this.pedito.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.HEART,
              owner.getX(),
              owner.getY() + 1.2,
              owner.getZ(),
              3,
              0.3,
              0.3,
              0.3,
              0.02
            );
            serverLevel.sendParticles(
              ParticleTypes.GLOW,
              owner.getX(),
              owner.getY() + 1.2,
              owner.getZ(),
              5,
              0.3,
              0.3,
              0.3,
              0.02
            );
          }
        }

        // Sanación a todos los aliados en un rango de 40 bloques (1600.0D d^2)
        List<PeditoEntity> healTargets = this.pedito
          .level()
          .getEntitiesOfClass(
            PeditoEntity.class,
            this.pedito.getBoundingBox().inflate(40.0D),
            e ->
              e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
          );

        for (PeditoEntity ally : healTargets) {
          ally.heal(2.0F); // Cura 2 HP (1 corazón)
          if (this.pedito.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
              ParticleTypes.HEART,
              ally.getX(),
              ally.getY() + 0.4,
              ally.getZ(),
              1,
              0.1,
              0.1,
              0.1,
              0.02
            );
            serverLevel.sendParticles(
              ParticleTypes.GLOW,
              ally.getX(),
              ally.getY() + 0.4,
              ally.getZ(),
              3,
              0.1,
              0.1,
              0.1,
              0.02
            );
          }
        }
      }
    }
  }

  @Override
  public void stop() {
    // Sonido final satisfactorio de descompresión (pitch grave de 0.6F)
    if (this.channelTicks >= 100 && this.pedito.isAlive()) {
      this.pedito
        .level()
        .playSound(
          null,
          this.pedito.getX(),
          this.pedito.getY(),
          this.pedito.getZ(),
          ModSounds.PEDITO_VOICE_PUPU,
          net.minecraft.sounds.SoundSource.NEUTRAL,
          1.0F,
          0.6F
        );
    }
    this.pedito.getNavigation().stop();
  }
}
