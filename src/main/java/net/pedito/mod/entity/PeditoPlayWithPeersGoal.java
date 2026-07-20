package net.pedito.mod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

public class PeditoPlayWithPeersGoal extends Goal {
    private final PeditoEntity pedito;
    private PeditoEntity playmate;
    private int playTimer;

    public PeditoPlayWithPeersGoal(PeditoEntity pedito) {
        this.pedito = pedito;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.pedito.isSittingCustom()) return false;
        if (this.pedito.getTarget() != null) return false;
        if (this.pedito.isTamedByOwner() && this.pedito.isOwnerInDanger()) return false;
        
        // Juegan más si son bebés o salvajes, menos si son adultos domesticados
        int chance = this.pedito.isBaby() ? 100 : (this.pedito.isTamedByOwner() ? 400 : 200);
        if (this.pedito.getRandom().nextInt(chance) != 0) return false;

        Player owner = this.pedito.getOwnerCustom();

        List<PeditoEntity> allies = this.pedito.level().getEntitiesOfClass(
                PeditoEntity.class,
                this.pedito.getBoundingBox().inflate(10.0D),
                e -> {
                    if (!e.isAlive() || e == this.pedito || e.isSittingCustom() || e.getTarget() != null) return false;
                    // Si ambos son domesticados, que sean del mismo dueño. Si ambos son salvajes, juegan entre sí.
                    if (this.pedito.isTamedByOwner() != e.isTamedByOwner()) return false;
                    if (this.pedito.isTamedByOwner()) return e.getOwnerCustom() == owner;
                    return true;
                }
        );
        
        if (allies.isEmpty()) return false;
        
        this.playmate = allies.get(this.pedito.getRandom().nextInt(allies.size()));
        return true;
    }

    @Override
    public void start() {
        this.playTimer = 100 + this.pedito.getRandom().nextInt(100); // 5 to 10 seconds
    }

    @Override
    public boolean canContinueToUse() {
        if (this.pedito.isSittingCustom() || this.pedito.getTarget() != null) return false;
        if (this.pedito.isTamedByOwner() && this.pedito.isOwnerInDanger()) return false;
        
        return this.playTimer > 0 && this.playmate != null && this.playmate.isAlive() && this.pedito.distanceToSqr(this.playmate) < 400.0D;
    }

    @Override
    public void tick() {
        this.playTimer--;
        
        this.pedito.getLookControl().setLookAt(this.playmate, 10.0F, 10.0F);
        
        if (this.pedito.distanceToSqr(this.playmate) > 4.0D) {
            this.pedito.getNavigation().moveTo(this.playmate, 1.2D);
        } else {
            this.pedito.getNavigation().stop();
        }
        
        if (this.playTimer % 20 == 0 && this.pedito.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, 
                this.pedito.getX(), this.pedito.getY() + 0.5, this.pedito.getZ(), 
                2, 0.2, 0.2, 0.2, 0.0);
                
            if (this.pedito.getRandom().nextInt(3) == 0) {
                this.pedito.playSound(net.pedito.mod.registry.ModSounds.PEDITO_VOICE_PEDI, 0.3F, 1.2F); // "jaja" or similar sound
            }
        }
    }
}
