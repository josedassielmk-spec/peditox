package net.pedito.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.pedito.mod.entity.client.PeditoModel;
import net.pedito.mod.entity.client.PeditoRenderer;
import net.pedito.mod.entity.client.block.PeditoChestBlockEntityRenderer;
import net.pedito.mod.registry.ModBlockEntities;
import net.pedito.mod.entity.client.golem.PeditoGolemRenderer;

public class PeditoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(PeditoModel.LAYER, PeditoModel::getTexturedModelData);
		EntityRendererRegistry.register(Pedito.PEDITO_ENTITY, PeditoRenderer::new);
		EntityRendererRegistry.register(Pedito.PEDITO_GOLEM_ENTITY, PeditoGolemRenderer::new);
		
		BlockEntityRendererRegistry.register(ModBlockEntities.PEDITO_CHEST_BE, PeditoChestBlockEntityRenderer::new);
	}
}
