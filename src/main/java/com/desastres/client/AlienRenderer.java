package com.desastres.client;

import com.desastres.DisastersMod;
import com.desastres.entity.AlienEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AlienRenderer extends MobRenderer<AlienEntity, AlienModel> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(DisastersMod.MODID, "textures/entity/alien.png");

    public AlienRenderer(EntityRendererProvider.Context context) {
        super(context, new AlienModel(context.bakeLayer(AlienModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(AlienEntity entity) {
        return TEXTURE;
    }
}
