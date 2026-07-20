package net.pedito.mod.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import java.util.List;

public class PeditoStaffItem extends Item {
    public PeditoStaffItem(Item.Properties properties) {
        super(properties);
    }

    public static Item.Properties createProperties() {
        return new Item.Properties().stacksTo(1);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        
        // Ensure swing animation plays
        player.swing(hand, true);
        
        if (!level.isClientSide()) {
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getViewVector(1.0F);
            Vec3 rayEnd = eyePos.add(lookVec.scale(25.0D));
            AABB aabb = player.getBoundingBox().expandTowards(lookVec.scale(25.0D)).inflate(1.0D);
            
            // Check entities first
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eyePos, rayEnd, aabb, (entity) -> entity instanceof LivingEntity && entity != player, 0.0f);
            
            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
                // Target an entity
                target.hurt(level.damageSources().magic(), 6.0F);
                level.playSound(null, player.blockPosition(), ModSounds.PEDITO_STAFF, SoundSource.PLAYERS, 1.0F, 1.0F);
                
                AABB searchAABB = target.getBoundingBox().inflate(128.0D);
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, searchAABB, 
                    (pedito) -> pedito.getOwnerCustom() == player
                );
                
                for (PeditoEntity pedito : peditos) {
                    pedito.setTarget(target);
                }
                
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                }
            } else {
                // Check blocks
                BlockHitResult blockHit = level.clip(new ClipContext(eyePos, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (blockHit.getType() == HitResult.Type.BLOCK) {
                    Vec3 hitPos = blockHit.getLocation();
                    level.playSound(null, player.blockPosition(), ModSounds.PEDITO_STAFF, SoundSource.PLAYERS, 1.0F, 1.0F);
                    
                    AABB searchAABB = player.getBoundingBox().inflate(128.0D);
                    List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, searchAABB, 
                        (pedito) -> pedito.getOwnerCustom() == player
                    );
                    
                    for (PeditoEntity pedito : peditos) {
                        pedito.getNavigation().moveTo(hitPos.x, hitPos.y, hitPos.z, 1.5D);
                    }
                    
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y + 0.5, hitPos.z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        
        return InteractionResult.SUCCESS;
    }
}
