package net.pedito.mod.entity.golem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.pedito.mod.registry.ModSounds;

public class PeditoGolemEntity extends IronGolem {
    private int lifetimeTicks = 0;
    private static final int MAX_LIFETIME = 300; // 15 seconds (300 ticks)

    public PeditoGolemEntity(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ATTACK_DAMAGE, 15.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            lifetimeTicks++;
            if (lifetimeTicks >= MAX_LIFETIME) {
                this.vanish();
            }
        }
    }

    private void vanish() {
        if (this.level() instanceof ServerLevel serverLevel) {
            double x = this.getX();
            double y = this.getY() + 1.0;
            double z = this.getZ();
            
            // Fart-themed poof/smoke/gas visual effects
            serverLevel.sendParticles(ParticleTypes.POOF, x, y, z, 40, 0.5, 1.0, 0.5, 0.1);
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 50, 0.8, 1.2, 0.8, 0.05);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 20, 0.5, 1.0, 0.5, 0.05);
            
            // Sound feedback: Gas release, explosion & sound of fart
            serverLevel.playSound(null, x, y, z, ModSounds.PEDITO_EXPLOSION, SoundSource.NEUTRAL, 1.5F, 1.1F);
            serverLevel.playSound(null, x, y, z, ModSounds.PEDITO_FART, SoundSource.NEUTRAL, 1.2F, 0.8F);
        }
        this.discard();
    }
}

