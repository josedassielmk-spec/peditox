package net.pedito.mod.entity.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Modelo de Pedito - cubo real (6 caras, con profundidad).
 *
 * Diagnostico del bug anterior: se intento un cubo real (depth=CUBE_SIZE) con texOffs(0,0),
 * pero LayerDefinition.create(...) declaraba un atlas de CUBE_SIZE x CUBE_SIZE (8x8). El
 * mapeo UV estandar de Minecraft para una caja de 8x8x8 necesita un atlas de 32x16 (4x el
 * ancho, 2x el alto de una cara) para tener sitio para las 6 caras. Con un atlas declarado
 * de solo 8x8, la cara frontal terminaba leyendo un rectangulo fuera de ese espacio 8x8 (que
 * en el PNG real de 256x128 caia en una zona transparente) -> alfa 0 en todas las caras, sin
 * ningun error. De ahi el "cubo invisible" y el revert a una lamina plana.
 *
 * Solucion real: profundidad real (CUBE_SIZE) + atlas declarado de 32x16 (el tamano correcto
 * para esta caja segun el estandar de Minecraft). Usamos directamente las texturas en
 * textures/entity/pedito/ que ya venian formateadas como atlas de Minecraft en alta
 * resolucion (64x32), lo que permite que el mapeo sea HD y perfecto sin necesidad
 * de regenerar _cube.png.
 */
public class PeditoModel extends EntityModel<PeditoRenderState> {

	public static final ModelLayerLocation LAYER =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath("pedito", "pedito"), "main");

	/** Ancho, alto y profundidad del cubo (un bloque entero para coincidir con el mapeo UV del atlas 64x32). */
	private static final float CUBE_SIZE = 16.0F;

	/** Atlas box-UV estandar de Minecraft para una caja CUBE_SIZE x CUBE_SIZE x CUBE_SIZE. */
	private static final int ATLAS_WIDTH = 64;
	private static final int ATLAS_HEIGHT = 32;

	private final ModelPart body;

	private boolean isSpinning;
	private int spinningTicks;

	public PeditoModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
	}

	public static LayerDefinition getTexturedModelData() {
		MeshDefinition modelData = new MeshDefinition();
		PartDefinition root = modelData.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create()
						.texOffs(0, 0)
						.addBox(-CUBE_SIZE / 2.0F, -CUBE_SIZE, -CUBE_SIZE / 2.0F, CUBE_SIZE, CUBE_SIZE, CUBE_SIZE),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		return LayerDefinition.create(modelData, ATLAS_WIDTH, ATLAS_HEIGHT);
	}

	@Override
	public void setupAnim(PeditoRenderState state) {
		super.setupAnim(state);
		this.body.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.body.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.isSpinning = state.isSpinning;
		this.spinningTicks = state.spinningTicks;
		if (state.isSpinning) {
			this.body.zRot = state.spinningTicks * 0.8F;
		} else {
			this.body.zRot = 0.0F;
		}
	}
}
