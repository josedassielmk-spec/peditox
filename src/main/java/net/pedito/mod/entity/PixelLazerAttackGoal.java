package net.pedito.mod.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class PixelLazerAttackGoal extends Goal {
    private final PeditoEntity pedito;
    private long nextAttackTick;
    private int attackTime;
    public PixelLazerAttackGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        PeditoEntity.SquadRole role = this.pedito.getSquadRole();
        if (role != PeditoEntity.SquadRole.ARTILLERY && role != PeditoEntity.SquadRole.SOLO) {
            return false;
        }
    
        if (this.pedito.tickCount < this.nextAttackTick) return false;
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive() || !this.pedito.isTamedByOwner()) return false;
        // removed ally restriction for pixel lazer
        return this.pedito.getRandom().nextInt(40) == 0;
    }

    @Override
    public void start() {
        this.attackTime = 60; // shoots for 3 seconds
        // Instead of completely stopping, let's allow slow repositioning
    }

    @Override
    public void tick() {
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive()) {
            this.attackTime = 0;
            return;
        }
        this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Formation Logic: Align in a semicircle or line facing the target
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                target.getBoundingBox().inflate(16.0D),
                e -> e.isAlive() && e.getTarget() == target
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        
        int total = allies.size();
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;

        // Reposition into formation while firing
        Vec3 dirToTarget = target.position().subtract(this.pedito.position()).normalize();
        Vec3 right = dirToTarget.cross(new Vec3(0, 1, 0)).normalize();
        
        // Pixel Lazer — línea de flanqueo
        // 3.0-5.0 blocks behind/sides · +1.5 height
        // oscillation y = base + 0.05*sin(edad*0.2)
        int side = (myIndex % 2 == 0) ? 1 : -1;
        int row = (myIndex / 2);
        
        double backDist = 3.0 + row * 1.0; // 3.0 to 5.0 behind
        double sideDist = side * (3.0 + row * 1.0); // sides
        
        double oscillationY = 1.5 + 0.05 * Math.sin(this.pedito.tickCount * 0.2);
        
        Vec3 optimalPos = target.position()
            .subtract(dirToTarget.scale(backDist))
            .add(right.scale(sideDist))
            .add(0, oscillationY, 0);

        if (this.pedito.distanceToSqr(optimalPos) > 1.0) {
            this.pedito.getMoveControl().setWantedPosition(optimalPos.x, optimalPos.y, optimalPos.z, 1.0D);
        } else {
            this.pedito.getNavigation().stop();
        }

        if (this.pedito.level() instanceof ServerLevel serverLevel) {
            Vec3 start = new Vec3(this.pedito.getX(), this.pedito.getY() + this.pedito.getBbHeight() / 2.0, this.pedito.getZ());
            Vec3 end = new Vec3(target.getX(), target.getY() + target.getBbHeight() / 2.0, target.getZ());
            Vec3 dir = end.subtract(start).normalize();

            // Sound
            if (this.attackTime % 10 == 0) {
                if (this.pedito.getRandom().nextInt(2) == 0) {
                    this.pedito.playAttackVoice();
                } else {
                    this.pedito.triggerTalkAnimation();
                }
                this.pedito.playSound(ModSounds.PEDITO_LASER, 0.4F, 1.0F);
            }

            // Draw Lazer
            double distance = start.distanceTo(end);
            for (double d = 0; d < distance; d += 0.5) {
                Vec3 pos = start.add(dir.scale(d));
                
                // Rainbow color based on distance and time
                float hue = (float) ((d * 0.1 + this.attackTime * 0.05) % 1.0);
                int rgb = java.awt.Color.HSBtoRGB(hue, 1.0F, 1.0F);
                int color = rgb & 0xFFFFFF;
                
                serverLevel.sendParticles(new DustParticleOptions(color, 0.8F),
                        pos.x, pos.y, pos.z, 3, 0.1, 0.1, 0.1, 0);
            }

            // Damage target
            if (this.attackTime % 5 == 0) {
                target.hurtServer(serverLevel, this.pedito.level().damageSources().mobAttack(this.pedito), 1.5F);
                target.setDeltaMovement(target.getDeltaMovement().add(dir.scale(0.1))); // tiny push
            }
        }

        this.attackTime--;
        if (this.attackTime <= 0) {
            this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 13 : 15); // 5 seconds cooldown
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.attackTime > 0 && this.pedito.getTarget() != null && this.pedito.getTarget().isAlive();
    }
}
