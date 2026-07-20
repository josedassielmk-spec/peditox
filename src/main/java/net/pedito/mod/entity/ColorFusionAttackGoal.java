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

public class ColorFusionAttackGoal extends Goal {
    private final PeditoEntity pedito;
    private long nextAttackTick;
    private int attackTimer;
    public ColorFusionAttackGoal(PeditoEntity pedito) {
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
        if (this.pedito.getAllyCount() < 3) return false;
        return this.pedito.getRandom().nextInt(60) == 0;
    }

    @Override
    public void start() {
        this.attackTimer = 40; // 2 seconds cast time
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

        // Formation Logic: Rapidly spinning ring above the target
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                target.getBoundingBox().inflate(16.0D),
                e -> e.isAlive() && e.getTarget() == target
        );
        allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
        
        int total = allies.size();
        int myIndex = allies.indexOf(this.pedito);
        if (myIndex == -1) myIndex = 0;

        // Position above the target in a ring
        double angleOffset = (Math.PI * 2 * myIndex) / Math.max(1, total);
        double currentAngle = (40 - this.attackTimer) * 0.3 + angleOffset; // Spins faster as it charges
        double radius = 1.0 + (this.attackTimer / 40.0) * 2.0; // Shrinks as it charges

        Vec3 optimalPos = target.position().add(
            Math.cos(currentAngle) * radius,
            target.getBbHeight() + 2.0, // Above target
            Math.sin(currentAngle) * radius
        );
        
        this.pedito.getMoveControl().setWantedPosition(optimalPos.x, optimalPos.y, optimalPos.z, 2.0D); // Fast movement

        this.attackTimer--;

        if (this.pedito.level() instanceof ServerLevel serverLevel) {
            if (this.attackTimer > 0) {
                // Charging particles
                float progress = 1.0F - (this.attackTimer / 40.0F);
                double pRadius = 3.0D * progress;
                for (int i = 0; i < 8; i++) {
                    double angle = this.pedito.getRandom().nextDouble() * Math.PI * 2;
                    double x = this.pedito.getX() + Math.cos(angle) * pRadius;
                    double z = this.pedito.getZ() + Math.sin(angle) * pRadius;
                    double y = this.pedito.getY() + this.pedito.getBbHeight() / 2.0;
                    
                    int r = this.pedito.getRandom().nextInt(256); int g = this.pedito.getRandom().nextInt(256); int b = this.pedito.getRandom().nextInt(256); int color = (r << 16) | (g << 8) | b;
                    serverLevel.sendParticles(new DustParticleOptions(color, 0.8F), x, y, z, 1, 0, 0, 0, 0);
                }
            } else if (this.attackTimer == 0) {
                // EXPLOSION! Only one pedito should trigger the damage/sound to avoid overlapping if they sync perfectly, 
                // but since they all run this goal and might be slightly desynced, we just lower the radius or sound
                this.pedito.playAttackVoice(1.2F);
                this.pedito.playSound(ModSounds.PEDITO_EXPLOSION, 0.8F, 1.0F);

                // Massive colorful explosion
                for (int i = 0; i < 300; i++) {
                    int r = this.pedito.getRandom().nextInt(256); int g = this.pedito.getRandom().nextInt(256); int b = this.pedito.getRandom().nextInt(256); int color = (r << 16) | (g << 8) | b;
                    serverLevel.sendParticles(new DustParticleOptions(color, 1.0F),
                            this.pedito.getX(), this.pedito.getY() + this.pedito.getBbHeight() / 2.0, this.pedito.getZ(),
                            1,
                            (this.pedito.getRandom().nextDouble() - 0.5) * 6.0,
                            (this.pedito.getRandom().nextDouble() - 0.5) * 6.0,
                            (this.pedito.getRandom().nextDouble() - 0.5) * 6.0,
                            0.5);
                }

                // Damage and knockback area
                AABB area = this.pedito.getBoundingBox().inflate(6.0D);
                List<LivingEntity> entities = this.pedito.level().getEntitiesOfClass(LivingEntity.class, area, e -> e != this.pedito && !e.isAlliedTo(this.pedito));
                for (LivingEntity e : entities) {
                    if (this.pedito.isTamedByOwner() && (e == this.pedito.getOwnerCustom() || (e instanceof PeditoEntity otherPedito && otherPedito.isTamedByOwner()))) continue;
                    
                    e.hurtServer(serverLevel, this.pedito.level().damageSources().mobAttack(this.pedito), 8.0F); // 4 hearts
                    double dx = e.getX() - this.pedito.getX();
                    double dz = e.getZ() - this.pedito.getZ();
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    if (distance > 0) {
                        e.setDeltaMovement(e.getDeltaMovement().add(dx / distance * 2.0, 0.8, dz / distance * 2.0));
                    }
                }
                
                this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 105 : 120); // 6 seconds cooldown
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.attackTimer > 0;
    }
}
