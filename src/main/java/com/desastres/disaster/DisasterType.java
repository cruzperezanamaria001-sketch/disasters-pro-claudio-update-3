package com.desastres.disaster;

import com.desastres.disaster.impl.BlackHoleDisaster;
import com.desastres.disaster.impl.BlizzardDisaster;
import com.desastres.disaster.impl.EarthquakeDisaster;
import com.desastres.disaster.impl.FireRainDisaster;
import com.desastres.disaster.impl.FloodDisaster;
import com.desastres.disaster.impl.UfoInvasionDisaster;
import com.desastres.disaster.impl.MarsCollisionDisaster;
import com.desastres.disaster.impl.HurricaneDisaster;
import com.desastres.disaster.impl.HeatWaveDisaster;
import com.desastres.disaster.impl.FreezeWaveDisaster;
import com.desastres.disaster.impl.HailstormDisaster;
import com.desastres.disaster.impl.HalleyCometDisaster;
import com.desastres.disaster.impl.HypotheticalBlackHoleDisaster;
import com.desastres.disaster.impl.NukeDisaster;
import com.desastres.disaster.impl.SinkholeDisaster;
import com.desastres.disaster.impl.MeteorDisaster;
import com.desastres.disaster.impl.MeteorShowerDisaster;
import com.desastres.disaster.impl.ThunderstormDisaster;
import com.desastres.disaster.impl.WormholeDisaster;
import com.desastres.disaster.impl.TornadoDisaster;
import com.desastres.disaster.impl.TsunamiDisaster;
import com.desastres.disaster.impl.VolcanoDisaster;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public enum DisasterType {
    BLACK_HOLE("black_hole", (l, p, look, i) -> new BlackHoleDisaster(l, p, i)),
    TSUNAMI("tsunami", (l, p, look, i) -> new TsunamiDisaster(l, p, i, look)),
    TORNADO("tornado", (l, p, look, i) -> new TornadoDisaster(l, p, i)),
    METEOR("meteor", (l, p, look, i) -> new MeteorDisaster(l, p, i, 5.0F + 3.0F * i)),
    METEOR_SHOWER("meteor_shower", (l, p, look, i) -> new MeteorShowerDisaster(l, p, i)),
    VOLCANO("volcano", (l, p, look, i) -> new VolcanoDisaster(l, p, i)),
    BLIZZARD("blizzard", (l, p, look, i) -> new BlizzardDisaster(l, p, i)),
    FLOOD("flood", (l, p, look, i) -> new FloodDisaster(l, p, i)),
    EARTHQUAKE("earthquake", (l, p, look, i) -> new EarthquakeDisaster(l, p, i)),
    THUNDERSTORM("thunderstorm", (l, p, look, i) -> new ThunderstormDisaster(l, p, i)),
    HAILSTORM("hailstorm", (l, p, look, i) -> new HailstormDisaster(l, p, i)),
    FIRE_RAIN("fire_rain", (l, p, look, i) -> new FireRainDisaster(l, p, i)),
    SINKHOLE("sinkhole", (l, p, look, i) -> new SinkholeDisaster(l, p, i)),
    NUKE("nuke", (l, p, look, i) -> new NukeDisaster(l, p, i)),
    HALLEY_COMET("halley_comet", (l, p, look, i) -> new HalleyCometDisaster(l, p, i)),
    WORMHOLE("wormhole", (l, p, look, i) -> new WormholeDisaster(l, p, i)),
    HYPOTHETICAL_BLACK_HOLE("hypothetical_black_hole", (l, p, look, i) -> new HypotheticalBlackHoleDisaster(l, p, i)),
    MARS_COLLISION("mars_collision", (l, p, look, i) -> new MarsCollisionDisaster(l, p, i)),
    HURRICANE("hurricane", (l, p, look, i) -> new HurricaneDisaster(l, p, i)),
    HEAT_WAVE("heat_wave", (l, p, look, i) -> new HeatWaveDisaster(l, p, i)),
    FREEZE_WAVE("freeze_wave", (l, p, look, i) -> new FreezeWaveDisaster(l, p, i)),
    UFO_INVASION("ufo_invasion", (l, p, look, i) -> new UfoInvasionDisaster(l, p, i));

    @FunctionalInterface
    public interface Factory {
        Disaster create(ServerLevel level, Vec3 pos, Vec3 look, int intensity);
    }

    private final String id;
    private final Factory factory;

    DisasterType(String id, Factory factory) {
        this.id = id;
        this.factory = factory;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "disasters.disaster." + id;
    }

    public Disaster create(ServerLevel level, Vec3 pos, Vec3 look, int intensity) {
        return factory.create(level, pos, look, intensity);
    }

    public static DisasterType byIndex(int index) {
        DisasterType[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    public static DisasterType byId(String id) {
        for (DisasterType t : values()) {
            if (t.id.equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    public static List<String> ids() {
        List<String> list = new ArrayList<>();
        for (DisasterType t : values()) {
            list.add(t.id);
        }
        return list;
    }
}
