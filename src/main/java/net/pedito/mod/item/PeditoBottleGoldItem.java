package net.pedito.mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.pedito.mod.Pedito;
import net.pedito.mod.entity.PeditoEntity;
import net.pedito.mod.registry.ModSounds;

public class PeditoBottleGoldItem extends Item {

	public PeditoBottleGoldItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel() instanceof ServerLevel serverLevel) {
			BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
			Entity spawned = Pedito.PEDITO_ENTITY.spawn(serverLevel, pos, EntitySpawnReason.SPAWN_ITEM_USE);
			
			if (spawned instanceof PeditoEntity pedito) {
				pedito.setVariant(PeditoEntity.VARIANT_GOLDEN);

				if (context.getPlayer() != null) {
					pedito.setOwnerCustom(context.getPlayer());
					pedito.setSittingCustom(false);
					pedito.level().broadcastEntityEvent(pedito, (byte) 7);
					serverLevel.sendParticles(ParticleTypes.PORTAL, pedito.getX(), pedito.getY() + 0.5, pedito.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
					serverLevel.playSound(null, pedito.getX(), pedito.getY(), pedito.getZ(), ModSounds.PEDITO_FART, SoundSource.NEUTRAL, 1.0F, 1.2F);
					if (!context.getPlayer().getAbilities().instabuild) {
						context.getItemInHand().shrink(1);
					}
				}
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}
}
