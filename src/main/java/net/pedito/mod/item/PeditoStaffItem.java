package net.pedito.mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.pedito.mod.Pedito;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModSounds;

import java.util.List;

public class PeditoStaffItem extends Item {

    public static Item.Properties createProperties() {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        Identifier.fromNamespaceAndPath(Pedito.MOD_ID, "staff_damage"), 7.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(
                        Identifier.fromNamespaceAndPath(Pedito.MOD_ID, "staff_speed"), -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
        return new Item.Properties().attributes(modifiers).durability(500);
    }

    public PeditoStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.PEDITO_STAFF, SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        player.getCooldowns().addCooldown(itemStack, 40);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            Vec3 eyePosition = player.getEyePosition();
            Vec3 lookVector = player.getViewVector(1.0F);
            double reach = 25.0;
            Vec3 endPosition = eyePosition.add(lookVector.scale(reach));

            // Hurt durability
            itemStack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            AABB searchBox = player.getBoundingBox().expandTowards(lookVector.scale(reach)).inflate(1.0D);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyePosition, endPosition, searchBox,
                    entity -> !entity.isSpectator() && entity.isPickable() && entity != player, reach * reach);
                    
            BlockHitResult blockHit = level.clip(new ClipContext(eyePosition, endPosition, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

            Vec3 hitPos = endPosition;
            LivingEntity hitLiving = null;

            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
                if (blockHit.getType() == HitResult.Type.MISS || eyePosition.distanceTo(entityHit.getLocation()) < eyePosition.distanceTo(blockHit.getLocation())) {
                    hitPos = entityHit.getLocation();
                    hitLiving = living;
                } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                    hitPos = blockHit.getLocation();
                }
            } else if (blockHit.getType() == HitResult.Type.BLOCK) {
                hitPos = blockHit.getLocation();
            }

            // Draw line of particles
            double distance = eyePosition.distanceTo(hitPos);
            for (double i = 0; i < distance; i += 0.5) {
                Vec3 particlePos = eyePosition.add(lookVector.scale(i));
                serverLevel.sendParticles(ParticleTypes.END_ROD, particlePos.x, particlePos.y, particlePos.z,
                        1, 0, 0, 0, 0.0);
            }

            if (hitLiving != null) {
                // Damage target
                hitLiving.hurt(serverLevel.damageSources().magic(), 6.0F);
                
                // Big particle explosion
                serverLevel.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z,
                        2, 0.5, 0.5, 0.5, 0.1);

                // Command Peditos
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, player.getBoundingBox().inflate(128.0D));
                for (PeditoEntity pedito : peditos) {
                    if (pedito.isTamedByOwner() && pedito.isOwnerCustom(player)) {
                        pedito.setSittingCustom(false); // Stand up / force unfreeze!
                        pedito.setTarget(hitLiving);
                        if (pedito.distanceToSqr(player) > 1600.0D) pedito.teleportTo(player.getX(), player.getY(), player.getZ());
                    }
                }
            } else {
                // Command peditos to area (we can just damage everything around hit block and make peditos target them)
                serverLevel.sendParticles(ParticleTypes.SONIC_BOOM, hitPos.x, hitPos.y + 0.5, hitPos.z,
                        1, 0, 0, 0, 0);
                        
                AABB areaOfEffect = new AABB(hitPos.subtract(3, 3, 3), hitPos.add(3, 3, 3));
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, areaOfEffect, e -> e != player && !(e instanceof PeditoEntity) && e.isAlive());
                
                for (LivingEntity t : targets) {
                    t.hurt(serverLevel.damageSources().magic(), 4.0F);
                }
                
                List<PeditoEntity> peditos = level.getEntitiesOfClass(PeditoEntity.class, player.getBoundingBox().inflate(128.0D));
                for (PeditoEntity pedito : peditos) {
                    if (pedito.isTamedByOwner() && pedito.isOwnerCustom(player)) {
                        pedito.setSittingCustom(false); // Stand up / force unfreeze!
                        if (!targets.isEmpty()) {
                            pedito.setTarget(targets.get(level.getRandom().nextInt(targets.size())));
                        } else {
                            pedito.setTarget(null);
                        }
                        if (pedito.distanceToSqr(player) > 1600.0D) {
                            pedito.teleportTo(player.getX(), player.getY(), player.getZ());
                        }
                    }
                }
            }
        }

        return InteractionResult.SUCCESS;
    }
}
