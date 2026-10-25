package com.desastres.client;

import com.desastres.DisastersMod;
import com.desastres.entity.DisasterVisualEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.util.Random;

/**
 * Dibuja los desastres como geometria 3D real (esferas, discos, embudos, olas...).
 * IMPORTANTE: cada getBuffer() devuelve un builder que deja de ser valido cuando se pide
 * otro RenderType distinto, por eso se vuelve a pedir justo antes de dibujar cada pieza.
 */
@OnlyIn(Dist.CLIENT)
public class DisasterVisualRenderer extends EntityRenderer<DisasterVisualEntity> {
    private static final float TAU = (float) (Math.PI * 2.0D);
    private static final int FULL = LightTexture.FULL_BRIGHT;

    private static final ResourceLocation WHITE = tex("white");
    private static final ResourceLocation DISK = tex("accretion_disk");
    private static final ResourceLocation TORNADO = tex("tornado");
    private static final ResourceLocation WAVE = tex("wave");
    private static final ResourceLocation METEOR = tex("meteor");
    private static final ResourceLocation CLOUD = tex("cloud");
    private static final ResourceLocation MARS = tex("mars");

    // Roca irregular del meteorito (desplazamientos fijos)
    private static final int ROCK_ST = 10;
    private static final int ROCK_SL = 14;
    private static final float[][] ROCK = new float[ROCK_ST + 1][ROCK_SL + 1];

    static {
        Random rnd = new Random(1337L);
        for (int i = 0; i <= ROCK_ST; i++) {
            for (int j = 0; j < ROCK_SL; j++) {
                ROCK[i][j] = 0.80F + rnd.nextFloat() * 0.40F;
            }
            ROCK[i][ROCK_SL] = ROCK[i][0];
        }
        for (int j = 0; j <= ROCK_SL; j++) {
            ROCK[0][j] = ROCK[0][0];
            ROCK[ROCK_ST][j] = ROCK[ROCK_ST][0];
        }
    }

    public DisasterVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(DisastersMod.MODID, "textures/entity/" + name + ".png");
    }

    @Override
    public ResourceLocation getTextureLocation(DisasterVisualEntity entity) {
        return WHITE;
    }

    @Override
    public boolean shouldRender(DisasterVisualEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true; // son enormes: nunca los recortamos por frustum
    }

    @Override
    public void render(DisasterVisualEntity e, float entityYaw, float partialTick, PoseStack ps,
                       MultiBufferSource buf, int light) {
        float time = e.tickCount + partialTick;
        switch (e.getKind()) {
            case BLACK_HOLE:
                renderBlackHole(e, time, ps, buf);
                break;
            case TORNADO:
                renderTornado(e, time, ps, buf, light);
                break;
            case TSUNAMI:
                renderTsunami(e, time, ps, buf, light);
                break;
            case METEOR:
                renderMeteor(e, time, ps, buf);
                break;
            case STORM_CLOUD:
                renderCloud(e, time, ps, buf, light);
                break;
            case ASH_PLUME:
                renderPlume(e, time, ps, buf, light);
                break;
            case SHOCKWAVE:
                renderShockwave(e, time, ps, buf);
                break;
            case COMET:
                renderComet(e, time, ps, buf);
                break;
            case WORMHOLE:
                renderWormhole(e, time, ps, buf);
                break;
            case HYP_BLACK_HOLE:
                renderHypotheticalBlackHole(e, time, ps, buf);
                break;
            case WHITE_HOLE:
                renderWhiteHole(e, time, ps, buf);
                break;
            case MARS:
                renderMars(e, time, ps, buf);
                break;
            case UFO:
                renderUfo(e, time, ps, buf);
                break;
            case LASER_BEAM:
                renderLaser(e, time, ps, buf);
                break;
            case HEAT_SKY:
                renderHeatSky(e, time, ps, buf);
                break;
            default:
                break;
        }
    }

    // =====================================================================================
    // AGUJERO NEGRO
    // =====================================================================================
    private void renderBlackHole(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float core = Math.max(0.05F, e.getS1());
        float outer = Math.max(core * 1.6F, e.getS2());

        // Horizonte de sucesos: esfera totalmente negra
        sphere(buf.getBuffer(RenderType.entityCutoutNoCull(WHITE)), ps.last(),
                0, 0, 0, core, core, core, 18, 28, 0, 0, 0, 255, FULL);

        // Halo violeta alrededor (capas translucidas)
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, core * 1.10F, core * 1.10F, core * 1.10F, 12, 20, 120, 60, 200, 70, FULL);
        sphere(glass, ps.last(), 0, 0, 0, core * 1.35F, core * 1.35F, core * 1.35F, 12, 20, 90, 30, 160, 36, FULL);
        sphere(glass, ps.last(), 0, 0, 0, core * 1.90F, core * 1.90F, core * 1.90F, 10, 16, 50, 10, 110, 18, FULL);

        // Discos de acrecion inclinados y girando a distinta velocidad
        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(14.0F));
        ps.mulPose(Axis.XP.rotationDegrees(22.0F));

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 7.0F));
        annulus(disk, ps.last(), core * 1.2F, outer, 64, 255, 255, 255, 235, FULL);
        ps.popPose();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-time * 11.0F));
        ps.translate(0.0F, core * 0.08F, 0.0F);
        annulus(disk, ps.last(), core * 1.4F, outer * 0.75F, 64, 255, 190, 140, 150, FULL);
        ps.popPose();
        ps.popPose();

        // Anillo fotonico (siempre mira a la camara): efecto de lente gravitacional
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        annulus(disk, ps.last(), core * 1.0F, core * 1.6F, 64, 255, 235, 200, 230, FULL);
        ps.popPose();
    }

    // =====================================================================================
    // TORNADO
    // =====================================================================================
    private void renderTornado(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf, int light) {
        float h = e.getS1();
        float rb = e.getS2();
        float rt = e.getS3();
        if (h < 0.5F) {
            return;
        }
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TORNADO));
        for (int layer = 0; layer < 3; layer++) {
            ps.pushPose();
            float dir = (layer % 2 == 0) ? 1.0F : -1.0F;
            ps.mulPose(Axis.YP.rotationDegrees(dir * time * (16.0F + 10.0F * layer)));
            float sc = 1.0F - 0.24F * layer;
            funnel(vc, ps.last(), h, rb * sc, rt * sc, 28, 22, time, layer, 105 + 35 * layer, light);
            ps.popPose();
        }
    }

    private static void funnel(VertexConsumer vc, PoseStack.Pose p, float h, float rb, float rt, int seg, int rings,
                               float time, int layer, int alpha, int light) {
        for (int i = 0; i < rings; i++) {
            float t0 = i / (float) rings;
            float t1 = (i + 1) / (float) rings;
            for (int j = 0; j < seg; j++) {
                float a0 = TAU * j / seg;
                float a1 = TAU * (j + 1) / seg;
                float u0 = 2.0F * j / seg + time * 0.012F * (layer + 1);
                float u1 = 2.0F * (j + 1) / seg + time * 0.012F * (layer + 1);
                funnelVertex(vc, p, h, rb, rt, t0, a0, u0, time, layer, alpha, light);
                funnelVertex(vc, p, h, rb, rt, t1, a0, u0, time, layer, alpha, light);
                funnelVertex(vc, p, h, rb, rt, t1, a1, u1, time, layer, alpha, light);
                funnelVertex(vc, p, h, rb, rt, t0, a1, u1, time, layer, alpha, light);
            }
        }
    }

    private static void funnelVertex(VertexConsumer vc, PoseStack.Pose p, float h, float rb, float rt, float t,
                                     float angle, float u, float time, int layer, int alpha, int light) {
        float radius = rb + (rt - rb) * (float) Math.pow(t, 1.3D);
        float wx = Mth.sin(t * 5.0F + time * 0.12F + layer * 1.7F) * 1.2F * t;
        float wz = Mth.cos(t * 4.3F + time * 0.10F + layer) * 1.2F * t;
        float cs = Mth.cos(angle);
        float sn = Mth.sin(angle);
        float x = wx + radius * cs;
        float z = wz + radius * sn;
        float y = t * h;
        float shade = (float) Math.pow(t, 0.6D);
        int r = (int) Mth.lerp(shade, 105.0F, 215.0F);
        int g = (int) Mth.lerp(shade, 92.0F, 215.0F);
        int b = (int) Mth.lerp(shade, 75.0F, 222.0F);
        float s = Mth.clamp((t - 0.82F) / 0.18F, 0.0F, 1.0F);
        float fade = 1.0F - s * s * (3.0F - 2.0F * s);
        vtx(vc, p, x, y, z, u, t * 3.0F - time * 0.02F, r, g, b, (int) (alpha * fade), light, cs, 0.0F, sn);
    }

    // =====================================================================================
    // TSUNAMI
    // =====================================================================================
    private void renderTsunami(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf, int light) {
        float hw = e.getS1();
        float height = e.getS2();
        float body = e.getS3();
        float dx = e.getDirX();
        float dz = e.getDirZ();
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4F) {
            return;
        }
        dx /= len;
        dz /= len;

        float tilt = e.getDirY(); // inclinacion lateral del terreno (altura por bloque)
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation((float) Math.atan2(dx, dz)));

        // Perfil de la ola (z = adelante, y = fraccion de altura): rampa trasera, cresta y curl frontal
        float[] pz = {-body * 1.6F, -body * 1.1F, -body * 0.6F, -0.6F, 0.9F, 1.9F, 2.3F, 2.0F, 1.6F};
        float[] py = {0.0F, 0.28F, 0.62F, 0.92F, 1.0F, 0.82F, 0.5F, 0.2F, 0.0F};

        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(WAVE));
        float step = 1.6F;
        int segs = Math.max(2, (int) (2.0F * hw / step));
        for (int i = 0; i < segs; i++) {
            float x0 = -hw + 2.0F * hw * i / segs;
            float x1 = -hw + 2.0F * hw * (i + 1) / segs;
            float h0 = waveHeight(x0, hw, time) * height;
            float h1 = waveHeight(x1, hw, time) * height;
            float b0 = x0 * tilt;
            float b1 = x1 * tilt;
            for (int k = 0; k < pz.length - 1; k++) {
                waveVertex(vc, p(ps), x0, h0, b0, pz, py, k, time, light);
                waveVertex(vc, p(ps), x1, h1, b1, pz, py, k, time, light);
                waveVertex(vc, p(ps), x1, h1, b1, pz, py, k + 1, time, light);
                waveVertex(vc, p(ps), x0, h0, b0, pz, py, k + 1, time, light);
            }
        }
        ps.popPose();
    }

    private static PoseStack.Pose p(PoseStack ps) {
        return ps.last();
    }

    private static float waveHeight(float x, float hw, float time) {
        float taper = 0.35F + 0.65F * Mth.clamp((1.0F - Math.abs(x) / hw) * 4.0F, 0.0F, 1.0F);
        float ripple = 0.88F + 0.12F * Mth.sin(x * 0.35F + time * 0.07F) + 0.06F * Mth.sin(x * 1.1F - time * 0.13F);
        return taper * ripple;
    }

    private static void waveVertex(VertexConsumer vc, PoseStack.Pose p, float x, float h, float base, float[] pz, float[] py,
                                   int k, float time, int light) {
        float t = py[k];
        float wob = (k > 0 && k < pz.length - 1) ? Mth.sin(x * 0.6F + time * 0.2F + k * 1.3F) * 0.22F : 0.0F;
        float foam = (k >= 3 && k <= 6) ? 0.55F : 0.0F;
        int r = (int) Mth.lerp(foam, Mth.lerp(t * t, 25.0F, 200.0F), 255.0F);
        int g = (int) Mth.lerp(foam, Mth.lerp(t, 95.0F, 235.0F), 255.0F);
        int b = (int) Mth.lerp(foam, Mth.lerp(t, 160.0F, 255.0F), 255.0F);
        int a = (k == 0 || k == pz.length - 1) ? 90 : 225;
        vtx(vc, p, x, base + t * h, pz[k] + wob, x * 0.15F + time * 0.004F, k * 0.25F - time * 0.03F,
                r, g, b, a, light, 0.0F, 0.5F, 0.5F);
    }

    // =====================================================================================
    // METEORITO
    // =====================================================================================
    private void renderMeteor(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(0.15F, e.getS1());
        float dx = e.getDirX();
        float dy = e.getDirY();
        float dz = e.getDirZ();
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            dx = 0.0F;
            dy = -1.0F;
            dz = 0.0F;
        }

        // Estilo 1 = bola de granizo: solo un trozo de hielo girando (barato de dibujar)
        if ((int) e.getS2() == 1) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(time * 31.0F));
            ps.mulPose(Axis.XP.rotationDegrees(time * 23.0F));
            rock(buf.getBuffer(RenderType.entityCutoutNoCull(WHITE)), ps.last(), r, 200, 230, 255, FULL);
            ps.popPose();
            return;
        }

        // Estela de fuego (cono hacia atras respecto a la direccion de caida)
        ps.pushPose();
        ps.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, dx, dy, dz));
        VertexConsumer trail = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        float tl = r * 10.0F;
        tube(trail, ps.last(), -r * 0.2F, -tl, r * 1.25F, 0.05F, 12, 6, 255, 140, 40, 200, 0, FULL);
        tube(trail, ps.last(), -r * 0.2F, -tl * 0.65F, r * 0.8F, 0.05F, 12, 6, 255, 230, 150, 220, 0, FULL);
        ps.popPose();

        // Roca irregular girando
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 23.0F));
        ps.mulPose(Axis.XP.rotationDegrees(time * 17.0F));
        rock(buf.getBuffer(RenderType.entityCutoutNoCull(METEOR)), ps.last(), r, 255, 255, 255, FULL);
        ps.popPose();

        // Resplandor incandescente
        VertexConsumer glow = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glow, ps.last(), 0, 0, 0, r * 1.3F, r * 1.3F, r * 1.3F, 10, 14, 255, 150, 50, 110, FULL);
        sphere(glow, ps.last(), 0, 0, 0, r * 1.8F, r * 1.8F, r * 1.8F, 8, 12, 255, 100, 20, 50, FULL);
    }

    private static void rock(VertexConsumer vc, PoseStack.Pose p, float r, int cr, int cg, int cb, int light) {
        for (int i = 0; i < ROCK_ST; i++) {
            for (int j = 0; j < ROCK_SL; j++) {
                rockVertex(vc, p, r, i, j, cr, cg, cb, light);
                rockVertex(vc, p, r, i + 1, j, cr, cg, cb, light);
                rockVertex(vc, p, r, i + 1, j + 1, cr, cg, cb, light);
                rockVertex(vc, p, r, i, j + 1, cr, cg, cb, light);
            }
        }
    }

    private static void rockVertex(VertexConsumer vc, PoseStack.Pose p, float r, int i, int j, int cr, int cg, int cb, int light) {
        float theta = (float) Math.PI * i / ROCK_ST;
        float phi = TAU * j / ROCK_SL;
        float sx = Mth.sin(theta) * Mth.cos(phi);
        float sy = Mth.cos(theta);
        float sz = Mth.sin(theta) * Mth.sin(phi);
        float rad = r * ROCK[i][j];
        vtx(vc, p, sx * rad, sy * rad, sz * rad, 2.0F * j / ROCK_SL, 2.0F * i / ROCK_ST,
                cr, cg, cb, 255, light, sx, sy, sz);
    }

    // =====================================================================================
    // NUBE DE TORMENTA / VENTISCA
    // =====================================================================================
    private static final int PUFFS = 18;

    private void renderCloud(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf, int light) {
        float rr = e.getS1();
        float thick = e.getS2();
        float white = e.getS3();
        int cr = (int) Mth.lerp(white, 58.0F, 232.0F);
        int cg = (int) Mth.lerp(white, 60.0F, 236.0F);
        int cb = (int) Mth.lerp(white, 68.0F, 245.0F);
        if (white >= 2.0F) { // nube de brasas (lluvia de fuego)
            cr = 120;
            cg = 42;
            cb = 28;
        }

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.08F));
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(CLOUD));
        for (int i = 0; i < PUFFS; i++) {
            float h1 = hash(i, 1);
            float h2 = hash(i, 2);
            float h3 = hash(i, 3);
            float h4 = hash(i, 4);
            float ang = h1 * TAU;
            float dist = rr * 0.92F * Mth.sqrt(h2);
            float edge = dist / rr;
            float cx = Mth.cos(ang) * dist;
            float cz = Mth.sin(ang) * dist;
            float cy = Mth.sin(time * 0.02F + i * 1.7F) * thick * 0.15F - edge * thick * 0.35F;
            float rx = rr * (0.20F + 0.14F * h3);
            float ry = thick * (0.45F + 0.35F * h4) * (1.0F - 0.45F * edge);
            sphere(vc, ps.last(), cx, cy, cz, rx, ry, rx * (0.8F + 0.4F * h4), 8, 12, cr, cg, cb, 215, light);
        }
        ps.popPose();
    }

    // =====================================================================================
    // COLUMNA DE CENIZA (VOLCAN)
    // =====================================================================================
    private static final int PLUME_PUFFS = 22;

    private void renderPlume(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf, int light) {
        float height = e.getS1();
        float top = e.getS2();
        boolean dust = e.getS3() < 0.0F;
        float power = Mth.clamp(Math.abs(e.getS3()), 0.0F, 1.0F);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(CLOUD));
        for (int i = 0; i < PLUME_PUFFS; i++) {
            float ti = (i / (float) PLUME_PUFFS + time * 0.0035F) % 1.0F;
            float y = ti * height * power;
            float rad = top * (0.16F + 0.84F * (float) Math.pow(ti, 0.85D)) * (0.75F + 0.5F * hash(i, 1)) * (0.4F + 0.6F * power);
            float ox = Mth.sin(i * 2.3F + time * 0.03F + ti * 3.0F) * rad * 0.45F + ti * height * 0.10F;
            float oz = Mth.cos(i * 1.9F + time * 0.025F + ti * 2.6F) * rad * 0.45F + ti * height * 0.04F;
            float glow = dust ? 0.0F : Math.max(0.0F, 1.0F - ti * 6.0F);
            int cr = (int) Mth.lerp(glow, dust ? Mth.lerp(ti, 125.0F, 165.0F) : Mth.lerp(ti, 40.0F, 120.0F), 255.0F);
            int cg = (int) Mth.lerp(glow, dust ? Mth.lerp(ti, 105.0F, 150.0F) : Mth.lerp(ti, 38.0F, 118.0F), 120.0F);
            int cb = (int) Mth.lerp(glow, dust ? Mth.lerp(ti, 82.0F, 135.0F) : Mth.lerp(ti, 40.0F, 122.0F), 40.0F);
            float fade = Mth.sin(ti * (float) Math.PI);
            int alpha = (int) (225.0F * Math.min(1.0F, fade * 2.2F));
            if (alpha <= 2) {
                continue;
            }
            sphere(vc, ps.last(), ox, y, oz, rad, rad * 0.8F, rad, 8, 12, cr, cg, cb, alpha, glow > 0.3F ? FULL : light);
        }
    }

    // =====================================================================================
    // ONDA EXPANSIVA (explosion nuclear)
    // =====================================================================================
    private void renderShockwave(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = e.getS1();
        float fade = Mth.clamp(e.getS2(), 0.0F, 1.0F);
        if (r < 0.5F || fade <= 0.01F) {
            return;
        }
        float tint = e.getS3();
        boolean cold = tint >= 2.5F;                                  // ola de congelacion
        boolean icy = cold || (tint >= 0.5F && tint < 1.5F);          // + impacto del cometa
        float sa = cold ? 0.4F : 1.0F;                                // la ola de frio es mas "fina"
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, r, r, r, 16, 28, icy ? 190 : 255, icy ? 225 : 235, icy ? 255 : 190, (int) (150 * fade * sa), FULL);
        sphere(glass, ps.last(), 0, 0, 0, r * 0.82F, r * 0.82F, r * 0.82F, 14, 24, icy ? 110 : 255, icy ? 180 : 160, icy ? 255 : 60, (int) (110 * fade * sa), FULL);
        sphere(glass, ps.last(), 0, 0, 0, r * 0.55F, r * 0.55F, r * 0.55F, 12, 20, 255, 255, 255, (int) (190 * fade * sa), FULL);

        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.translate(0.0F, 0.3F, 0.0F);
        annulus(disk, ps.last(), r * (cold ? 0.9F : 0.8F), r * 1.08F, 72, icy ? 200 : 255, icy ? 230 : 230, icy ? 255 : 200, (int) (230 * fade), FULL);
        ps.popPose();
    }

    // =====================================================================================
    // COMETA HALLEY
    // =====================================================================================
    private void renderComet(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(0.5F, e.getS1());
        float tailLen = Math.max(5.0F, e.getS2());
        float dx = e.getDirX();
        float dy = e.getDirY();
        float dz = e.getDirZ();
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) {
            dx = 0.0F;
            dy = -1.0F;
            dz = 0.0F;
        }

        // Nucleo helado, irregular, girando lentamente
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 6.0F));
        ps.mulPose(Axis.XP.rotationDegrees(time * 4.0F));
        rock(buf.getBuffer(RenderType.entityCutoutNoCull(METEOR)), ps.last(), r, 170, 205, 245, FULL);
        ps.popPose();

        // Coma (halo) y colas, todo en el mismo buffer translucente
        VertexConsumer glow = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glow, ps.last(), 0, 0, 0, r * 1.5F, r * 1.5F, r * 1.5F, 10, 16, 190, 230, 255, 120, FULL);
        sphere(glow, ps.last(), 0, 0, 0, r * 2.2F, r * 2.2F, r * 2.2F, 8, 12, 140, 200, 255, 60, FULL);
        sphere(glow, ps.last(), 0, 0, 0, r * 3.2F, r * 3.2F, r * 3.2F, 8, 12, 100, 170, 255, 28, FULL);

        ps.pushPose();
        ps.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, dx, dy, dz)); // +Z = sentido de avance
        float flicker = 1.0F + 0.04F * Mth.sin(time * 0.7F);
        // cola ionica: recta, azul, larga
        tube(glow, ps.last(), -r * 0.5F, -tailLen * flicker, r * 0.9F, r * 3.0F, 14, 10, 120, 200, 255, 170, 0, FULL);
        // cola de polvo: curvada, amarillenta, mas ancha
        tube(glow, ps.last(), -r * 0.5F, -tailLen * 0.8F, r * 1.2F, r * 4.8F, 14, 10, 255, 235, 190, 120, 0, tailLen * 0.12F, FULL);
        // nucleo brillante de la cola
        tube(glow, ps.last(), -r * 0.3F, -tailLen * 0.45F, r * 0.8F, r * 1.6F, 12, 8, 255, 255, 255, 230, 0, FULL);
        ps.popPose();
    }

    // =====================================================================================
    // AGUJERO DE GUSANO (tunel: la entrada absorbe hacia dentro, la salida expulsa hacia fuera)
    // =====================================================================================
    private static final int TUNNEL_RINGS = 9;

    private void renderWormhole(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(0.3F, e.getS1());
        boolean exit = e.getS3() >= 0.5F;
        int ar = exit ? 255 : 110;
        int ag = exit ? 170 : 90;
        int ab = exit ? 60 : 255;
        int br = exit ? 255 : 80;
        int bg = exit ? 245 : 220;
        int bb = exit ? 170 : 255;

        // aura esferica difusa
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, r * 1.25F, r * 1.25F, r * 1.25F, 10, 16, ar, ag, ab, 40, FULL);

        float depth = r * 5.0F;
        float dirSign = exit ? 1.0F : -1.0F; // entrada: los anillos se alejan (succion); salida: avanzan hacia ti
        float phase = (time * 0.03F * dirSign) % 1.0F;
        if (phase < 0.0F) {
            phase += 1.0F;
        }

        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        for (int k = 0; k < TUNNEL_RINGS; k++) {
            float f = (k / (float) TUNNEL_RINGS + phase) % 1.0F;
            float rad = r * (1.0F - 0.82F * f);
            int alpha = (int) (230.0F * (float) Math.pow(1.0F - f, 0.7D) * Math.min(1.0F, f * 8.0F + 0.15F));
            int cr = (int) Mth.lerp(f, (float) ar, (float) br);
            int cg = (int) Mth.lerp(f, (float) ag, (float) bg);
            int cb = (int) Mth.lerp(f, (float) ab, (float) bb);
            ps.pushPose();
            ps.translate(0.0F, 0.0F, -f * depth);
            ps.mulPose(Axis.ZP.rotationDegrees(time * (8.0F + k * 3.0F) * (k % 2 == 0 ? 1.0F : -1.0F)));
            ps.mulPose(Axis.XP.rotationDegrees(90.0F));
            annulus(disk, ps.last(), rad * 0.78F, rad, 40, cr, cg, cb, alpha, FULL);
            ps.popPose();
        }
        // borde brillante del portal
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(time * 14.0F * dirSign));
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        annulus(disk, ps.last(), r * 0.96F, r * 1.2F, 48, br, bg, bb, 240, FULL);
        ps.popPose();
        ps.popPose();

        // luz al final del tunel
        VertexConsumer light = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.translate(0.0F, 0.0F, -depth);
        sphere(light, ps.last(), 0, 0, 0, r * 0.16F, r * 0.16F, r * 0.16F, 8, 12, br, bg, bb, 255, FULL);
        ps.popPose();
    }

    // =====================================================================================
    // AGUJERO NEGRO HIPOTETICO (anillos frios cruzados + chorros relativistas)
    // =====================================================================================
    private void renderHypotheticalBlackHole(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float core = Math.max(0.05F, e.getS1());

        sphere(buf.getBuffer(RenderType.entityCutoutNoCull(WHITE)), ps.last(),
                0, 0, 0, core, core, core, 18, 28, 0, 0, 0, 255, FULL);

        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, core * 1.05F, core * 1.05F, core * 1.05F, 12, 20, 150, 220, 255, 70, FULL);
        sphere(glass, ps.last(), 0, 0, 0, core * 1.5F, core * 1.5F, core * 1.5F, 10, 16, 80, 140, 255, 26, FULL);

        // dos anillos de acrecion frios, casi perpendiculares, girando en sentidos opuestos
        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(72.0F));
        ps.mulPose(Axis.YP.rotationDegrees(time * 9.0F));
        annulus(disk, ps.last(), core * 1.25F, core * 3.6F, 64, 170, 210, 255, 235, FULL);
        ps.popPose();
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(-38.0F));
        ps.mulPose(Axis.XP.rotationDegrees(20.0F));
        ps.mulPose(Axis.YP.rotationDegrees(-time * 13.0F));
        annulus(disk, ps.last(), core * 1.5F, core * 3.0F, 64, 210, 190, 255, 190, FULL);
        ps.popPose();

        // chorros relativistas por los polos
        VertexConsumer jets = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        ps.pushPose();
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F)); // +Z local pasa a +Y
        tube(jets, ps.last(), core * 0.9F, core * 9.0F, core * 0.3F, core * 1.3F, 14, 8, 190, 225, 255, 200, 0, FULL);
        tube(jets, ps.last(), -core * 0.9F, -core * 9.0F, core * 0.3F, core * 1.3F, 14, 8, 190, 225, 255, 200, 0, FULL);
        ps.popPose();

        // anillo de lente gravitacional frente a la camara
        VertexConsumer lens = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        annulus(lens, ps.last(), core * 1.0F, core * 1.5F, 64, 190, 230, 255, 225, FULL);
        ps.popPose();
    }

    // =====================================================================================
    // AGUJERO BLANCO (esfera brillante que pulsa, rayos y ondas que se expanden)
    // =====================================================================================
    private void renderWhiteHole(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(0.1F, e.getS1());
        float pulse = 1.0F + 0.06F * Mth.sin(time * 0.3F);
        float rr = r * pulse;

        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, rr, rr, rr, 16, 24, 255, 255, 255, 240, FULL);
        sphere(glass, ps.last(), 0, 0, 0, rr * 1.25F, rr * 1.25F, rr * 1.25F, 12, 18, 255, 245, 200, 110, FULL);
        for (int k = 0; k < 3; k++) {
            float ph = (time * 0.02F + k / 3.0F) % 1.0F;
            float rad = r * (1.1F + 1.8F * ph);
            sphere(glass, ps.last(), 0, 0, 0, rad, rad, rad, 8, 14, 255, 240, 190, (int) (110.0F * (1.0F - ph)), FULL);
        }
        // rayos
        for (int i = 0; i < 10; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(hash(i, 1) * 360.0F + time * 0.6F));
            ps.mulPose(Axis.XP.rotationDegrees(hash(i, 2) * 180.0F - 90.0F));
            tube(glass, ps.last(), r * 0.9F, r * (3.2F + 1.5F * hash(i, 3)), r * 0.14F, r * 0.02F, 6, 3, 255, 250, 230, 210, 0, FULL);
            ps.popPose();
        }
        // ondas expansivas de cara a la camara
        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        ps.pushPose();
        ps.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        ps.mulPose(Axis.XP.rotationDegrees(90.0F));
        for (int k = 0; k < 2; k++) {
            float ph = (time * 0.025F + k * 0.5F) % 1.0F;
            float rad = r * (1.3F + 3.0F * ph);
            annulus(disk, ps.last(), rad * 0.9F, rad, 56, 255, 245, 210, (int) (200.0F * (1.0F - ph)), FULL);
        }
        ps.popPose();
    }

    // =====================================================================================
    // MARTE
    // =====================================================================================
    private void renderMars(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(1.0F, e.getS1());
        float prog = Mth.clamp(e.getS2(), 0.0F, 1.0F);
        ps.pushPose();
        ps.mulPose(Axis.ZP.rotationDegrees(25.19F));      // inclinacion axial de Marte
        ps.mulPose(Axis.YP.rotationDegrees(time * 0.35F));
        sphere(buf.getBuffer(RenderType.entityCutoutNoCull(MARS)), ps.last(), 0, 0, 0, r, r, r, 24, 40, 255, 255, 255, 255, FULL);
        ps.popPose();

        // atmosfera que se calienta (entrada en la atmosfera terrestre) segun se acerca
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, 0, 0, r * 1.03F, r * 1.03F, r * 1.03F, 14, 24, 255, 150, 100, (int) (35.0F + 90.0F * prog), FULL);
        sphere(glass, ps.last(), 0, 0, 0, r * 1.12F, r * 1.12F, r * 1.12F, 10, 16, 255, 110, 60, (int) (10.0F + 60.0F * prog * prog), FULL);
    }

    // =====================================================================================
    // OVNI
    // =====================================================================================
    private void renderUfo(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float r = Math.max(0.5F, e.getS1());
        float beamLen = e.getS2();
        float beamR = e.getS3();

        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(time * 3.0F)); // gira lentamente

        // casco metalico (superior e inferior)
        VertexConsumer solid = buf.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        sphere(solid, ps.last(), 0, r * 0.02F, 0, r, r * 0.26F, r, 14, 28, 165, 170, 182, 255, FULL);
        sphere(solid, ps.last(), 0, -r * 0.10F, 0, r * 0.8F, r * 0.22F, r * 0.8F, 10, 24, 95, 100, 115, 255, FULL);

        // cupula de cristal con el piloto, y luces del borde
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        sphere(glass, ps.last(), 0, r * 0.16F, 0, r * 0.42F, r * 0.36F, r * 0.42F, 10, 16, 140, 235, 255, 150, FULL);
        sphere(glass, ps.last(), 0, r * 0.20F, 0, r * 0.10F, r * 0.12F, r * 0.10F, 6, 10, 90, 220, 120, 210, FULL);
        for (int i = 0; i < 10; i++) {
            float a = i * TAU / 10.0F + time * 0.12F;
            int phase = ((int) (time * 0.2F) + i) % 3;
            int cr = phase == 0 ? 255 : (phase == 1 ? 255 : 80);
            int cg = phase == 0 ? 70 : (phase == 1 ? 230 : 230);
            int cb = phase == 0 ? 60 : (phase == 1 ? 80 : 255);
            sphere(glass, ps.last(), Mth.cos(a) * r * 0.93F, r * 0.02F, Mth.sin(a) * r * 0.93F,
                    r * 0.07F, r * 0.07F, r * 0.07F, 6, 8, cr, cg, cb, 255, FULL);
        }

        // aro brillante y resplandor inferior
        VertexConsumer disk = buf.getBuffer(RenderType.entityTranslucent(DISK));
        annulus(disk, ps.last(), r * 0.88F, r * 1.04F, 48, 200, 235, 255, 200, FULL);
        ps.pushPose();
        ps.translate(0.0F, -r * 0.2F, 0.0F);
        ps.mulPose(Axis.YP.rotationDegrees(-time * 7.0F));
        annulus(disk, ps.last(), r * 0.25F, r * 0.75F, 40, 120, 255, 170, 150, FULL);
        ps.popPose();

        // haz de abduccion
        if (beamLen > 1.0F && beamR > 0.5F) {
            VertexConsumer beam = buf.getBuffer(RenderType.entityTranslucent(WHITE));
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(90.0F)); // +Z local pasa a -Y
            tube(beam, ps.last(), r * 0.1F, beamLen, r * 0.35F, beamR, 20, 2, 130, 255, 170, 150, 35, FULL);
            tube(beam, ps.last(), r * 0.1F, beamLen, r * 0.2F, beamR * 0.45F, 14, 2, 255, 255, 255, 90, 15, FULL);
            ps.popPose();
            VertexConsumer rings = buf.getBuffer(RenderType.entityTranslucent(DISK));
            for (int k = 0; k < 5; k++) {
                float f = (time * 0.02F + k / 5.0F) % 1.0F;
                float rad = Mth.lerp(1.0F - f, r * 0.35F, beamR);
                ps.pushPose();
                ps.translate(0.0F, -(beamLen * (1.0F - f)), 0.0F);
                annulus(rings, ps.last(), rad * 0.88F, rad, 32, 150, 255, 190, (int) (190.0F * Mth.sin(f * (float) Math.PI)), FULL);
                ps.popPose();
            }
        }
        ps.popPose();
    }

    // =====================================================================================
    // RAYO LASER (haz corto entre dos puntos)
    // =====================================================================================
    private void renderLaser(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float len = e.getS1();
        if (len < 0.2F) {
            return;
        }
        int mode = (int) e.getS2();
        int cr = mode == 1 ? 90 : (mode == 2 ? 255 : 90);
        int cg = mode == 1 ? 175 : (mode == 2 ? 90 : 255);
        int cb = mode == 1 ? 255 : (mode == 2 ? 205 : 130);
        float fade = Mth.clamp(1.0F - time / 5.0F, 0.2F, 1.0F);

        ps.pushPose();
        ps.mulPose(new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, e.getDirX(), e.getDirY(), e.getDirZ()));
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        tube(vc, ps.last(), 0.0F, len, 0.32F, 0.32F, 6, 1, cr, cg, cb, (int) (50 * fade), (int) (50 * fade), FULL);
        tube(vc, ps.last(), 0.0F, len, 0.14F, 0.14F, 6, 1, cr, cg, cb, (int) (150 * fade), (int) (150 * fade), FULL);
        tube(vc, ps.last(), 0.0F, len, 0.05F, 0.05F, 6, 1, 255, 255, 255, (int) (255 * fade), (int) (255 * fade), FULL);
        ps.popPose();
    }

    // =====================================================================================
    // CIELO DE LA OLA DE CALOR (domo anaranjado + sol extremo)
    // =====================================================================================
    private void renderHeatSky(DisasterVisualEntity e, float time, PoseStack ps, MultiBufferSource buf) {
        float rr = Math.max(5.0F, e.getS1());
        float p = Mth.clamp(e.getS2(), 0.0F, 1.0F);
        if (p <= 0.01F) {
            return;
        }
        float pulse = 0.85F + 0.15F * Mth.sin(time * 0.05F);
        VertexConsumer glass = buf.getBuffer(RenderType.entityTranslucent(WHITE));
        // domo (se ve desde dentro): tinte anaranjado del ambiente
        sphere(glass, ps.last(), 0, 0, 0, rr, rr * 0.9F, rr, 9, 24, 255, 140, 40, (int) (34.0F * p * pulse), FULL);
        sphere(glass, ps.last(), 0, 0, 0, rr * 0.94F, rr * 0.84F, rr * 0.94F, 8, 20, 255, 200, 90, (int) (16.0F * p), FULL);

        // sol extremo
        float sr = rr * 0.11F * (0.8F + 0.2F * p);
        ps.pushPose();
        ps.translate(rr * 0.30F, rr * 0.78F, rr * 0.20F);
        sphere(glass, ps.last(), 0, 0, 0, sr, sr, sr, 12, 20, 255, 250, 205, 255, FULL);
        sphere(glass, ps.last(), 0, 0, 0, sr * 1.4F, sr * 1.4F, sr * 1.4F, 10, 16, 255, 220, 120, (int) (120.0F * p), FULL);
        sphere(glass, ps.last(), 0, 0, 0, sr * 2.2F, sr * 2.2F, sr * 2.2F, 8, 14, 255, 170, 60, (int) (60.0F * p), FULL);
        sphere(glass, ps.last(), 0, 0, 0, sr * 3.4F, sr * 3.4F, sr * 3.4F, 8, 12, 255, 120, 30, (int) (30.0F * p), FULL);
        for (int i = 0; i < 12; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(hash(i, 1) * 360.0F + time * 0.5F));
            ps.mulPose(Axis.XP.rotationDegrees(hash(i, 2) * 180.0F - 90.0F));
            tube(glass, ps.last(), sr * 1.1F, sr * (3.0F + 2.0F * hash(i, 3)), sr * 0.12F, sr * 0.02F, 6, 2, 255, 220, 150, (int) (150.0F * p), 0, FULL);
            ps.popPose();
        }
        ps.popPose();
    }

    // =====================================================================================
    // GEOMETRIA BASICA
    // =====================================================================================
    private static float hash(int i, int k) {
        float x = Mth.sin(i * 12.9898F + k * 78.233F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }

    private static void vtx(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float u, float v,
                            int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(p.pose(), x, y, z)
                .color(r, g, b, Mth.clamp(a, 0, 255))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(p.normal(), nx, ny, nz)
                .endVertex();
    }

    /** Esfera (elipsoide) con centro (cx,cy,cz) y radios rx, ry, rz. */
    private static void sphere(VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float cz,
                               float rx, float ry, float rz, int stacks, int slices,
                               int r, int g, int b, int a, int light) {
        for (int i = 0; i < stacks; i++) {
            float t0 = (float) Math.PI * i / stacks;
            float t1 = (float) Math.PI * (i + 1) / stacks;
            float v0 = i / (float) stacks;
            float v1 = (i + 1) / (float) stacks;
            for (int j = 0; j < slices; j++) {
                float p0 = TAU * j / slices;
                float p1 = TAU * (j + 1) / slices;
                float u0 = j / (float) slices;
                float u1 = (j + 1) / (float) slices;
                sphereVertex(vc, p, cx, cy, cz, rx, ry, rz, t0, p0, u0, v0, r, g, b, a, light);
                sphereVertex(vc, p, cx, cy, cz, rx, ry, rz, t1, p0, u0, v1, r, g, b, a, light);
                sphereVertex(vc, p, cx, cy, cz, rx, ry, rz, t1, p1, u1, v1, r, g, b, a, light);
                sphereVertex(vc, p, cx, cy, cz, rx, ry, rz, t0, p1, u1, v0, r, g, b, a, light);
            }
        }
    }

    private static void sphereVertex(VertexConsumer vc, PoseStack.Pose p, float cx, float cy, float cz,
                                     float rx, float ry, float rz, float theta, float phi, float u, float v,
                                     int r, int g, int b, int a, int light) {
        float sx = Mth.sin(theta) * Mth.cos(phi);
        float sy = Mth.cos(theta);
        float sz = Mth.sin(theta) * Mth.sin(phi);
        vtx(vc, p, cx + sx * rx, cy + sy * ry, cz + sz * rz, u, v, r, g, b, a, light, sx, sy, sz);
    }

    /** Anillo plano en el plano XZ (u = angulo, v = radio interior 0 -> exterior 1). */
    private static void annulus(VertexConsumer vc, PoseStack.Pose p, float ri, float ro, int seg,
                                int r, int g, int b, int a, int light) {
        for (int k = 0; k < seg; k++) {
            float a0 = TAU * k / seg;
            float a1 = TAU * (k + 1) / seg;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float u0 = k / (float) seg;
            float u1 = (k + 1) / (float) seg;
            vtx(vc, p, c0 * ri, 0.0F, s0 * ri, u0, 0.0F, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
            vtx(vc, p, c0 * ro, 0.0F, s0 * ro, u0, 1.0F, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
            vtx(vc, p, c1 * ro, 0.0F, s1 * ro, u1, 1.0F, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
            vtx(vc, p, c1 * ri, 0.0F, s1 * ri, u1, 0.0F, r, g, b, a, light, 0.0F, 1.0F, 0.0F);
        }
    }

    private static void tube(VertexConsumer vc, PoseStack.Pose p, float z0, float z1, float r0, float r1,
                             int seg, int rings, int cr, int cg, int cb, int a0, int a1, int light) {
        tube(vc, p, z0, z1, r0, r1, seg, rings, cr, cg, cb, a0, a1, 0.0F, light);
    }

    /**
     * Tubo/cono a lo largo del eje Z, con radio y opacidad que varian de un extremo al otro.
     * "bend" curva el tubo hacia +Y de forma cuadratica (cola de polvo de un cometa).
     */
    private static void tube(VertexConsumer vc, PoseStack.Pose p, float z0, float z1, float r0, float r1,
                             int seg, int rings, int cr, int cg, int cb, int a0, int a1, float bend, int light) {
        for (int k = 0; k < rings; k++) {
            float t0 = k / (float) rings;
            float t1 = (k + 1) / (float) rings;
            float za = Mth.lerp(t0, z0, z1);
            float zb = Mth.lerp(t1, z0, z1);
            float ra = Mth.lerp(t0, r0, r1);
            float rb = Mth.lerp(t1, r0, r1);
            int aa = (int) Mth.lerp(t0, (float) a0, (float) a1);
            int ab = (int) Mth.lerp(t1, (float) a0, (float) a1);
            float ba = bend * t0 * t0;
            float bb = bend * t1 * t1;
            for (int j = 0; j < seg; j++) {
                float an0 = TAU * j / seg;
                float an1 = TAU * (j + 1) / seg;
                float c0 = Mth.cos(an0);
                float s0 = Mth.sin(an0);
                float c1 = Mth.cos(an1);
                float s1 = Mth.sin(an1);
                vtx(vc, p, c0 * ra, s0 * ra + ba, za, 0.0F, 0.0F, cr, cg, cb, aa, light, c0, s0, 0.0F);
                vtx(vc, p, c0 * rb, s0 * rb + bb, zb, 0.0F, 1.0F, cr, cg, cb, ab, light, c0, s0, 0.0F);
                vtx(vc, p, c1 * rb, s1 * rb + bb, zb, 1.0F, 1.0F, cr, cg, cb, ab, light, c1, s1, 0.0F);
                vtx(vc, p, c1 * ra, s1 * ra + ba, za, 1.0F, 0.0F, cr, cg, cb, aa, light, c1, s1, 0.0F);
            }
        }
    }
}
