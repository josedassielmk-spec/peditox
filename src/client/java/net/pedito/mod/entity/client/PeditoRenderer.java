package net.pedito.mod.entity.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.PoseStack;

import net.pedito.mod.Pedito;
import net.pedito.mod.entity.PeditoEntity;

public class PeditoRenderer extends MobRenderer<PeditoEntity, PeditoRenderState, PeditoModel> {
    private static final String T = "textures/entity/pedito/";
    private static final Map<String, Identifier> TEXTURE_CACHE = new HashMap<>();

    public PeditoRenderer(EntityRendererProvider.Context context) {
        super(context, new PeditoModel(context.bakeLayer(PeditoModel.LAYER)), 0.3F);
    }

    private static Identifier tex(String name) {
        return TEXTURE_CACHE.computeIfAbsent(name, k -> Identifier.fromNamespaceAndPath(Pedito.MOD_ID, T + k + ".png"));
    }

    @Override
    public PeditoRenderState createRenderState() {
        return new PeditoRenderState();
    }

    @Override
    protected void scale(PeditoRenderState state, PoseStack poseStack) {
        super.scale(state, poseStack);
        float baseScale = 0.5F;
        if (state.variant == PeditoEntity.VARIANT_ALPHA) {
            baseScale = 1.0F; // Alpha is double the size of a normal adult (1.0F vs 0.5F)
        } else if (state.isBaby) {
            baseScale *= 0.5F; // Baby is half the size of adult (0.25F)
        }
        poseStack.scale(baseScale, baseScale, baseScale);
    }

    @Override
    public void extractRenderState(PeditoEntity entity, PeditoRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant();
        state.tamedByOwner = entity.isTamedByOwner();
        state.surpriseTicks = entity.getSurpriseTicks();
        state.talkingTicks = entity.getTalkingTicks();
        state.ageInTicks = entity.tickCount;
        state.tier = entity.getTier();
        state.isBaby = entity.isBaby();
        state.isSitting = entity.isSittingCustom();
        state.isSpinning = entity.isSpinning();
        state.spinningTicks = entity.getSpinningTicks();
    }

    @Override
    protected int getModelTint(PeditoRenderState state) {
        if (state.isSpinning) {
            float progress = Math.min(state.spinningTicks / 20.0F, 1.0F);
            // Interpolate color towards solid red (0xFFFF0000)
            int r = 255;
            int g = (int) (255 * (1.0F - progress));
            int b = (int) (255 * (1.0F - progress));
            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        }
        return super.getModelTint(state);
    }

    @Override
    public Identifier getTextureLocation(PeditoRenderState state) {
        String baseTex = getBaseTextureName(state);
        if (state.variant == PeditoEntity.VARIANT_ALPHA) {
            return tex(baseTex);
        }
        if (!state.isBaby && state.tier > 0 && state.tier <= 5) {
            String[] tiers = {"", "_copper", "_iron", "_gold", "_diamond", "_netherite"};
            return tex(baseTex + tiers[state.tier]);
        }
        return tex(baseTex);
    }

    private String getBaseTextureName(PeditoRenderState state) {
        // Permitimos animaciones especiales de boca para los peditos normales y arcoíris con tier
        boolean hasDedicatedTalking = ((state.variant == PeditoEntity.VARIANT_NORMAL || (state.variant == PeditoEntity.VARIANT_RAINBOW && state.tier > 0)) && !state.isBaby);

        if (state.surpriseTicks > 0) {
            if (hasDedicatedTalking) return "mouth_hooo";
            if (state.isBaby) {
                // Animacion tematica de bebe: bostezo en vez del generico "ojos abiertos"
                return state.tamedByOwner ? "face_baby_yawn_pet" : "face_baby_yawn";
            }
            return getBlinkTexture(state.variant, state.isBaby, state.tamedByOwner, state.tier, 0);
        }

        if (state.talkingTicks > 0) {
            if (hasDedicatedTalking) {
                int frame = (int) ((state.ageInTicks / 4) % 4);
                switch (frame) {
                    case 0: return "mouth_normal";
                    case 1: return "mouth_hablando";
                    case 2: return "mouth_a";
                    default: return "mouth_o";
                }
            } else {
                int frame = (int) ((state.ageInTicks / 4) % 2);
                return getBlinkTexture(state.variant, state.isBaby, state.tamedByOwner, state.tier, frame == 0 ? 0 : 1);
            }
        }

        double lifeTimeSeconds = state.ageInTicks / 20.0;
        double mod5 = lifeTimeSeconds % 5.0;
        double mod25 = lifeTimeSeconds % 25.0;
        int blinkIndex; // 0 = open, 1 = closed, 2 = wink
        if (mod5 < 4.85) {
            blinkIndex = 0;
        } else if (mod25 < 4.97) {
            blinkIndex = 2;
        } else {
            blinkIndex = 1;
        }

        return getBlinkTexture(state.variant, state.isBaby, state.tamedByOwner, state.tier, blinkIndex);
    }

    private static String getBlinkTexture(int variant, boolean isBaby, boolean isPet, int tier, int blinkIndex) {
        if (variant == PeditoEntity.VARIANT_ALPHA) {
            return blinkIndex == 2 ? "face_alpha_wink" : blinkIndex == 1 ? "face_alpha_closed" : "face_alpha_open";
        } else if (variant == PeditoEntity.VARIANT_NIGHT) {
            if (isBaby) {
                return blinkIndex == 2 ? "face_baby_night_wink" : blinkIndex == 1 ? "face_baby_night_closed" : "face_baby_night_sad";
            } else {
                return blinkIndex == 2 ? "face_adult_night_wink" : blinkIndex == 1 ? "face_adult_night_closed" : "face_adult_night_sad";
            }
        } else if (variant == PeditoEntity.VARIANT_RAINBOW && tier == 0) {
            if (isBaby) {
                return blinkIndex == 2 ? "face_baby_rainbow_wink" : blinkIndex == 1 ? "face_baby_rainbow_closed" : "face_baby_rainbow_open";
            } else {
                return blinkIndex == 2 ? "face_rainbow_wink" : blinkIndex == 1 ? "face_rainbow_closed" : "face_rainbow_open";
            }
        } else { // Normal (Day) / Rainbow with tier
            if (isBaby) {
                if (isPet) {
                    return blinkIndex == 2 ? "face_baby_wink_pet" : blinkIndex == 1 ? "face_baby_closed_pet" : "face_baby_open_pet";
                } else {
                    return blinkIndex == 2 ? "face_baby_wink" : blinkIndex == 1 ? "face_baby_closed" : "face_baby_open";
                }
            } else {
                if (isPet) {
                    return blinkIndex == 2 ? "face_adult_wink_pet" : blinkIndex == 1 ? "face_adult_closed_pet" : "face_adult_open_pet";
                } else {
                    return blinkIndex == 2 ? "face_adult_wink" : blinkIndex == 1 ? "face_adult_closed" : "face_adult_open";
                }
            }
        }
    }
}
