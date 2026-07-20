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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.pedito.mod.entity.PeditoEntity;
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
        
        if (!level.isClientSide()) {
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getViewVector(1.0F);
            Vec3 rayEnd = eyePos.add(lookVec.scale(25.0D));
            AABB aabb = player.getBoundingBox().expandTowards(lookVec.scale(25.0D)).inflate(1.0D);
            
            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(level, player, eyePos, rayEnd, aabb, (entity) -> entity instanceof LivingEntity && entity != player, 0.0f);
            
            if (hitResult != null && hitResult.getEntity() instanceof LivingEntity target) {
                target.hurt(level.damageSources().magic(), 6.0F);
                
                AABB searchAABB = target.getBoundingBox().inflate(128.0D);
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, searchAABB, 
                    (pedito) -> pedito.getOwnerCustom() == player
                );
                
                for (PeditoEntity pedito : peditos) {
                    pedito.setTarget(target);
                }
            }
        }
        
        return InteractionResult.SUCCESS;
    }
}
