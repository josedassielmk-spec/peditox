package net.pedito.mod.entity.client.golem;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

public class PeditoGolemRenderer extends IronGolemRenderer {
    private static final Identifier PEDITO_GOLEM_LOCATION = Identifier.fromNamespaceAndPath("pedito", "textures/entity/golem/pedito_golem.png");

    public PeditoGolemRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(IronGolemRenderState ironGolemRenderState) {
        return PEDITO_GOLEM_LOCATION;
    }
}
