package net.pedito.mod.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.pedito.mod.Pedito;
import net.pedito.mod.entity.PeditoEntity;

/**
 * Equivalente al item "pedito:pedito_bottle" de Bedrock (minecraft:entity_placer).
 * Al usarlo sobre un bloque, coloca un Pedito adulto encima.
 */
public class PeditoBottleItem extends Item {

	public PeditoBottleItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getLevel() instanceof ServerLevel serverLevel) {
			BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
			Entity pedito = Pedito.PEDITO_ENTITY.spawn(serverLevel, pos, EntitySpawnReason.SPAWN_ITEM_USE);
			
			if (pedito != null) {
				if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
					context.getItemInHand().shrink(1);
				}
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}
}
