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

public class RainbowDashAttackGoal extends Goal {
    private final PeditoEntity pedito;
    private long nextAttackTick;
    private int attackState; // 0 = inactive, 1 = positioning, 2 = charging, 3 = dashing
    private int timer;
    private Vec3 dashDirection;

    public RainbowDashAttackGoal(PeditoEntity pedito) {
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
        if (this.pedito.getAllyCount() < 2) return false;
        return this.pedito.getRandom().nextInt(40) == 0;
    }

    @Override
    public void start() {
        this.attackState = 1;
        this.timer = 30; // 1.5 seconds positioning
    }

    @Override
    public void tick() {
        LivingEntity target = this.pedito.getTarget();
        if (target == null || !target.isAlive()) {
            this.attackState = 0;
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

        if (this.attackState == 1) { // Positioning around target in a star pattern
            this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.timer--;
            
            // Rainbow Dash — cuña de embestida
            // V a 8.0 bloques detrás
            Vec3 dirToTarget = target.position().subtract(this.pedito.position()).normalize();
            Vec3 right = dirToTarget.cross(new Vec3(0, 1, 0)).normalize();
            
            int row = (myIndex + 1) / 2;
            int side = (myIndex % 2 == 0) ? 1 : -1;
            if (myIndex == 0) { row = 0; side = 0; } // Leader
            
            Vec3 basePos = target.position().subtract(dirToTarget.scale(8.0));
            
            Vec3 optimalPos = basePos
                .subtract(dirToTarget.scale(row * 1.5))
                .add(right.scale(row * 1.5 * side))
                .add(0, 1.0, 0);
            
            this.pedito.getMoveControl().setWantedPosition(optimalPos.x, optimalPos.y, optimalPos.z, 2.0D);
            
            if (this.timer <= 0) {
                this.attackState = 2;
                this.timer = 15; // 0.75 seconds charge in place
                this.pedito.getNavigation().stop();
                this.pedito.playAttackVoice(1.5F);
            }
            
        } else if (this.attackState == 2) { // Charging
            this.pedito.getLookControl().setLookAt(target, 30.0F, 30.0F);
            this.timer--;
            
            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Charging particles
                for (int i = 0; i < 3; i++) {
                    int r = this.pedito.getRandom().nextInt(256);
                    int g = this.pedito.getRandom().nextInt(256);
                    int b = this.pedito.getRandom().nextInt(256);
                    int color = (r << 16) | (g << 8) | b;
                    
                    serverLevel.sendParticles(new DustParticleOptions(color, 0.5F),
                            this.pedito.getX() + (this.pedito.getRandom().nextDouble() - 0.5),
                            this.pedito.getY() + this.pedito.getRandom().nextDouble(),
                            this.pedito.getZ() + (this.pedito.getRandom().nextDouble() - 0.5),
                            1, 0, 0, 0, 0);
                }
            }
            if (this.timer <= 0) {
                this.attackState = 3;
                this.timer = 15; // 0.75 seconds of dashing
                Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2.0, 0);
                Vec3 peditoPos = this.pedito.position().add(0, this.pedito.getBbHeight() / 2.0, 0);
                this.dashDirection = targetPos.subtract(peditoPos).normalize().scale(1.0); // 20 m/s = 1 block/tick
                this.pedito.playSound(ModSounds.PEDITO_DASH, 0.5F, 1.0F);
            }
        } else if (this.attackState == 3) { // Dashing
            this.pedito.setDeltaMovement(this.dashDirection);
            this.timer--;

            if (this.pedito.level() instanceof ServerLevel serverLevel) {
                // Rainbow trail
                int r = this.pedito.getRandom().nextInt(256);
                int g = this.pedito.getRandom().nextInt(256);
                int b = this.pedito.getRandom().nextInt(256);
                int color = (r << 16) | (g << 8) | b;
                serverLevel.sendParticles(new DustParticleOptions(color, 1.0F),
                        this.pedito.getX(), this.pedito.getY() + this.pedito.getBbHeight() / 2.0, this.pedito.getZ(),
                        10, 0.5, 0.5, 0.5, 0.1);
                
                // Damage entities in path
                AABB boundingBox = this.pedito.getBoundingBox().inflate(1.0D);
                List<LivingEntity> hitEntities = this.pedito.level().getEntitiesOfClass(LivingEntity.class, boundingBox, e -> e != this.pedito && !e.isAlliedTo(this.pedito));
                for (LivingEntity e : hitEntities) {
                    if (this.pedito.isTamedByOwner() && (e == this.pedito.getOwnerCustom() || (e instanceof PeditoEntity otherPedito && otherPedito.isTamedByOwner()))) continue;
                    e.hurtServer(serverLevel, this.pedito.level().damageSources().mobAttack(this.pedito), 6.0F);
                    e.setDeltaMovement(e.getDeltaMovement().add(this.dashDirection.scale(0.5)));
                }
            }

            if (this.timer <= 0 || this.pedito.horizontalCollision) {
                this.attackState = 0;
                this.pedito.setDeltaMovement(Vec3.ZERO);
                this.nextAttackTick = this.pedito.tickCount + (this.pedito.hasCopperSynergy() ? 88 : 100); // 5 seconds
            }
        }
    }

    @Override
    public boolean canContinueToUse() {
        return this.attackState > 0;
    }
}
