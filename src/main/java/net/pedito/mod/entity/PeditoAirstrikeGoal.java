package net.pedito.mod.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.registry.ModSounds;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

public class PeditoAirstrikeGoal extends Goal {
    private final PeditoEntity pedito;
    private int phase; // 0=align, 1=bombard, 2=pool
    private int timer;
    private long nextAttackTick;
    private double targetX, targetY, targetZ;
    private double strikeX, strikeY, strikeZ;

    public PeditoAirstrikeGoal(PeditoEntity pedito) {
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
        if (!this.pedito.isTamedByOwner()) return false;
        
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive()) return false;

        List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(
                LivingEntity.class,
                target.getBoundingBox().inflate(6.0D),
                e -> e.isAlive() && e != this.pedito.getOwnerCustom() && !(e instanceof PeditoEntity)
        );
        
        if (enemies.size() < 3) return false;
        
        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                this.pedito.getBoundingBox().inflate(16.0D),
                e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom()
        );
        
        if (allies.size() < 3) return false;

        // Calculate center of enemies
        double sumX = 0, sumY = 0, sumZ = 0;
        for (LivingEntity e : enemies) {
            sumX += e.getX();
            sumY += e.getY();
            sumZ += e.getZ();
        }
        this.strikeX = sumX / enemies.size();
        this.strikeY = sumY / enemies.size();
        this.strikeZ = sumZ / enemies.size();

        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phase < 3;
    }

    @Override
    public void start() {
        this.phase = 0;
        this.timer = 15;
        this.pedito.playSound(ModSounds.PEDITO_VOICE_PUPULLITO, 0.7F, 1.20F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
    }

    @Override
    public void tick() {
        if (this.phase == 0) {
            List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                    PeditoEntity.class,
                    this.pedito.getBoundingBox().inflate(32.0D),
                    e -> e.isAlive() && e.isTamedByOwner() && e.getOwnerCustom() == this.pedito.getOwnerCustom()
            );
            allies.sort(Comparator.comparingInt(net.minecraft.world.entity.Entity::getId));
            int myIndex = allies.indexOf(this.pedito);
            if (myIndex == -1) myIndex = 0;

            int cols = (int) Math.ceil(Math.sqrt(allies.size()));
            int row = myIndex / cols;
            int col = myIndex % cols;
            
            double gridSpacing = 1.5;
            double offsetX = (col - cols / 2.0) * gridSpacing;
            double offsetZ = (row - (allies.size() / cols) / 2.0) * gridSpacing;

            this.targetX = this.strikeX + offsetX;
            this.targetY = this.strikeY + 10.0;
            this.targetZ = this.strikeZ + offsetZ;

            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.targetY, this.targetZ, 2.0D);
            this.pedito.getLookControl().setLookAt(this.strikeX, this.strikeY, this.strikeZ, 30.0F, 30.0F);

            this.timer--;
            if (this.timer <= 0) {
                this.phase = 1;
                this.timer = 60;
            }
        } else if (this.phase == 1) {
            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.targetY, this.targetZ, 1.0D);
            this.pedito.getLookControl().setLookAt(this.strikeX, this.strikeY, this.strikeZ, 30.0F, 30.0F);
            
            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Shoot meteor every 12 ticks per pedito (staggered by id)
                if ((this.pedito.tickCount + this.pedito.getId()) % 12 == 0) {
                    this.pedito.playSound(ModSounds.PEDITO_SPRAY, 0.6F, 1.50F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.10F);
                    
                    // Actually visually striking the ground is just an explosion at the target after a delay
                    // But we can simulate a falling projectile by adding a task or just spawning particles down
                    for (int y = 0; y < 10; y++) {
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SLIME_BLOCK.defaultBlockState()), 
                                this.pedito.getX(), this.pedito.getY() - y, this.pedito.getZ(), 2, 0.1, 0.1, 0.1, 0);
                        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CRYING_OBSIDIAN.defaultBlockState()), 
                                this.pedito.getX(), this.pedito.getY() - y, this.pedito.getZ(), 2, 0.1, 0.1, 0.1, 0);
                    }
                    
                    // Impact explosion on the ground
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, this.pedito.getX(), this.strikeY, this.pedito.getZ(), 1, 0, 0, 0, 0);
                    this.pedito.playSound(ModSounds.PEDITO_EXPLOSION, 0.8F, 1.15F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
                }
            }

            this.timer--;
            if (this.timer <= 0) {
                this.phase = 2;
                this.timer = 100;
            }
        } else if (this.phase == 2) {
            // Pool persistence phase
            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Only the first one should apply area effects to save performance, but it's simpler to let everyone do a small radius
                if (this.pedito.tickCount % 10 == 0) {
                    serverLevel.sendParticles(ParticleTypes.SNEEZE, this.pedito.getX(), this.strikeY, this.pedito.getZ(), 10, 1.5, 0.5, 1.5, 0.01);
                    serverLevel.sendParticles(ParticleTypes.SQUID_INK, this.pedito.getX(), this.strikeY, this.pedito.getZ(), 5, 1.5, 0.1, 1.5, 0);
                    if (this.pedito.tickCount % 20 == 0) {
                        this.pedito.playSound(ModSounds.PEDITO_SPRAY, 0.5F, 0.70F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
                    }
                    
                    AABB poolBox = new AABB(this.pedito.getX() - 1.5, this.strikeY - 1, this.pedito.getZ() - 1.5,
                                            this.pedito.getX() + 1.5, this.strikeY + 2, this.pedito.getZ() + 1.5);
                    List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(LivingEntity.class, poolBox, e -> e.isAlive() && e != this.pedito.getOwnerCustom() && !(e instanceof PeditoEntity));
                    for (LivingEntity enemy : enemies) {
                        enemy.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0, false, false));
                        enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false));
                    }
                }
            }
            
            this.timer--;
            if (this.timer <= 0) {
                this.phase = 3;
                this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 440 : 500);
            }
        }
    }
}
