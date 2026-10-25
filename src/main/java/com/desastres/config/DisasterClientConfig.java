package com.desastres.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Opciones SOLO de cliente (no afectan al servidor). */
public final class DisasterClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.DoubleValue CAMERA_SHAKE_STRENGTH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("camara");
        CAMERA_SHAKE = b
                .comment("Vibracion de camara durante el acercamiento de Marte y las grandes explosiones.")
                .define("cameraShake", true);
        CAMERA_SHAKE_STRENGTH = b
                .comment("Intensidad de la vibracion (1.0 = normal, 0 = nada).")
                .defineInRange("cameraShakeStrength", 1.0D, 0.0D, 2.0D);
        b.pop();
        SPEC = b.build();
    }

    private DisasterClientConfig() {}
}
