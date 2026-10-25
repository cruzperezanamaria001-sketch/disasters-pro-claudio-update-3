package com.desastres.client;

import com.desastres.DisastersMod;
import com.desastres.config.DisasterClientConfig;
import com.desastres.entity.DisasterVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vibracion de camara (solo cliente). Una vez por tick busca entre las entidades visuales cercanas la que
 * mas deberia sacudir la camara (Marte acercandose, grandes ondas expansivas, huracanes enormes) y la
 * suaviza; el evento de camara solo aplica ese valor, asi no hay trabajo pesado por fotograma.
 */
@Mod.EventBusSubscriber(modid = DisastersMod.MODID, value = Dist.CLIENT)
public final class CameraShakeHandler {
    private static float target = 0.0F;
    private static float shake = 0.0F;

    private CameraShakeHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (mc.level == null || player == null || !DisasterClientConfig.CAMERA_SHAKE.get()) {
            target = 0.0F;
            shake = 0.0F;
            return;
        }
        float best = 0.0F;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof DisasterVisualEntity v)) {
                continue;
            }
            double dist = Math.sqrt(v.distanceToSqr(player));
            float s = 0.0F;
            switch (v.getKind()) {
                case MARS: {
                    float p = Mth.clamp(v.getS2(), 0.0F, 1.0F);
                    s = p * p * (float) Mth.clamp(1.0D - dist / 700.0D, 0.0D, 1.0D);
                    break;
                }
                case SHOCKWAVE: {
                    // Grandes explosiones (impacto de Marte, explosion nuclear); el cometa y la ola de frio no sacuden
                    if (v.getS1() > 25.0F && (v.getS3() < 0.5F || (v.getS3() >= 1.5F && v.getS3() < 2.5F))) {
                        float fade = Mth.clamp(v.getS2(), 0.0F, 1.0F);
                        s = fade * (float) Mth.clamp(1.0D - dist / (v.getS1() * 1.5D + 40.0D), 0.0D, 1.0D);
                    }
                    break;
                }
                case TORNADO: {
                    // Embudos gigantes (huracan)
                    if (v.getS3() > 20.0F) {
                        s = 0.35F * (float) Mth.clamp(1.0D - dist / (v.getS3() * 2.5D), 0.0D, 1.0D);
                    }
                    break;
                }
                default:
                    break;
            }
            if (s > best) {
                best = s;
            }
        }
        target = best;
        shake += (target - shake) * 0.25F;
        if (shake < 0.001F) {
            shake = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shake <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float amp = shake * DisasterClientConfig.CAMERA_SHAKE_STRENGTH.get().floatValue();
        float t = mc.player.tickCount + (float) event.getPartialTick();
        event.setPitch(event.getPitch() + Mth.sin(t * 1.9F) * 1.3F * amp);
        event.setYaw(event.getYaw() + Mth.sin(t * 2.3F + 1.3F) * 1.3F * amp);
        event.setRoll(event.getRoll() + Mth.sin(t * 3.1F) * 2.2F * amp);
    }
}
