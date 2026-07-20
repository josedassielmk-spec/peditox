package net.pedito.mod.entity.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Estado de renderizado propio de Pedito (patron introducido en las versiones recientes de
 * Minecraft: la logica de render ya NO puede leer la entidad directamente en getTextureLocation,
 * asi que estos datos se copian desde la entidad una vez por frame en PeditoRenderer#extractRenderState.
 */
public class PeditoRenderState extends LivingEntityRenderState {
	/** 0 = normal, 1 = noche, 2 = arcoiris. Ver PeditoEntity.VARIANT_*. */
	public int variant;
	public boolean tamedByOwner;
	public int surpriseTicks;
	public int talkingTicks;
	public long ageInTicks;
	public int tier;
	public boolean isBaby;
	public boolean isSitting;
	public boolean isSpinning;
	public int spinningTicks;
}
