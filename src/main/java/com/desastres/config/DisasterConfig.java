package com.desastres.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DisasterConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BLOCK_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_PERMISSION;
    public static final ForgeConfigSpec.DoubleValue DURATION_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_ACTIVE;
    public static final ForgeConfigSpec.IntValue MARS_IMPACT_RADIUS;
    public static final ForgeConfigSpec.IntValue UFO_EFFECT_RADIUS;
    public static final ForgeConfigSpec.DoubleValue UFO_RAY_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue LASER_PISTOL_DAMAGE;
    public static final ForgeConfigSpec.IntValue LASER_PISTOL_RANGE;
    public static final ForgeConfigSpec.DoubleValue ALIEN_DAMAGE;
    public static final ForgeConfigSpec.IntValue HEAT_WAVE_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HEAT_WAVE_FIRES;
    public static final ForgeConfigSpec.IntValue FREEZE_EFFECT_SECONDS;
    public static final ForgeConfigSpec.IntValue FREEZE_TERRAIN_SECONDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("general");
        BLOCK_DAMAGE = b
                .comment("Si es false, los desastres NO modifican bloques (solo efectos visuales y empuje/dano a entidades).")
                .define("blockDamage", true);
        REQUIRE_PERMISSION = b
                .comment("Si es true, solo operadores (nivel 2) o jugadores en creativo pueden usar el control.",
                        "Ponlo en false si quieres que cualquiera con el item pueda usarlo (por ejemplo en supervivencia).")
                .define("requirePermission", true);
        DURATION_MULTIPLIER = b
                .comment("Multiplicador de duracion de los desastres (1.0 = normal).")
                .defineInRange("durationMultiplier", 1.0D, 0.1D, 5.0D);
        MAX_ACTIVE = b
                .comment("Maximo de desastres activos al mismo tiempo (los meteoritos sueltos no cuentan).")
                .defineInRange("maxActiveDisasters", 6, 1, 50);
        b.pop();

        b.push("marte");
        MARS_IMPACT_RADIUS = b
                .comment("Radio (en bloques) de la zona de destruccion del impacto de Marte con intensidad media.",
                        "Ningun bloque fuera de este radio se modifica. Intensidad baja = 80 %, alta = 120 %.")
                .defineInRange("marsImpactRadius", 40, 16, 96);
        b.pop();

        b.push("ovni");
        UFO_EFFECT_RADIUS = b
                .comment("Radio (en bloques) en el que el OVNI dispara sus rayos de energia.")
                .defineInRange("ufoEffectRadius", 28, 8, 64);
        UFO_RAY_DAMAGE = b
                .comment("Dano de cada rayo del OVNI.")
                .defineInRange("ufoRayDamage", 6.0D, 0.0D, 40.0D);
        ALIEN_DAMAGE = b
                .comment("Dano del disparo laser de los alienigenas.")
                .defineInRange("alienDamage", 5.0D, 0.0D, 40.0D);
        LASER_PISTOL_DAMAGE = b
                .comment("Dano de cada disparo de la pistola laser.")
                .defineInRange("laserPistolDamage", 8.0D, 0.0D, 100.0D);
        LASER_PISTOL_RANGE = b
                .comment("Alcance maximo (bloques) de la pistola laser.")
                .defineInRange("laserPistolRange", 64, 16, 128);
        b.pop();

        b.push("clima");
        HEAT_WAVE_SECONDS = b
                .comment("Duracion base (segundos) de la ola de calor. Se multiplica por durationMultiplier.")
                .defineInRange("heatWaveSeconds", 90, 10, 600);
        HEAT_WAVE_FIRES = b
                .comment("Si la ola de calor puede provocar incendios (limitados y solo cerca de jugadores).",
                        "Tambien se respeta la regla de juego doFireTick y que no llueva.")
                .define("heatWaveFires", true);
        FREEZE_EFFECT_SECONDS = b
                .comment("Duracion (segundos) de la congelacion y la lentitud que causa la ola de congelacion.")
                .defineInRange("freezeEffectSeconds", 8, 1, 120);
        FREEZE_TERRAIN_SECONDS = b
                .comment("Segundos que dura el hielo temporal sobre el terreno antes de restaurarse.")
                .defineInRange("freezeTerrainSeconds", 30, 5, 300);
        b.pop();
        SPEC = b.build();
    }

    private DisasterConfig() {}
}
