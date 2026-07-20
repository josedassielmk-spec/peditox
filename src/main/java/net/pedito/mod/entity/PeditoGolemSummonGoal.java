package net.pedito.mod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.pedito.mod.entity.golem.PeditoGolemEntity;
import net.pedito.mod.Pedito;
import net.pedito.mod.registry.ModSounds;
import java.util.EnumSet;
import java.util.List;

public class PeditoGolemSummonGoal extends Goal {
    private final PeditoEntity pedito;
    private long nextAttackTick;
    private int castTimer;

    public PeditoGolemSummonGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        PeditoEntity.SquadRole role = this.pedito.getSquadRole();
        if (role != PeditoEntity.SquadRole.VANGUARD && role != PeditoEntity.SquadRole.SOLO) {
            return false;
        }
    
        if (this.pedito.tickCount < this.nextAttackTick) return false;
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive() || !this.pedito.isTamedByOwner()) return false;
        
        if (this.pedito.getAllyCount() < 6) return false;
        
        // Probabilidad de activación: 5% por tick
        if (this.pedito.getRandom().nextInt(20) != 0) return false;

        // Escanea radio 30.0 bloques para garantizar que no exista un Golem previo en combate
        List<PeditoGolemEntity> golems = this.pedito.level().getEntitiesOfClass(
                PeditoGolemEntity.class, 
                this.pedito.getBoundingBox().inflate(30.0D)
        );
        if (!golems.isEmpty()) {
            this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 88 : 100); // Micro-cooldown por spam
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        this.castTimer = 40; // 40 ticks
        this.pedito.getNavigation().stop();
        this.pedito.playAttackVoice(0.5F);
    }

    @Override
    public void tick() {
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive()) {
            this.castTimer = 0;
            return;
        }

        this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);
        this.castTimer--;

        if (this.pedito.level() instanceof ServerLevel serverLevel) {
            // Fase de canalizacion
            if (this.castTimer > 0) {
                double x = this.pedito.getX() + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                double y = this.pedito.getY() + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                double z = this.pedito.getZ() + (this.pedito.getRandom().nextDouble() - 0.5) * 4.0;
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0);
            } 
            
            // Spawn Golem
            if (this.castTimer == 0) {
                PeditoGolemEntity golem = Pedito.PEDITO_GOLEM_ENTITY.create(serverLevel, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
                if (golem != null) {
                    golem.setPos(this.pedito.getX(), this.pedito.getY(), this.pedito.getZ());
                    golem.setCustomName(Component.literal("Pedito Golem"));
                    golem.setCustomNameVisible(true);
                    golem.setTarget(target);
                    // Wither I por 600 ticks
                    golem.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 0, false, false));
                    serverLevel.addFreshEntity(golem);
                    
                    // Detonación central instantánea
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.pedito.getX(), this.pedito.getY(), this.pedito.getZ(), 5, 1.0D, 1.0D, 1.0D, 0.1D);
                    this.pedito.playSound(ModSounds.PEDITO_SUMMON, 0.8F, 1.0F);
                }
                
                this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 1056 : 1200); // Cooldown 1200 ticks
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.castTimer > 0;
    }
}
