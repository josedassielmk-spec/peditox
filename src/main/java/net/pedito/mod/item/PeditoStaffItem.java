// Updated to include deployment logic, correct sounds, and AABB
package net.pedito.mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.server.level.ServerLevel;
import net.pedito.mod.Pedito;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModSounds;
import net.pedito.mod.registry.ModBlocks;
import net.pedito.mod.block.entity.PeditoChestBlockEntity;

import java.util.List;

public class PeditoStaffItem extends Item {

    public static Item.Properties createProperties() {
        ItemAttributeModifiers modifiers = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(Pedito.MOD_ID, "staff_damage"), 7.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(Pedito.MOD_ID, "staff_speed"), -2.4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build();
        return new Item.Properties().attributes(modifiers).durability(500);
    }

    public PeditoStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        player.getCooldowns().addCooldown(itemStack, 40);

        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            Vec3 eyePosition = player.getEyePosition();
            Vec3 lookVector = player.getViewVector(1.0F);
            double reach = 25.0;
            Vec3 endPosition = eyePosition.add(lookVector.scale(reach));

            itemStack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

            BlockHitResult blockHit = level.clip(new ClipContext(eyePosition, endPosition, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = blockHit.getBlockPos();
                if (level.getBlockState(pos).is(ModBlocks.PEDITO_CHEST)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof PeditoChestBlockEntity chest) {
                        
                        // 6.4 Lógica de Interacción
                        if (player.isCrouching()) {
                            // Fase de Despliegue (Release)
                            boolean released = chest.releaseLastEntity(level, pos);
                            if (released) {
                                level.playSound(null, pos, ModSounds.PEDITO_SUMMON, SoundSource.BLOCKS, 1.0f, 1.0f);
                                serverLevel.sendParticles(ParticleTypes.GLOW, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.1);
                                chest.setOpen(true);
                            } else {
                                level.playSound(null, pos, ModSounds.PEDITO_PUPU, SoundSource.BLOCKS, 1.0f, 1.0f);
                            }
                        } else {
                            // Fase de Captura (Global Suction Algorithm R=128.0)
                            List<PeditoEntity> nearPeditos = level.getEntitiesOfClass(PeditoEntity.class, new AABB(pos).inflate(128.0),
                                    p -> p.isTamedByOwner() && p.isOwnerCustom(player));

                            if (!nearPeditos.isEmpty()) {
                                PeditoEntity p = nearPeditos.get(0);
                                if (chest.storePedito(p)) {
                                    p.discard();
                                    level.playSound(null, pos, ModSounds.PEDITO_FART_SPAWN_2, SoundSource.BLOCKS, 1.0f, 1.0f);
                                    serverLevel.sendParticles(ParticleTypes.GLOW, p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                                    chest.setOpen(true);
                                }
                            } else {
                                // Error/Invalido
                                level.playSound(null, pos, ModSounds.PEDITO_PUPU, SoundSource.BLOCKS, 1.0f, 1.0f);
                            }
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
            }
            
            // ... (Resto del código de ataque ofensivo) ...
        }
        return InteractionResult.SUCCESS;
    }
}
