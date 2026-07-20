package net.pedito.mod.entity.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.pedito.mod.Pedito;
import net.pedito.mod.block.PeditoChestBlock;
import net.pedito.mod.block.entity.PeditoChestBlockEntity;

public class PeditoChestBlockEntityRenderer implements BlockEntityRenderer<PeditoChestBlockEntity> {
    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;
    
    // Matriz de textura especificada en doc técnica
    public static final Material TEXTURE = new Material(InventoryMenu.BLOCK_ATLAS, 
            ResourceLocation.fromNamespaceAndPath(Pedito.MOD_ID, "item/chest_legendary_crystal_64x48"));

    public PeditoChestBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart modelPart = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = modelPart.getChild("bottom");
        this.lid = modelPart.getChild("lid");
        this.lock = modelPart.getChild("lock");
    }

    @Override
    public void render(PeditoChestBlockEntity entity, float tickDelta, PoseStack poses, MultiBufferSource buffer, int light, int overlay) {
        poses.pushPose();
        
        BlockState state = entity.getBlockState();
        float rotation = state.hasProperty(PeditoChestBlock.FACING) ? state.getValue(PeditoChestBlock.FACING).toYRot() : 0;
        
        poses.translate(0.5, 0.5, 0.5);
        poses.mulPose(Axis.YP.rotationDegrees(-rotation));
        poses.translate(-0.5, -0.5, -0.5);
        
        // 6.6 Animación y Visuales: Curva f(t) = sin(pi * t)
        float progress = entity.getOpenNess(tickDelta);
        float animatedProgress = (float) Math.sin(Math.PI * progress);
        
        // Interpolación lineal (lerp) estado cerrado (0) y abierto (120 grados)
        float angle = animatedProgress * 120.0f;
        this.lid.xRot = -(angle * ((float)Math.PI / 180F));
        this.lock.xRot = this.lid.xRot;
        
        var vertexConsumer = TEXTURE.buffer(buffer, RenderType::entityCutout);
        this.lid.render(poses, vertexConsumer, light, overlay);
        this.lock.render(poses, vertexConsumer, light, overlay);
        this.bottom.render(poses, vertexConsumer, light, overlay);
        
        poses.popPose();
    }
}
