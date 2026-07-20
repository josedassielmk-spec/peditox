package net.pedito.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.pedito.mod.registry.ModSounds;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BiogasProtectionAuraGoal extends Goal {
    private final PeditoEntity pedito;
    private int phase; // 0=return, 1=active, 2=fade
    private int timer;
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public BiogasProtectionAuraGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        PeditoEntity.SquadRole role = this.pedito.getSquadRole();
        if (role != PeditoEntity.SquadRole.ROYAL_GUARD && role != PeditoEntity.SquadRole.SOLO) {
            return false;
        }
    
        if (!this.pedito.isTamedByOwner()) return false;
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null || !owner.isAlive()) return false;

        Long cd = playerCooldowns.get(owner.getUUID());
        if (cd != null && this.pedito.tickCount < cd) return false;

        if (owner.getHealth() < owner.getMaxHealth() * 0.3f) {
            List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                    PeditoEntity.class,
                    owner.getBoundingBox().inflate(16.0D),
                    e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
            );
            if (allies.size() >= 2) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase < 3;
    }

    @Override
    public void start() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner != null) {
            playerCooldowns.put(owner.getUUID(), this.pedito.tickCount + 800L);
        }
        this.phase = 0;
        this.timer = 10;
        this.pedito.playSound(ModSounds.PEDITO_VOICE_PEDI, 0.8F, 1.25F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
        this.pedito.setTarget(null); // Cancel attack
    }

    @Override
    public void tick() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) {
            this.phase = 3;
            return;
        }

        if (this.phase == 0) {
            // Fase de retorno
            this.pedito.getNavigation().moveTo(owner, 2.5D);
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 1;
                this.timer = 120;
            }
        } else if (this.phase == 1) {
            // Fase activa
            this.timer--;
            
            List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                    PeditoEntity.class,
                    owner.getBoundingBox().inflate(16.0D),
                    e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner 
            );
            allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
            int total = allies.size();
            int myIndex = allies.indexOf(this.pedito);
            if (myIndex == -1) myIndex = 0;

            double time = this.pedito.tickCount * 0.05;
            double omega = 6.28; // rad/s
            double t = this.pedito.tickCount / 20.0;
            
            double radius = 1.2;
            double angle = omega * t + (2 * Math.PI * myIndex) / Math.max(1, total);
            
            double x = owner.getX() + radius * Math.cos(angle);
            double z = owner.getZ() + radius * Math.sin(angle);
            double y = owner.getY() + 1.0 + 0.1 * Math.sin(omega * t * 2.0);
            
            this.pedito.getMoveControl().setWantedPosition(x, y, z, 2.5D);
            this.pedito.getLookControl().setLookAt(owner, 10.0F, 10.0F);

            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Solo el primer pedito dibuja el escudo y hace la logica centralizada
                if (myIndex == 0) {
                    owner.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20, 2, false, false));
                    owner.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 0, false, false));

                    if (this.pedito.tickCount % 20 == 0) {
                        this.pedito.playSound(ModSounds.PEDITO_SPRAY, 1.0F, 0.90F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.02F);
                    }

                    // Dome particles
                    for (int i = 0; i < 120; i++) {
                        double phi = Math.acos(1.0 - 2.0 * this.pedito.getRandom().nextDouble());
                        double theta = 2.0 * Math.PI * this.pedito.getRandom().nextDouble();
                        double px = owner.getX() + 2.0 * Math.sin(phi) * Math.cos(theta);
                        double py = owner.getY() + 1.0 + 2.0 * Math.cos(phi);
                        double pz = owner.getZ() + 2.0 * Math.sin(phi) * Math.sin(theta);
                        
                        if (this.pedito.getRandom().nextBoolean()) {
                            serverLevel.sendParticles(new DustParticleOptions(0x19CC19, 0.6F), px, py, pz, 1, 0, 0, 0, 0);
                        } else {
                            serverLevel.sendParticles(ParticleTypes.GLOW, px, py, pz, 1, 0, 0, 0, 0);
                        }
                    }

                    // Shockwave on collision
                    AABB domeBox = owner.getBoundingBox().inflate(2.0D);
                    List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(LivingEntity.class, domeBox, e -> e.isAlive() && e != owner && !(e instanceof PeditoEntity));
                    for (LivingEntity enemy : enemies) {
                        double dx = enemy.getX() - owner.getX();
                        double dz = enemy.getZ() - owner.getZ();
                        enemy.setDeltaMovement(enemy.getDeltaMovement().add(dx * 0.5, 0.5, dz * 0.5));
                        enemy.hurt(serverLevel.damageSources().magic(), 2.0F);
                        serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, enemy.getX(), enemy.getY(), enemy.getZ(), 1, 0, 0, 0, 0);
                        this.pedito.playSound(ModSounds.PEDITO_FART, 1.1F, 1.10F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
                    }
                }
            }

            if (this.timer <= 0) {
                this.phase = 2;
                this.timer = 10;
            }
        } else if (this.phase == 2) {
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 3;
            }
        }
    }
}
