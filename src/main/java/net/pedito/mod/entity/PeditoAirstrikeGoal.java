package net.pedito.mod.entity;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
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
        this.timer = 20; // Time to fly up and align
        this.pedito.setSpinning(false);
        this.pedito.setSpinningTicks(0);
        this.pedito.playSound(ModSounds.PEDITO_VOICE_PUPULLITO, 0.7F, 1.20F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.05F);
    }

    @Override
    public void stop() {
        this.pedito.setSpinning(false);
        this.pedito.setSpinningTicks(0);
        super.stop();
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
            this.targetY = this.strikeY + 12.0; // Fly up high!
            this.targetZ = this.strikeZ + offsetZ;

            // Fly rapidly up to align
            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.targetY, this.targetZ, 2.0D);
            this.pedito.getLookControl().setLookAt(this.strikeX, this.strikeY, this.strikeZ, 30.0F, 30.0F);

            this.timer--;
            if (this.timer <= 0) {
                this.phase = 1;
                this.timer = 40; // Max dive duration
                this.pedito.setSpinning(true);
                this.pedito.setSpinningTicks(0);
            }
        } else if (this.phase == 1) {
            // Dive bomb!
            // Look straight down as they dive like a drill
            this.pedito.getLookControl().setLookAt(this.targetX, this.strikeY - 2.0, this.targetZ, 180.0F, 90.0F);
            
            // Increment spinning tick counter
            this.pedito.setSpinningTicks(this.pedito.getSpinningTicks() + 1);

            // Move control down towards the ground strike target
            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.strikeY, this.targetZ, 1.5D);

            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Spawn a beautiful speed-trail / meteorite tail
                // Flame particles
                serverLevel.sendParticles(ParticleTypes.FLAME, this.pedito.getX(), this.pedito.getY() + 0.3, this.pedito.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
                // Smoke particles
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, this.pedito.getX(), this.pedito.getY() + 0.3, this.pedito.getZ(), 3, 0.1, 0.1, 0.1, 0.01);
                // Glowing red/orange speed trail dust
                serverLevel.sendParticles(new DustParticleOptions(0xFF0000, 1.2F), this.pedito.getX(), this.pedito.getY() + 0.3, this.pedito.getZ(), 6, 0.2, 0.2, 0.2, 0.0);
                
                // Variant-specific custom particle sparks
                if (this.pedito.getVariant() == PeditoEntity.VARIANT_RAINBOW) {
                    int rgb = (this.pedito.getRandom().nextInt(256) << 16) | (this.pedito.getRandom().nextInt(256) << 8) | this.pedito.getRandom().nextInt(256);
                    serverLevel.sendParticles(new DustParticleOptions(rgb, 1.0F), this.pedito.getX(), this.pedito.getY() + 0.3, this.pedito.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
                } else if (this.pedito.getVariant() == PeditoEntity.VARIANT_NIGHT) {
                    serverLevel.sendParticles(new DustParticleOptions(0x1A237E, 1.0F), this.pedito.getX(), this.pedito.getY() + 0.3, this.pedito.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
                }
            }

            this.timer--;
            
            // Impact checks: hit the ground, or too low, or timer ran out
            boolean hitGround = this.pedito.getY() <= this.strikeY + 1.2 || this.pedito.onGround() || this.timer <= 0;
            if (hitGround) {
                // EXPLOSION!
                if (this.pedito.level() instanceof ServerLevel serverLevel) {
                    double x = this.pedito.getX();
                    double y = this.pedito.getY();
                    double z = this.pedito.getZ();

                    serverLevel.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 5, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 0.8, 0.8, 0.8, 0.05);
                    serverLevel.sendParticles(ParticleTypes.FLAME, x, y, z, 25, 0.6, 0.6, 0.6, 0.1);

                    this.pedito.playSound(ModSounds.PEDITO_EXPLOSION, 1.0F, 1.1F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.1F);
                    this.pedito.playSound(ModSounds.PEDITO_FART, 1.2F, 0.8F + (this.pedito.getRandom().nextFloat() - this.pedito.getRandom().nextFloat()) * 0.1F);

                    // Damage and knock back enemies in a 4.0 blocks radius
                    AABB damageBox = new AABB(x - 4.0, y - 2.0, z - 4.0, x + 4.0, y + 3.0, z + 4.0);
                    List<LivingEntity> enemies = this.pedito.level().getEntitiesOfClass(
                            LivingEntity.class,
                            damageBox,
                            e -> e.isAlive() && e != this.pedito.getOwnerCustom() && !(e instanceof PeditoEntity)
                    );
                    for (LivingEntity enemy : enemies) {
                        enemy.hurtServer(serverLevel, this.pedito.level().damageSources().mobAttack(this.pedito), 10.0F);
                        enemy.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1, false, false));
                        enemy.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2, false, false));

                        double dx = enemy.getX() - x;
                        double dz = enemy.getZ() - z;
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (dist > 0) {
                            enemy.setDeltaMovement(enemy.getDeltaMovement().add(dx / dist * 1.5, 0.6, dz / dist * 1.5));
                        }
                    }
                }

                // Stop spinning
                this.pedito.setSpinning(false);
                this.pedito.setSpinningTicks(0);

                // Transition to pool/return phase
                this.phase = 2;
                this.timer = 100;
            }
        } else if (this.phase == 2) {
            // Pool persistence phase: float back up slowly and release toxic fumes
            this.pedito.getMoveControl().setWantedPosition(this.targetX, this.strikeY + 4.0, this.targetZ, 0.8D);
            this.pedito.getLookControl().setLookAt(this.targetX, this.strikeY, this.targetZ, 30.0F, 30.0F);

            if (this.pedito.level() instanceof ServerLevel serverLevel) {
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
