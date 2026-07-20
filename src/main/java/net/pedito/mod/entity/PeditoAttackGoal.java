package net.pedito.mod.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

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

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                target.getBoundingBox().inflate(40.0D),
                e -> e.isAlive() && (e.getTarget() == target || (e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom()))
        );
        
        PeditoEntity.SquadRole myRole = this.pedito.getSquadRole();
        
        // Find peers in the same role to calculate formation geometry
        List<PeditoEntity> peers = allies.stream()
            .filter(e -> e.getSquadRole() == myRole)
            .sorted((p1, p2) -> {
                int score1 = p1.getTier() * 1000 + (int)(p1.getHealth() * 10);
                int score2 = p2.getTier() * 1000 + (int)(p2.getHealth() * 10);
                if (score1 != score2) return Integer.compare(score2, score1);
                return Integer.compare(p1.getId(), p2.getId());
            }).collect(Collectors.toList());
            
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
        
        if (myRole == PeditoEntity.SquadRole.SOLO || myRole == PeditoEntity.SquadRole.VANGUARD) {
            double radius = 2.5;
            double angle = (Math.PI * 2 * roleIndex) / roleTotal + time * this.orbitDirection * 1.5;
            wantedX = targetX + Math.cos(angle) * radius;
            wantedZ = targetZ + Math.sin(angle) * radius;
            wantedY = targetY + 1.0 + Math.sin(time * 2.0 + myIndex) * 0.5; 
        } else if (myRole == PeditoEntity.SquadRole.TACTICAL) {
            double radius = 6.0;
            double angle = (Math.PI * 2 * roleIndex) / roleTotal - time * this.orbitDirection * 0.5;
            wantedX = targetX + Math.cos(angle) * radius;
            wantedZ = targetZ + Math.sin(angle) * radius;
            wantedY = targetY + 3.0 + Math.sin(time * 2.0 + myIndex) * 1.0; 
        } else if (myRole == PeditoEntity.SquadRole.ARTILLERY) {
            double radius = 14.0;
            double angle = (Math.PI * 2 * roleIndex) / roleTotal + time * this.orbitDirection * 0.2;
            wantedX = targetX + Math.cos(angle) * radius;
            wantedZ = targetZ + Math.sin(angle) * radius;
            wantedY = targetY + 6.0 + Math.sin(time + myIndex) * 2.0; 
        } else if (myRole == PeditoEntity.SquadRole.ROYAL_GUARD) {
            net.minecraft.world.entity.player.Player owner = this.pedito.getOwnerCustom();
            if (owner != null) {
                double radius = 2.0;
                double angle = (Math.PI * 2 * roleIndex) / roleTotal + time * 2.0;
                wantedX = owner.getX() + Math.cos(angle) * radius;
                wantedZ = owner.getZ() + Math.sin(angle) * radius;
                wantedY = owner.getY() + 1.0;
            }
        }
        
        double currentDistSq = this.pedito.distanceToSqr(wantedX, wantedY, wantedZ);
        if (currentDistSq > 1.0D) {
            this.pedito.getMoveControl().setWantedPosition(wantedX, wantedY, wantedZ, 1.8D);
        } else {
            this.pedito.setDeltaMovement(this.pedito.getDeltaMovement().multiply(0.5, 0.5, 0.5));
        }

        this.attackCooldown = Math.max(this.attackCooldown - 1, 0);
        double distToTargetSq = this.pedito.distanceToSqr(target);
        
        if (this.attackCooldown == 0 && this.pedito.hasLineOfSight(target)) {
            boolean canAttack = false;
            int nextCooldown = 20;
            
            if ((myRole == PeditoEntity.SquadRole.VANGUARD || myRole == PeditoEntity.SquadRole.SOLO) && distToTargetSq <= 16.0D) {
                canAttack = true;
                nextCooldown = 15; 
            } else if (myRole == PeditoEntity.SquadRole.TACTICAL && distToTargetSq <= 64.0D) {
                canAttack = true;
                nextCooldown = 30; 
            } else if (myRole == PeditoEntity.SquadRole.ARTILLERY && distToTargetSq <= 400.0D) {
                canAttack = true;
                nextCooldown = 60; 
            } else if (myRole == PeditoEntity.SquadRole.ROYAL_GUARD && distToTargetSq <= 36.0D) {
                canAttack = true;
                nextCooldown = 10; 
            }
            
            if (canAttack && (this.pedito.getRandom().nextInt(Math.max(1, roleTotal)) == 0 || roleTotal < 3)) {
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
                case 1: color = 0xCC7722; damage = 1.5F; break; // Copper
                case 2: color = 0xAAAAAA; damage = 2.0F; break; // Iron
                case 3: color = 0xFFD700; damage = 2.5F; break; // Gold
                case 4: color = 0x00FFFF; damage = 4.0F; break; // Diamond
                case 5: color = 0x4B0082; damage = 6.0F; break; // Netherite
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
                if (role == PeditoEntity.SquadRole.TACTICAL || role == PeditoEntity.SquadRole.ARTILLERY) {
                    Vec3 start = this.pedito.position().add(0, this.pedito.getBbHeight()/2, 0);
                    Vec3 end = target.position().add(0, target.getBbHeight()/2, 0);
                    Vec3 dir = end.subtract(start).normalize();
                    double dist = start.distanceTo(end);
                    for (double d = 0; d < dist; d += 0.5) {
                        Vec3 p = start.add(dir.scale(d));
                        serverLevel.sendParticles(new DustParticleOptions(color, 0.8F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                    serverLevel.playSound(null, this.pedito.getX(), this.pedito.getY(), this.pedito.getZ(), ModSounds.PEDITO_LASER, SoundSource.NEUTRAL, 0.8F, 1.2F);
                } else {
                    serverLevel.sendParticles(new DustParticleOptions(color, 1.5F),
                            target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ(),
                            80, 0.8, 0.8, 0.8, 0.2);
                    serverLevel.playSound(null, this.pedito.getX(), this.pedito.getY(), this.pedito.getZ(), ModSounds.PEDITO_FART, SoundSource.NEUTRAL, 0.8F, 1.2F);
                }

                this.pedito.playAttackVoice();
            }

            // Swarm Damage saturation formula: D_total = D_avg * (1 + ln(1 + beta * (N - 1))) * mu_tier
            // Scale individual hits by (1 + ln(1 + beta * (N - 1))) / N to cap cumulative DPS log-style
            List<PeditoEntity> swarmAllies = this.pedito.level().getEntitiesOfClass(
                    PeditoEntity.class,
                    target.getBoundingBox().inflate(16.0D),
                    e -> e.isAlive() && (e.getTarget() == target || (e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom()))
            );
            int N = Math.max(1, swarmAllies.size());
            double beta = 0.45;
            float swarmMultiplier = (float) ((1.0 + Math.log(1.0 + beta * (N - 1))) / N);
            damage *= swarmMultiplier;

            target.hurtServer(serverLevel, level.damageSources().mobAttack(this.pedito), damage);
        }
    }
}
