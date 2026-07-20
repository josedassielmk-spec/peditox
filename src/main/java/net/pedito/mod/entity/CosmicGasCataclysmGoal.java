package net.pedito.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.pedito.mod.registry.ModSounds;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CosmicGasCataclysmGoal extends Goal {
    private final PeditoEntity pedito;
    private int phase; 
    private int timer;
    private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    private double meteorX, meteorY, meteorZ;
    private double impactX, impactY, impactZ;

    public CosmicGasCataclysmGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
        
        // Auto if health < 15%
        boolean autoTrigger = owner.getHealth() < owner.getMaxHealth() * 0.15f;
        
        if (!autoTrigger) return false;

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                owner.getBoundingBox().inflate(24.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner
        );

        return allies.size() >= 20;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase < 6;
    }

    @Override
    public void start() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner != null) {
            playerCooldowns.put(owner.getUUID(), this.pedito.tickCount + 3600L);
            this.impactX = owner.getX();
            this.impactY = owner.getY();
            this.impactZ = owner.getZ();
        }
        this.phase = 0;
        this.timer = 40; // Phase 1: 40 ticks
        this.pedito.playSound(ModSounds.PEDITO_SUMMON, 1.2F, 0.65F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.02F);
    }

    @Override
    public void tick() {
        Player owner = this.pedito.getOwnerCustom();
        if (owner == null) {
            this.phase = 6;
            return;
        }

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                owner.getBoundingBox().inflate(64.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == owner 
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;
        int totalInDome = Math.min(allies.size(), 20);

        if (myIndex < 20) {
            if (this.phase < 4) { // Only position in dome for first 4 phases
                // Dome positioning (Fibonacci sphere over a hemisphere)
                double i = myIndex + 0.5;
                double phi = Math.acos(1.0 - (i / totalInDome)); // Only top half
                double theta = Math.PI * (1.0 + Math.sqrt(5.0)) * i;
                
                double radius = 15.0;
                double tx = this.impactX + radius * Math.sin(phi) * Math.cos(theta);
                double ty = this.impactY + 20.0 + radius * Math.cos(phi) - 15.0; // adjust to make it a dome over the player
                double tz = this.impactZ + radius * Math.sin(phi) * Math.sin(theta);
                
                this.pedito.getMoveControl().setWantedPosition(tx, ty, tz, 3.0D);
                this.pedito.getLookControl().setLookAt(this.impactX, this.impactY + 30.0, this.impactZ, 30.0F, 30.0F);
            }

            if (this.pedito.level() instanceof ServerLevel serverLevel && myIndex == 0) {
                // Leader logic
                if (this.phase == 0) {
                    // Net lines (random connections)
                    for (int n = 0; n < 5; n++) {
                        PeditoEntity p1 = allies.get(this.pedito.getRandom().nextInt(totalInDome));
                        PeditoEntity p2 = allies.get(this.pedito.getRandom().nextInt(totalInDome));
                        serverLevel.sendParticles(new DustParticleOptions(0xCC00CC, 0.4F), 
                                p1.getX(), p1.getY(), p1.getZ(), 1, (p2.getX()-p1.getX())*0.5, (p2.getY()-p1.getY())*0.5, (p2.getZ()-p1.getZ())*0.5, 0.5);
                    }
                    this.meteorY = this.impactY + 35.0;
                } else if (this.phase == 1) {
                    if (this.timer == 20) {
                        this.pedito.playSound(ModSounds.PEDITO_VOICE_SWAN, 1.0F, 0.50F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.03F);
                    }
                    this.meteorY -= 0.9; // Descends at 18 m/s (0.9 blocks/tick)
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.impactX, this.meteorY, this.impactZ, 100, 4.0, 4.0, 4.0, 0.05);
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.impactX, this.meteorY, this.impactZ, 50, 4.0, 4.0, 4.0, 0.1);
                } else if (this.phase == 2) {
                    serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, this.impactX, this.impactY, this.impactZ, 10, 1.0, 0.1, 1.0, 0);
                    serverLevel.sendParticles(new DustParticleOptions(this.pedito.getRandom().nextInt(0xFFFFFF), 2.0F), this.impactX, this.impactY, this.impactZ, 500, 15.0, 1.0, 15.0, 0.1);
                    this.pedito.playSound(ModSounds.PEDITO_EXPLOSION, 2.0F, 0.75F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.02F);
                    
                    AABB damageArea = new AABB(this.impactX, this.impactY, this.impactZ, this.impactX, this.impactY, this.impactZ).inflate(15.0D);
                    List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(
                            LivingEntity.class,
                            damageArea,
                            e -> e.isAlive() && e != owner && !(e instanceof PeditoEntity)
                    );
                    for (LivingEntity enemy : enemies) {
                        enemy.hurt(serverLevel.damageSources().magic(), 35.0F);
                    }
                } else if (this.phase == 3) {
                    if (this.pedito.tickCount % 5 == 0) {
                        serverLevel.sendParticles(ParticleTypes.FALLING_HONEY, this.impactX, this.impactY + 15.0, this.impactZ, 50, 15.0, 5.0, 15.0, 0.0);
                        serverLevel.sendParticles(ParticleTypes.GLOW, this.impactX, this.impactY + 15.0, this.impactZ, 20, 15.0, 5.0, 15.0, 0.0);
                    }
                    if (this.pedito.tickCount % 20 == 0) {
                        this.pedito.playSound(ModSounds.PEDITO_SPRAY, 0.8F, 0.80F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.03F);
                        
                        List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(
                                LivingEntity.class,
                                owner.getBoundingBox().inflate(15.0D),
                                e -> e.isAlive() && e != owner && !(e instanceof PeditoEntity)
                        );
                        for (LivingEntity enemy : enemies) {
                            enemy.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2, false, false));
                            enemy.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 2, false, false));
                            enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 240, 2, false, false));
                        }
                    }
                }
            }
            
            if (this.phase == 4) {
                // Exhaustion phase
                if (this.timer == 400 && myIndex == 0) {
                    this.pedito.playSound(ModSounds.PEDITO_VOICE_PEDI, 0.7F, 0.80F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
                }
                this.pedito.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2, false, false)); // 50% speed
            }
        }

        this.timer--;
        if (this.timer <= 0) {
            if (this.phase == 0) { this.phase = 1; this.timer = 20; }
            else if (this.phase == 1) { this.phase = 2; this.timer = 5; }
            else if (this.phase == 2) { this.phase = 3; this.timer = 200; }
            else if (this.phase == 3) { this.phase = 4; this.timer = 400; }
            else if (this.phase == 4) { this.phase = 6; } // end
        }
    }
}
