package net.pedito.mod.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.EnumSet;
import java.util.List;

public class PeditoGoldenBurstGoal extends Goal {
    private final PeditoEntity pedito;
    private int cooldown = 0;
    private int castTime = 0;

    public PeditoGoldenBurstGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.pedito.getVariant() != PeditoEntity.VARIANT_GOLDEN) return false;
        if (this.pedito.isBaby()) return false;
        
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }

        LivingEntity target = this.pedito.getTarget();
        if (target != null && target.isAlive() && this.pedito.distanceToSqr(target) < 100.0D) {
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.castTime = 40; // 2 seconds cast
        this.pedito.playSound(SoundEvents.TOTEM_USE, 1.0F, 1.0F);
        this.pedito.setSpinningTicks(40);
        this.pedito.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.castTime > 0) {
            this.castTime--;
            
            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Draw energy particles
                double radius = 1.5;
                double angle = this.castTime * 0.5;
                double x = this.pedito.getX() + Math.cos(angle) * radius;
                double z = this.pedito.getZ() + Math.sin(angle) * radius;
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, this.pedito.getY() + 1.0, z, 1, 0, 0, 0, 0);
            }
            
            if (this.castTime <= 0) {
                this.executeBurst();
                this.cooldown = 400; // 20 seconds cooldown
            }
        }
    }
    
    private void executeBurst() {
        if (!(this.pedito.level() instanceof ServerLevel serverLevel)) return;
        
        serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, this.pedito.getX(), this.pedito.getY() + 0.5, this.pedito.getZ(), 200, 3.0, 1.0, 3.0, 0.5);
        this.pedito.playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.5F); // Wait, GENERIC_EXPLODE is deprecated or use different? Let's use ENTITY_GENERIC_EXPLODE
        
        AABB area = this.pedito.getBoundingBox().inflate(6.0D);
        
        // Heal allies
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(PeditoEntity.class, area);
        for (PeditoEntity ally : allies) {
            if (ally.isAlive() && !ally.is(this.pedito)) {
                // Same owner or both wild
                if (ally.isTamedByOwner() == this.pedito.isTamedByOwner() && (!ally.isTamedByOwner() || ally.getOwnerCustom() == this.pedito.getOwnerCustom())) {
                    ally.heal(15.0F);
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, ally.getX(), ally.getY() + 0.5, ally.getZ(), 10, 0.5, 0.5, 0.5, 0);
                }
            }
        }
        
        // Damage enemies
        List<LivingEntity> entities = this.pedito.level().getEntitiesOfClass(LivingEntity.class, area);
        for (LivingEntity e : entities) {
            if (e != this.pedito && e.isAlive() && this.pedito.wantsToAttack(e, this.pedito.getOwnerCustom())) {
                e.hurt(this.pedito.damageSources().mobAttack(this.pedito), 15.0F);
                Vec3 knockback = e.position().subtract(this.pedito.position()).normalize().scale(1.5);
                e.push(knockback.x, 0.5, knockback.z);
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.castTime > 0;
    }
}
