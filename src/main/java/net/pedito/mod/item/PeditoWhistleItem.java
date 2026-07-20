package net.pedito.mod.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pedito.mod.entity.PeditoFormationGoal;

public class PeditoWhistleItem extends Item {

    public PeditoWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);

        // Solo permitir usar el silbato desde la mano principal (MAIN_HAND)
        if (usedHand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            java.util.UUID uuid = player.getUUID();
            int current = PeditoFormationGoal.getPlayerFormation(uuid);
            int next = (current + 1) % PeditoFormationGoal.FormationType.values().length;
            PeditoFormationGoal.setPlayerFormation(uuid, next);

            PeditoFormationGoal.FormationType type = PeditoFormationGoal.FormationType.byId(next);

            // Tono dinámico y lógico para cada formación militar
            float pitch = 1.0F;
            switch (type) {
                case DEFAULT: pitch = 1.0F; break;
                case COLUMN: pitch = 1.15F; break;
                case LINE: pitch = 1.3F; break;
                case V_SHAPE: pitch = 1.45F; break;
                case BOX: pitch = 1.6F; break;
                case CIRCLE: pitch = 1.75F; break;
                case DIAGONAL: pitch = 1.9F; break;
            }

            // Sonido de silbato usando NOTE_BLOCK_FLUTE con tono dinámico
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.5F, pitch);
            
            // Efecto de partículas alrededor del jugador
            if (level instanceof ServerLevel serverLevel) {
                for (int i = 0; i < 20; i++) {
                    double theta = level.getRandom().nextDouble() * 2 * Math.PI;
                    double radius = 1.0 + level.getRandom().nextDouble() * 1.5;
                    double x = player.getX() + Math.cos(theta) * radius;
                    double z = player.getZ() + Math.sin(theta) * radius;
                    double y = player.getY() + 0.2 + level.getRandom().nextDouble() * 1.2;
                    serverLevel.sendParticles(new DustParticleOptions(0x32CD32, 1.0F), x, y, z, 1, 0, 0, 0, 0);
                    serverLevel.sendParticles(new DustParticleOptions(0x00FFFF, 1.0F), x, y, z, 1, 0, 0, 0, 0);
                }
            }

            // Mostrar la nueva formación en la action bar en español e inglés
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e§l[Pedito] §fFormación Militar: §a" + type.getNameEs() + " §7/ §b" + type.getNameEn()), true);
            }
        }
        player.getCooldowns().addCooldown(itemStack, 15); // Pequeño cooldown de 0.75s
        return InteractionResult.SUCCESS;
    }
}
