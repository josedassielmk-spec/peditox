package net.pedito.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class CubicVortexAttackGoal extends Goal {
    private final PeditoEntity pedito;
    private long nextAttackTick;
    private int attackTimer;
    public CubicVortexAttackGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        PeditoEntity.SquadRole role = this.pedito.getSquadRole();
        if (role != PeditoEntity.SquadRole.TACTICAL && role != PeditoEntity.SquadRole.SOLO) {
            return false;
        }
    
        if (this.pedito.tickCount < this.nextAttackTick) return false;
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive() || !this.pedito.isTamedByOwner()) return false;
        if (this.pedito.getAllyCount() < 5) return false;
        return this.pedito.getRandom().nextInt(70) == 0;
    }

    @Override
    public void start() {
        this.attackTimer = 100; // 5 seconds
        this.pedito.playAttackVoice(0.5F); // Deep start pitch, now louder and animated
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

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                target.getBoundingBox().inflate(16.0D),
                e -> e.isAlive() && e.getTarget() == target
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        int total = allies.size();
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;

        // Cubic Vortex — Pentágono. radio 5.0, centrado +3.5 sobre el objetivo.
        // hélice descendente, cilindro radio 3.0
        
        double pentagonRadius = this.pedito.hasIronGoldSynergy() ? 6.5 : 5.0; // Base radio 5.0
        double heightOffset = 3.5;
        
        // Descending helix effect over the 100 ticks (from 100 down to 0)
        double progress = (100.0 - this.attackTimer) / 100.0; // 0.0 to 1.0
        
        // Helix cylinder radius (3.0), pentagon radius (5.0)
        double currentRadius = pentagonRadius * (1.0 - progress) + 3.0 * progress;
        double currentHeight = heightOffset - (progress * 3.5); // descends to target level
        
        // Pentagon angle (5 points), rotate over time
        double theta = (Math.PI * 2 / Math.max(1, total)) * myIndex;
        double timeAngle = this.pedito.tickCount * 0.2; // fast spin for vortex
        
        double x = Math.cos(theta + timeAngle) * currentRadius;
        double z = Math.sin(theta + timeAngle) * currentRadius;
        
        Vec3 optimalPos = target.position().add(
            x,
            currentHeight,
            z
        );
        
        // Move towards vortex position
        this.pedito.getMoveControl().setWantedPosition(optimalPos.x, optimalPos.y, optimalPos.z, 2.0D);

        this.attackTimer--;

        if (this.pedito.level() instanceof ServerLevel serverLevel) {
            // Draw connecting lines to form the "Cubic/Spherical" grid
            if (this.attackTimer % 5 == 0 && total > 1) {
                PeditoEntity nextAlly = allies.get((myIndex + 1) % total);
                Vec3 start = this.pedito.position().add(0, this.pedito.getBbHeight() / 2.0, 0);
                Vec3 end = nextAlly.position().add(0, nextAlly.getBbHeight() / 2.0, 0);
                Vec3 dir = end.subtract(start).normalize();
                double dist = start.distanceTo(end);
                
                int color = 0x8800FF; // Purple/Void color
                
                for (double d = 0; d < dist; d += 0.5) {
                    Vec3 pos = start.add(dir.scale(d));
                    serverLevel.sendParticles(new DustParticleOptions(color, 0.5F), pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                }
            }

            // Suck target in and damage
            if (this.attackTimer % 10 == 0) {
                target.hurtServer(serverLevel, this.pedito.level().damageSources().mobAttack(this.pedito), 1.0F);
                // Pull target to center (trap it)
                Vec3 toCenter = target.position().subtract(target.position()).scale(0); // Actually, keep it at 0 delta movement
                target.setDeltaMovement(0, 0, 0);
                
                this.pedito.playAttackVoice(0.8F);
                this.pedito.playSound(ModSounds.PEDITO_VORTEX, 0.5F, 1.0F); // Vortex sound
            }
        }

        if (this.attackTimer <= 0) {
            this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 140 : 160); // 10 seconds cooldown
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.attackTimer > 0;
    }
}
