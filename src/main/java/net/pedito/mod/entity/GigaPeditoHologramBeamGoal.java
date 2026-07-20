package net.pedito.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class GigaPeditoHologramBeamGoal extends Goal {
    private final PeditoEntity pedito;
    private int phase; 
    private int timer;
    private long nextAttackTick;
    private double targetX, targetY, targetZ;
    private double centerX, centerY, centerZ;

    public GigaPeditoHologramBeamGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        PeditoEntity.SquadRole role = this.pedito.getSquadRole();
        if (role != PeditoEntity.SquadRole.ROYAL_GUARD && role != PeditoEntity.SquadRole.SOLO) {
            return false;
        }
    
        if (this.pedito.tickCount < this.nextAttackTick) return false;
        if (!this.pedito.isTamedByOwner()) return false;

        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive() || target.getMaxHealth() <= 100) return false;

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                this.pedito.getBoundingBox().inflate(24.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom()
        );

        return allies.size() >= 10;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase < 4 && this.pedito.getTarget() != null && this.pedito.getTarget().isAlive();
    }

    @Override
    public void start() {
        this.phase = 0;
        this.timer = 30; // Phase 1: 30 ticks
        this.pedito.playSound(ModSounds.PEDITO_SUMMON, 1.0F, 0.75F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.02F);
    }

    @Override
    public void tick() {
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive()) {
            this.phase = 4;
            return;
        }

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                target.getBoundingBox().inflate(32.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom() 
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;
        int totalInCircle = Math.min(allies.size(), 10);
        
        if (myIndex < 10) {
            // Form a 6.0 diameter circle (radius 3.0), 12.0 blocks above target
            this.centerX = target.getX();
            this.centerY = target.getY() + target.getBbHeight() + 12.0;
            this.centerZ = target.getZ();
            
            double angle = (2 * Math.PI * myIndex) / totalInCircle;
            this.targetX = this.centerX + 3.0 * Math.cos(angle);
            this.targetY = this.centerY;
            this.targetZ = this.centerZ + 3.0 * Math.sin(angle);
            
            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.targetY, this.targetZ, 2.5D);
            this.pedito.getLookControl().setLookAt(this.centerX, this.centerY, this.centerZ, 30.0F, 30.0F);

            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Gold lines to center
                serverLevel.sendParticles(new DustParticleOptions(0xFFD800, 0.4F), 
                        this.pedito.getX(), this.pedito.getY(), this.pedito.getZ(), 1, (this.centerX - this.pedito.getX())*0.5, (this.centerY - this.pedito.getY())*0.5, (this.centerZ - this.pedito.getZ())*0.5, 0.5);

                // Only leader does the big stuff
                if (myIndex == 0) {
                    // Hologram head (abstract representation with particles)
                    for (int i = 0; i < 20; i++) {
                        double px = this.centerX + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                        double py = this.centerY + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                        double pz = this.centerZ + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                        serverLevel.sendParticles(ParticleTypes.WITCH, px, py, pz, 1, 0, 0, 0, 0);
                        serverLevel.sendParticles(ParticleTypes.GLOW, px, py, pz, 1, 0, 0, 0, 0);
                    }
                    
                    if (this.phase == 1) { // Phase 2: Beam
                        if (this.timer == 80) {
                            this.pedito.playSound(ModSounds.PEDITO_LASER, 1.5F, 0.85F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.02F);
                        }
                        
                        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 3, false, false));
                        if (this.timer % 4 == 0) {
                            target.hurt(serverLevel.damageSources().magic(), 3.5F);
                        }

                        // Beam particles
                        for (double y = target.getY(); y < this.centerY; y += 0.5) {
                            serverLevel.sendParticles(ParticleTypes.WITCH, this.centerX, y, this.centerZ, 2, 0.9, 0, 0.9, 0);
                            serverLevel.sendParticles(new DustParticleOptions(this.pedito.getRandom().nextInt(0xFFFFFF), 1.0F), this.centerX, y, this.centerZ, 1, 0.5, 0, 0.5, 0);
                        }
                        // Impact particles
                        serverLevel.sendParticles(ParticleTypes.GLOW_SQUID_INK, target.getX(), target.getY(), target.getZ(), 10, 1.5, 0.5, 1.5, 0.1);
                    }
                }
            }
        }

        if (this.phase == 0) {
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 1;
                this.timer = 80;
            }
        } else if (this.phase == 1) {
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 2;
                this.timer = 25;
                if (myIndex == 0) {
                    this.pedito.playSound(ModSounds.PEDITO_FART_SPAWN, 1.0F, 1.00F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
                }
            }
        } else if (this.phase == 2) {
            if (this.pedito.level() instanceof ServerLevel serverLevel && myIndex == 0) {
                serverLevel.sendParticles(ParticleTypes.FIREWORK, this.centerX, this.centerY, this.centerZ, 30, 2, 2, 2, 0.1);
            }
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 4;
                this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 1056 : 1200);
            }
        }
    }
}
