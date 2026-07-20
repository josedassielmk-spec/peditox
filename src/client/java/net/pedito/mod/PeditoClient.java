package net.pedito.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.pedito.mod.entity.client.PeditoModel;
import net.pedito.mod.entity.client.PeditoRenderer;

import net.pedito.mod.entity.client.golem.PeditoGolemRenderer;

public class PeditoClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(PeditoModel.LAYER, PeditoModel::getTexturedModelData);
		EntityRendererRegistry.register(Pedito.PEDITO_ENTITY, PeditoRenderer::new);
		EntityRendererRegistry.register(Pedito.PEDITO_GOLEM_ENTITY, PeditoGolemRenderer::new);
	}
}
